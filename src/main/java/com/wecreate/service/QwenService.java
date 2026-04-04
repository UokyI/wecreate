package com.wecreate.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wecreate.entity.WecreatePrompt;
import com.wecreate.mapper.WecreatePromptMapper;
import com.wecreate.utils.GitUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.wecreate.utils.ProcessUtils.getProcessId;

@Slf4j
@Service
public class QwenService {

    // 从配置表获取 qwenPath，不再使用 @Value 注解
    private String getQwenPath() {
        String path = systemConfigService.getQwenCLIPath();
        if (path == null || path.isEmpty()) {
            log.warn("未找到 Qwen CLI 路径配置，使用默认值");
            return "D:\\node-v22.14.0-win-x64\\qwen.cmd";
        }
        return path;
    }

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private WecreatePromptMapper promptMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String buildPrompt(String userRequest, String path) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的程序员，请你在指定工作目录下，完成规划，再完成我提出的开发任务。");
        prompt.append("工作目录是：").append(path).append("。");
        prompt.append("如果需求涉及代码编写，则使用 list_files 工具探索项目结构，了解现有文件组织方式。");
//        prompt.append("如果涉及代码提交或合并 merge 等涉及到指定 git message 操作等，请使用 auto-commit-prefix 技能进行提交。");
        prompt.append("重要：所有文件操作必须基于实际存在的文件路径，不要假设文件存在。");
        prompt.append("重要：请避免反复修改和测试的循环，最多循环测试修复 5 次。");
        prompt.append("我的需求是：").append(userRequest).append("。");
        return prompt.toString();
    }

    public void callQwenCodeCLISync(String userRequest, List<String> responseMessages, String path, Consumer<String[]> onAccept, String userId, String projectId) throws Exception {
        String prompt = buildPrompt(userRequest, path);
        // 创建 WecreatePrompt 记录
        WecreatePrompt promptRecord = new WecreatePrompt();
        promptRecord.setCreateUser(userId != null ? userId : "System");
        promptRecord.setPromptContent(prompt);
        promptRecord.setWorkDirectory(path);
        promptRecord.setTraceId(MDC.get("traceId"));
        promptRecord.setRequestIp(MDC.get("rip"));
        promptRecord.setProjectId(projectId != null ? projectId : "System");
        promptRecord.setCreateTime(LocalDateTime.now());
        // 插入初始记录
        promptMapper.insert(promptRecord);
        Long recordId = promptRecord.getId().longValue();
        log.info("已创建提示词记录，ID: {}", recordId);

        long startTime = System.currentTimeMillis();
        try {

            responseMessages.add("发送的请求内容：" + prompt);

            responseMessages.add("正在调用 Qwen Code CLI...");

            // 创建临时文件来存储 prompt，避免命令行参数中的换行符问题
            Path tempPromptFile = Files.createTempFile("qwen_prompt_", ".txt");
            try (FileWriter writer = new FileWriter(tempPromptFile.toFile())) {
                writer.write(prompt);
            }

            // 确保临时文件在 JVM 退出时被删除
            tempPromptFile.toFile().deleteOnExit();

            // 构建命令 (在 Windows 中需要使用 PowerShell 执行)
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.directory(new File(path));
            // 使用文件输入方式传递 prompt: qwen --approval-mode=yolo < prompt_file.txt
            // 通过配置文件设置模型和 customHeaders
            createQwenSettingsFile(userId != null ? userId : "System", path, "qwen3.6-plus");
            processBuilder.command("cmd", "/c", getQwenPath() + " --approval-mode=yolo < " + tempPromptFile.toAbsolutePath());
            responseMessages.add("执行命令：" + String.join(" ", processBuilder.command()));
            log.info("开始执行 Qwen 命令，工作目录：{}, 命令：{}", path, processBuilder.command());
            onAccept.accept(new String[]{"message", "发送的请求内容：" + prompt});

            // 启动进程
            Process process = processBuilder.start();
            long processId = getProcessId(process);
            log.info("已启动进程，PID: {}", processId);
            onAccept.accept(new String[]{"System","cli 进程 ID：" + processId});
            // 读取标准输出
            BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));

            // 读取错误输出
            BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), "GB2312"));

            // 读取标准输出
            String line;
            while ((line = stdoutReader.readLine()) != null) {
                log.info("qwen 输出：{}", line);
                responseMessages.add(line);
                onAccept.accept(new String[]{"message",line});
                // 解析并更新 token 信息
                updateTokenInfoFromResponse(line, promptRecord, userId);
            }

            // 读取错误输出
            while ((line = stderrReader.readLine()) != null) {
                log.error("qwen 异常输出：{}", line);
                responseMessages.add("[error] " + line);
                onAccept.accept(new String[]{"error",line});
                // 解析并更新 token 信息
                updateTokenInfoFromResponse(line, promptRecord, userId);
            }

            // 等待进程完成，并获取退出码，设置超时时间避免无限等待
            if (!process.waitFor(1800, java.util.concurrent.TimeUnit.SECONDS)) { // 30 分钟
                onAccept.accept(new String[]{"System", "Qwen 命令执行超时 (30min)，正在强制终止进程树..."});
                process.destroyForcibly();
                throw new RuntimeException("Qwen 命令执行超时 (30 分钟)");
            }
            int exitCode = process.exitValue();

            log.info("Qwen 命令执行完成，退出码：{}", exitCode);
            onAccept.accept(new String[]{"System", "qwen 命令执行完成，退出码：" + exitCode});
            // 验证命令是否成功执行
            if (exitCode != 0) {
                throw new RuntimeException("Qwen command failed with exit code: " + exitCode);
            }

            // 删除临时文件
            Files.deleteIfExists(tempPromptFile);
        } catch (Exception e) {
            log.error("调用 Qwen Code CLI 失败：" + e.getMessage(), e);
            throw e;
        } finally {
            // 计算执行时间
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;

            // 更新记录
            promptRecord = promptMapper.selectById(recordId);
            if (promptRecord != null) {
                promptRecord.setExecutionTime((int) executionTime);
                promptRecord.setLmTime(LocalDateTime.now());
                promptRecord.setLmUser(userId != null ? userId : "System");
                promptMapper.updateById(promptRecord);
//                tornadoService.sendTornado(promptRecord);
                log.info("已更新提示词记录，执行时间：{} ms", executionTime);
            }
        }
    }

    public void callQwenCodeCLIWithStreaming(String userRequest, String path, Consumer<String> onMessage, Consumer<String> onWarning, Consumer<String> onBaseInfo, Consumer<String> onSystem, Consumer<String> onError, String userId, String projectId, String model) throws Exception {
        Process process = null;
        Thread stdoutThread = null;
        Thread stderrThread = null;
        BufferedWriter stdinWriter = null;

        // 创建 WecreatePrompt 记录
        WecreatePrompt promptRecord = new WecreatePrompt();
        promptRecord.setCreateUser(userId != null ? userId : "System");
        promptRecord.setLmUser(userId != null ? userId : "System");
        promptRecord.setPromptContent(buildPrompt(userRequest, path));
        promptRecord.setWorkDirectory(path);
        promptRecord.setTraceId(MDC.get("traceId"));
        promptRecord.setRequestIp(MDC.get("rip"));
        promptRecord.setProjectId(projectId != null ? projectId : "System");
        promptRecord.setCreateTime(LocalDateTime.now());

        // 插入初始记录
        promptMapper.insert(promptRecord);
        Long recordId = promptRecord.getId().longValue();
        log.info("已创建提示词记录，ID: {}", recordId);

        long startTime = System.currentTimeMillis();
        long processId = 0;
        try {
            String prompt = buildPrompt(userRequest, path);
            log.debug("发送的请求内容：" + prompt);

            log.info("正在调用 Qwen Code CLI...");

            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.directory(new File(path));
            // 不使用文件重定向，而是通过程序方式传递输入
            // 通过配置文件设置模型和 customHeaders
            createQwenSettingsFile(userId != null ? userId : "System", path, model);
            processBuilder.command("cmd", "/c", getQwenPath() + " --output-format stream-json --approval-mode=yolo ");
            log.debug("执行命令：" + String.join(" ", processBuilder.command()));
            log.info("开始执行 Qwen 命令，工作目录：{}, 命令：{}", path, processBuilder.command());
            onBaseInfo.accept("发送的请求内容：" + prompt);
            process = processBuilder.start();
            processId = getProcessId(process);
            log.info("已启动进程，PID: {}", processId);
            onSystem.accept("cli 进程 ID：" + processId);
            // 获取 stdin 并写入 prompt
            stdinWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), "UTF-8"));
            stdinWriter.write(prompt);
            stdinWriter.flush();
            stdinWriter.close(); // 关闭 stdin 表示输入结束

            final BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            final BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), "GB2312"));

            final AtomicBoolean outputDetected = new AtomicBoolean(false);

            final WecreatePrompt finalPromptRecord = promptRecord;
            stdoutThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = stdoutReader.readLine()) != null) {
                        outputDetected.set(true);
                        if (onMessage != null) {
                            onMessage.accept(line);
                        }
                        // 解析并更新 token 信息
                        updateTokenInfoFromResponse(line, finalPromptRecord, userId);
                    }
                } catch (IOException e) {
                    log.error("读取标准输出时发生错误", e);
                }
            });

            stderrThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = stderrReader.readLine()) != null) {
                        outputDetected.set(true);
                        log.error("qwen 异常输出：{}", line);
                        if (onError != null) {
                            onError.accept(line);
                        }
                        // 解析并更新 token 信息
                        updateTokenInfoFromResponse(line, finalPromptRecord, userId);
                    }
                } catch (IOException e) {
                    log.error("读取错误输出时发生错误", e);
                }
            });

            stdoutThread.start();
            stderrThread.start();

            // 等待进程完成，设置较长时间的总超时
            if (!process.waitFor(1800, java.util.concurrent.TimeUnit.SECONDS)) { // 30 分钟总超时
                // 超时时强制终止整个进程树
                onSystem.accept("Qwen 命令执行超时 (30min)，正在强制终止进程树...");
                destroyAllProcessTree(processId, process);
                throw new RuntimeException("Qwen 命令执行超时 (30 分钟)");
            }

            int exitCode = process.exitValue();

            stdoutThread.join(5000); // 等待最多 5 秒
            stderrThread.join(5000); // 等待最多 5 秒

            log.info("Qwen 命令执行完成，退出码：{}", exitCode);
            onSystem.accept("qwen 命令执行完成，退出码：" + exitCode);
            if (exitCode != 0) {
                throw new RuntimeException("Qwen command failed with exit code: " + exitCode);
            }

        } catch (Exception e) {
            log.error("调用 Qwen Code CLI 失败：" + e.getMessage(), e);
            if (onError != null) {
                onError.accept("异常信息：" + e.getMessage());
            }
            throw e;
        } finally {
            // 计算执行时间
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;

            // 更新记录
            promptRecord = promptMapper.selectById(recordId);
            if (promptRecord != null) {
                promptRecord.setExecutionTime((int) executionTime);
                promptRecord.setLmTime(LocalDateTime.now());
                promptRecord.setLmUser(userId != null ? userId : "System");
                promptMapper.updateById(promptRecord);
//                tornadoService.sendTornado(promptRecord);
                log.info("已更新提示词记录，执行时间：{} ms", executionTime);
            }

            // 检查 Git 变更信息
            checkAndSaveGitStats(promptRecord, path, userId);

            // 确保资源被正确释放
            if (stdinWriter != null) {
                try {
                    stdinWriter.close();
                } catch (IOException e) {
                    log.error("关闭标准输入 writer 时出错", e);
                }
            }
            if (process != null) {
                try {
                    // 确保进程被完全终止
                    destroyAllProcessTree(processId, process);
                } catch (Exception e) {
                    log.error("终止进程时出错", e);
                }
            }
            // 确保所有线程都被中断
            if (stdoutThread != null && stdoutThread.isAlive()) {
                stdoutThread.interrupt();
            }
            if (stderrThread != null && stderrThread.isAlive()) {
                stderrThread.interrupt();
            }
        }
    }

    /**
     * 为当前用户创建临时的 Qwen 配置文件
     * 配置文件中设置 customHeaders 来传递工号
     * @param userId 用户工号
     * @param workDir 工作目录
     * @return 配置文件路径
     */
    private String createQwenSettingsFile(String userId, String workDir, String model) throws IOException {
        // 在用户工作目录下创建 .qwen 配置目录
        File qwenDir = new File(workDir, ".qwen");
        if (!qwenDir.exists()) {
            qwenDir.mkdirs();
        }

        // 创建 settings.json 文件
        File settingsFile = new File(qwenDir, "settings.json");

        // 构建配置内容（仅保留模型指定和自定义请求头）
        String settingsContent = String.format(
                "{\n" +
                        "  \"model\": {\n" +
                        "    \"id\": \"%s\",\n" +
                        "    \"generationConfig\": {\n" +
                        "      \"customHeaders\": {\n" +
                        "        \"X-Username\": \"%s\"\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}",
                model, userId
        );

        try (FileWriter writer = new FileWriter(settingsFile)) {
            writer.write(settingsContent);
        }

        log.info("已为用户 {} 创建 Qwen 配置文件：{}, 模型: {}", userId, settingsFile.getAbsolutePath(), model);
        return settingsFile.getAbsolutePath();
    }

    /**
     * 检查并保存 Git 统计信息
     * @param promptRecord 提示词记录
     * @param path 工作目录路径
     */
    private void checkAndSaveGitStats(WecreatePrompt promptRecord, String path, String userId) {
        try {
            log.info("检查 Git 统计信息...");
            log.info("检查是否为 Git 仓库...");
            // 检查是否为 Git 仓库
            if (!GitUtils.isGitRepository(path)) {
                log.info("目录 {} 不是 Git 仓库", path);
                // 尝试查找子目录中是否有 Git 仓库
                File dir = new File(path);
                if (dir.isDirectory()) {
                    File[] subDirs = dir.listFiles(File::isDirectory);
                    if (subDirs != null) {
                        for (File subDir : subDirs) {
                            String subPath = subDir.getAbsolutePath();
                            if (GitUtils.isGitRepository(subPath)) {
                                log.info("在子目录 {} 中找到 Git 仓库，重新赋值 path 路径", subPath);
                                path = subPath;
                                break;
                            }
                        }
                    }
                }

                // 再次检查 path 是否为 Git 仓库，如果不是则返回
                if (!GitUtils.isGitRepository(path)) {
                    log.info("目录 {} 及其子目录都不是 Git 仓库", path);
                    return;
                }
            }

            log.info("检查是否有最近的 commit（5 分钟内）");
            // 获取最近 5 分钟内的 commit hash
            String commitHash = GitUtils.getRecentCommitHash(path);
            if (commitHash == null) {
                log.info("目录 {} 中没有最近的 Git Commit", path);
                return;
            }

            // 查询全表最近 10 分钟内是否有相同的 commit 记录
            LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);
            QueryWrapper<WecreatePrompt> wrapper = new QueryWrapper<>();
            wrapper.ge("CREATE_TIME", tenMinutesAgo)
                   .eq("GIT_COMMIT_HASH", commitHash);
            Long count = promptMapper.selectCount(wrapper);
            if (count > 0) {
                log.info("该 commit [{}] 在最近 10 分钟内已记录过，跳过", commitHash);
                return;
            }
            promptRecord.setGitCommitHash(commitHash);

            // 构建描述信息
            StringBuilder description = new StringBuilder();

            // 获取 git show --stat 的完整输出
            try {
                ProcessBuilder statProcessBuilder = new ProcessBuilder();
                statProcessBuilder.directory(new File(path));
                statProcessBuilder.command("git", "show", "--stat");

                Process statProcess = statProcessBuilder.start();
                BufferedReader statReader = new BufferedReader(new InputStreamReader(statProcess.getInputStream()));

                description.append("--- Git Show Stat ---\n");
                String statLine;
                while ((statLine = statReader.readLine()) != null) {
                    description.append(statLine).append("\n");
                }

                int statExitCode = statProcess.waitFor();
                if (statExitCode != 0) {
                    log.warn("执行 git show --stat 命令失败，退出码：{}", statExitCode);
                }
            } catch (Exception e) {
                log.error("获取 git show --stat 输出时出错：{}", e.getMessage(), e);
            }

            log.info("获取 Git 统计信息");
            // 获取 Git 统计信息
            int[] gitStats = GitUtils.getGitStats(path);
            if (gitStats != null) {
                promptRecord.setGitChangedFiles(gitStats[0]);
                promptRecord.setGitInsertions(gitStats[1]);
                promptRecord.setGitDeletions(gitStats[2]);
                promptRecord.setGitTotalChanges(gitStats[3]);

                description.append("\n--- Git 统计信息摘要 ---\n");
                description.append("Git 变更统计 - 文件数：").append(gitStats[0])
                       .append(", 新增行数：").append(gitStats[1])
                       .append(", 删除行数：").append(gitStats[2])
                       .append(", 总变更行数：").append(gitStats[3]).append("\n");
                log.info("Git 变更统计 - 文件数：{}, 新增行数：{}, 删除行数：{}, 总变更行数：{}",
                        gitStats[0], gitStats[1], gitStats[2], gitStats[3]);
            } else {
                log.warn("无法获取 Git 统计信息");
            }

            // 将描述信息保存到数据库
            promptRecord.setDescription(description.toString());
            promptRecord.setLmUser(userId != null ? userId : "System");
            promptMapper.updateById(promptRecord);
        } catch (Exception e) {
            log.error("检查 Git 统计信息时出错：{}", e.getMessage(), e);
        }
    }

    /**
     * 从响应中解析并更新 token 信息
     * @param responseLine 响应行
     * @param promptRecord 提示词记录
     * @param userId 用户 ID
     */
    private void updateTokenInfoFromResponse(String responseLine, WecreatePrompt promptRecord, String userId) {
        try {
            // 使用 JsonContentParser 解析响应内容，提取 token 信息
            com.wecreate.utils.JsonContentParser.TokenInfo tokenInfo =
                com.wecreate.utils.JsonContentParser.extractTokenInfo(responseLine);

            // 如果提取到了 token 信息，则更新数据库记录
            if (tokenInfo != null && tokenInfo.hasAnyToken()) {
                boolean updated = false;

                if (tokenInfo.getInputTokens() != null) {
                    promptRecord.setInputTokens(tokenInfo.getInputTokens());
                    updated = true;
                }

                if (tokenInfo.getOutputTokens() != null) {
                    promptRecord.setOutputTokens(tokenInfo.getOutputTokens());
                    updated = true;
                }

                if (tokenInfo.getCachedTokens() != null) {
                    promptRecord.setCachedTokens(tokenInfo.getCachedTokens());
                    updated = true;
                }

                if (tokenInfo.getTotalTokens() != null) {
                    promptRecord.setTotalTokens(tokenInfo.getTotalTokens());
                    updated = true;
                }

                // 如果有任何更新，则保存到数据库
                if (updated) {
                    promptRecord.setLmUser(userId != null ? userId : "System");
                    promptRecord.setLmTime(LocalDateTime.now());
                    promptMapper.updateById(promptRecord);
                    log.info("已更新 token 信息 - 输入：{}, 输出：{}, 缓存：{}, 总计：{}",
                        promptRecord.getInputTokens(), promptRecord.getOutputTokens(),
                        promptRecord.getCachedTokens(), promptRecord.getTotalTokens());
                }
            }
        } catch (Exception e) {
            log.warn("解析响应中的 token 信息失败：{}", e.getMessage());
        }
    }

    public void destroyAllProcessTree(long processId, Process  process) {
        // 尝试通过 Windows API 直接杀死进程树
        if (0 == processId) {
            log.error("进程 ID 为 0，无法终止进程树");
            return;
        }
        try {
            log.info("在 Windows 上使用 taskkill /T /F 来终止整个进程树");
            // 在 Windows 上使用 taskkill /T /F 来终止整个进程树
            // /T 终止指定进程和由它启用的子进程
            // /F 指定强制终止进程
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(processId));
            pb.redirectErrorStream(true); // 重定向错误流到输出流
            Process taskkillProcess = pb.start();

            // 创建线程来读取 taskkill 的输出
            Thread outputThread = new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(taskkillProcess.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("taskkill 输出：" + line);
                    }
                } catch (IOException e) {
                    log.error("读取 taskkill 输出时出错", e);
                }
            });
            outputThread.start();

            // 等待 taskkill 命令完成，设置超时时间
            if (!taskkillProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("taskkill 命令执行超时");
                taskkillProcess.destroyForcibly();
            }

            // 等待输出线程完成
            outputThread.join(2000);

            log.info("已尝试通过 taskkill 杀死进程树，PID: " + processId);
        } catch (Exception taskkillEx) {
            log.error("通过 taskkill 杀死进程失败", taskkillEx);
        }
        log.info("防呆，kill process");
        destroyProcessTree(process);
    }

    /**
     * 强制终止进程及其所有子进程
     *
     * @param process 需要终止的进程
     */
    public void destroyProcessTree(Process process) {
        try {
            // 首先尝试使用 Java 9+ 的 destroyForcibly 方法
            process.destroyForcibly();
            log.info("已调用 destroyForcibly 终止进程");
        } catch (Exception e) {
            log.error("终止进程失败", e);
        }
    }
}
