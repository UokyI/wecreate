package com.wecreate.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wecreate.entity.WecreatePrompt;
import com.wecreate.mapper.WecreatePromptMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 项目记忆服务
 * 负责读写项目 .memory 目录下的记忆文件，并在任务执行前后进行读取和整理
 * 包含 AI 自评分功能，在记忆整理时自动对任务完成质量进行评分（1-10分）
 */
@Slf4j
@Service
public class MemoryService {

    private static final String MEMORY_DIR_NAME = ".memory";
    private static final String MEMORY_FILE_PREFIX = "memory_";
    private static final String MEMORY_FILE_SUFFIX = ".md";
    private static final String MAIN_MEMORY_FILE = "MEMORY.md";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private WecreatePromptMapper wecreatePromptMapper;

    @Autowired
    private WecreatePromptService wecreatePromptService;

    @Autowired
    private AISelfScoreService aiSelfScoreService;

    /**
     * 判断记忆功能是否启用
     */
    public boolean isEnabled() {
        return systemConfigService.isMemoryEnabled();
    }

    /**
     * 确保 .memory 目录存在
     */
    private Path ensureMemoryDirExists(String projectPath) {
        Path memoryDir = Paths.get(projectPath, MEMORY_DIR_NAME);
        try {
            if (!Files.exists(memoryDir)) {
                Files.createDirectories(memoryDir);
                log.info("创建记忆目录: {}", memoryDir);
            }
        } catch (IOException e) {
            log.error("创建记忆目录失败: {}", memoryDir, e);
        }
        return memoryDir;
    }

    /**
     * 任务执行前：读取项目记忆文件内容
     * 返回主记忆文件内容和最近3次历史记忆内容，供 AI 参考
     */
    public String readMemoriesBeforeTask(String projectPath) {
        if (!isEnabled()) {
            return null;
        }

        Path memoryDir = ensureMemoryDirExists(projectPath);
        StringBuilder memoryContent = new StringBuilder();

        // 1. 读取主记忆文件 MEMORY.md
        Path mainMemoryFile = memoryDir.resolve(MAIN_MEMORY_FILE);
        if (Files.exists(mainMemoryFile)) {
            try {
                String content = new String(Files.readAllBytes(mainMemoryFile), StandardCharsets.UTF_8);
                memoryContent.append("## 项目主记忆 (MEMORY.md)\n\n");
                memoryContent.append(content);
                memoryContent.append("\n\n");
                log.info("读取项目主记忆文件: {}", mainMemoryFile);
            } catch (IOException e) {
                log.error("读取主记忆文件失败: {}", mainMemoryFile, e);
            }
        }

        // 2. 读取最近3次历史记忆文件
        List<Path> historyFiles = getHistoryMemoryFiles(memoryDir, 3);
        if (!historyFiles.isEmpty()) {
            memoryContent.append("## 最近执行历史记忆\n\n");
            for (Path historyFile : historyFiles) {
                try {
                    String content = new String(Files.readAllBytes(historyFile), StandardCharsets.UTF_8);
                    memoryContent.append("### ").append(historyFile.getFileName().toString()).append("\n\n");
                    memoryContent.append(content);
                    memoryContent.append("\n\n");
                } catch (IOException e) {
                    log.error("读取历史记忆文件失败: {}", historyFile, e);
                }
            }
        }

        return memoryContent.length() > 0 ? memoryContent.toString() : null;
    }

    /**
     * 任务执行后：自检并整理记忆
     * 根据当前任务的执行记录（WECREATE_CLI_PROMPT），生成总结并写入记忆文件
     * 同时进行 AI 自评分，评估任务完成质量和执行流程
     */
    public void consolidateMemoryAfterTask(String projectPath, String projectId, String traceId) {
        if (!isEnabled()) {
            return;
        }

        try {
            Path memoryDir = ensureMemoryDirExists(projectPath);

            // 1. 查询本次任务相关的执行记录
            QueryWrapper<WecreatePrompt> wrapper = new QueryWrapper<>();
            if (traceId != null && !traceId.isEmpty()) {
                // 如果有 traceId（单个任务），只查询该 traceId 的记录
                wrapper.eq("TRACE_ID", traceId);
            } else if (projectId != null && !projectId.isEmpty()) {
                // 否则查询该项目最近的记录（用于批量任务）
                wrapper.eq("PROJECT_ID", projectId)
                        .orderByDesc("CREATE_TIME")
                        .last("LIMIT 10");
            } else {
                return;
            }

            List<WecreatePrompt> prompts = wecreatePromptMapper.selectList(wrapper);
            if (prompts == null || prompts.isEmpty()) {
                log.info("未找到执行记录，跳过记忆整理, projectPath: {}, traceId: {}", projectPath, traceId);
                return;
            }

            // 2. AI 自评分：对任务完成质量进行综合评估
            performAISelfScoring(prompts, projectPath, traceId);

            // 3. 自动生成记忆总结
            String memorySummary = generateMemorySummary(prompts);

            if (memorySummary == null || memorySummary.trim().isEmpty()) {
                log.info("记忆总结为空，跳过写入");
                return;
            }

            // 4. 写入历史记忆文件（带时间戳）
            String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
            String historyFileName = MEMORY_FILE_PREFIX + timestamp + MEMORY_FILE_SUFFIX;
            Path historyFile = memoryDir.resolve(historyFileName);
            Files.write(historyFile, memorySummary.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("写入历史记忆文件: {}", historyFile);

            // 5. 更新主记忆文件 MEMORY.md（追加或覆盖）
            updateMainMemoryFile(memoryDir, prompts, memorySummary);

        } catch (Exception e) {
            log.error("记忆整理失败", e);
        }
    }

    /**
     * 根据执行记录生成记忆总结
     */
    private String generateMemorySummary(List<WecreatePrompt> prompts) {
        StringBuilder summary = new StringBuilder();
        summary.append("# 任务执行记忆\n\n");
        summary.append("**生成时间**: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

        // 统计信息
        int totalTasks = prompts.size();
        long tasksWithGitChanges = prompts.stream().filter(p -> p.getGitChangedFiles() != null && p.getGitChangedFiles() > 0).count();
        int totalChangedFiles = prompts.stream().mapToInt(p -> p.getGitChangedFiles() != null ? p.getGitChangedFiles() : 0).sum();
        int totalInputTokens = prompts.stream().mapToInt(p -> p.getInputTokens() != null ? p.getInputTokens() : 0).sum();
        int totalOutputTokens = prompts.stream().mapToInt(p -> p.getOutputTokens() != null ? p.getOutputTokens() : 0).sum();

        summary.append("## 执行概览\n\n");
        summary.append("- 任务总数: ").append(totalTasks).append("\n");
        summary.append("- 有代码变更的任务: ").append(tasksWithGitChanges).append("\n");
        summary.append("- 总变更文件数: ").append(totalChangedFiles).append("\n");
        summary.append("- 总输入 Token: ").append(totalInputTokens).append("\n");
        summary.append("- 总输出 Token: ").append(totalOutputTokens).append("\n\n");

        // 任务详情（只记录有实际内容的任务）
        summary.append("## 任务详情\n\n");
        for (int i = 0; i < prompts.size(); i++) {
            WecreatePrompt prompt = prompts.get(i);
            if (prompt.getPromptContent() == null || prompt.getPromptContent().trim().isEmpty()) {
                continue;
            }

            // 截取 prompt content（避免过长）
            String content = prompt.getPromptContent();
            if (content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }

            summary.append("### 任务 ").append(i + 1).append("\n\n");
            summary.append("- **请求内容**: ").append(content).append("\n");
            summary.append("- **工作目录**: ").append(prompt.getWorkDirectory() != null ? prompt.getWorkDirectory() : "N/A").append("\n");
            summary.append("- **评分**: ").append(prompt.getScore() != null ? prompt.getScore() : "N/A").append("\n");

            if (prompt.getGitChangedFiles() != null && prompt.getGitChangedFiles() > 0) {
                summary.append("- **Git 变更**: ").append(prompt.getGitChangedFiles()).append(" 个文件, +").append(prompt.getGitInsertions()).append(" -").append(prompt.getGitDeletions()).append("\n");
            }

            if (prompt.getDescription() != null && !prompt.getDescription().isEmpty()) {
                String desc = prompt.getDescription();
                if (desc.length() > 300) {
                    desc = desc.substring(0, 300) + "...";
                }
                summary.append("- **描述**: ").append(desc).append("\n");
            }

            summary.append("\n");
        }

        // 经验教训（从有评分 >= 5 的任务中提取成功模式）
        List<WecreatePrompt> acceptedTasks = prompts.stream()
                .filter(p -> p.getScore() != null && p.getScore().intValue() >= 5)
                .collect(Collectors.toList());

        if (!acceptedTasks.isEmpty()) {
            summary.append("## 成功模式\n\n");
            for (WecreatePrompt task : acceptedTasks) {
                if (task.getPromptContent() != null && !task.getPromptContent().trim().isEmpty()) {
                    String content = task.getPromptContent();
                    if (content.length() > 150) {
                        content = content.substring(0, 150) + "...";
                    }
                    summary.append("- ").append(content).append("\n");
                }
            }
            summary.append("\n");
        }

        return summary.toString();
    }

    /**
     * 更新主记忆文件（合并新旧内容）
     */
    private void updateMainMemoryFile(Path memoryDir, List<WecreatePrompt> prompts, String newSummary) {
        Path mainMemoryFile = memoryDir.resolve(MAIN_MEMORY_FILE);
        StringBuilder content = new StringBuilder();

        content.append("# 项目记忆\n\n");
        content.append("**项目路径**: ").append(prompts.isEmpty() ? "N/A" : prompts.get(0).getWorkDirectory()).append("\n");
        content.append("**最后更新**: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

        content.append("## 项目概述\n\n");
        content.append("> 此文件由 WeCreate 记忆功能自动生成和更新。\n");
        content.append("> 每次任务执行后，系统会自动总结关键信息并追加到历史记忆中。\n\n");

        content.append("## 最近任务摘要\n\n");
        content.append("以下是最近执行的任务摘要，详细记录请查看历史记忆文件。\n\n");
        content.append(newSummary);

        // 如果有旧的主记忆文件，保留其项目概述部分
        if (Files.exists(mainMemoryFile)) {
            try {
                String oldContent = new String(Files.readAllBytes(mainMemoryFile), StandardCharsets.UTF_8);
                // 提取旧记忆中的项目概述部分（如果有手动维护的内容）
                int overviewStart = oldContent.indexOf("## 项目概述");
                int overviewEnd = oldContent.indexOf("## 最近任务摘要");
                if (overviewStart >= 0 && overviewEnd > overviewStart) {
                    String oldOverview = oldContent.substring(overviewStart, overviewEnd).trim();
                    // 如果旧概述包含手动维护的内容，保留它
                    if (!oldOverview.contains("此文件由 WeCreate 记忆功能自动生成")) {
                        content.insert(content.indexOf("## 最近任务摘要"), oldOverview + "\n\n");
                    }
                }
            } catch (IOException e) {
                log.error("读取旧主记忆文件失败", e);
            }
        }

        try {
            Files.write(mainMemoryFile, content.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("更新主记忆文件: {}", mainMemoryFile);
        } catch (IOException e) {
            log.error("写入主记忆文件失败", e);
        }
    }

    /**
     * 获取历史记忆文件列表（按时间倒序，取指定数量）
     */
    private List<Path> getHistoryMemoryFiles(Path memoryDir, int limit) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(memoryDir, MEMORY_FILE_PREFIX + "*" + MEMORY_FILE_SUFFIX)) {
            List<Path> files = new ArrayList<>();
            for (Path entry : stream) {
                files.add(entry);
            }
            // 按文件名倒序（最新的在前）
            files.sort((a, b) -> b.getFileName().compareTo(a.getFileName()));
            return files.stream().limit(limit).collect(Collectors.toList());
        } catch (IOException e) {
            log.error("获取历史记忆文件列表失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取所有记忆文件列表（用于前端展示）
     */
    public List<String> listMemoryFiles(String projectPath) {
        Path memoryDir = ensureMemoryDirExists(projectPath);
        List<String> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(memoryDir)) {
            for (Path entry : stream) {
                files.add(entry.getFileName().toString());
            }
            files.sort((a, b) -> b.compareTo(a)); // 倒序
        } catch (IOException e) {
            log.error("读取记忆文件列表失败", e);
        }
        return files;
    }

    /**
     * 读取指定记忆文件内容
     */
    public String readMemoryFile(String projectPath, String fileName) {
        Path memoryDir = ensureMemoryDirExists(projectPath);
        Path filePath = memoryDir.resolve(fileName);
        try {
            if (Files.exists(filePath)) {
                return new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("读取记忆文件失败: {}", filePath, e);
        }
        return null;
    }

    /**
     * AI 自评分：对任务完成质量进行综合评估
     * 评分范围：1-10分
     * 评估维度：
     * 1. 任务完成质量（是否达成目标）
     * 2. 执行流程规范性（是否按正确流程执行）
     * 3. 代码/技术实现质量（代码质量、技术方案等）
     * 
     * @param prompts 任务执行记录列表
     * @param projectPath 项目路径
     * @param traceId 追踪ID
     */
    private void performAISelfScoring(List<WecreatePrompt> prompts, String projectPath, String traceId) {
        if (prompts == null || prompts.isEmpty()) {
            return;
        }

        try {
            log.info("开始 AI 自评分，任务数量: {}, traceId: {}", prompts.size(), traceId);

            // 为每个任务记录进行自评分
            for (WecreatePrompt prompt : prompts) {
                // 如果已经有用户评分，跳过（保留用户评分）
                if (prompt.getScore() != null) {
                    log.debug("任务已有用户评分，跳过 AI 自评: traceId={}, 用户评分={}", 
                            prompt.getTraceId(), prompt.getScore());
                    continue;
                }

                // 构建 AI 评分请求
                String scoreAnalysisPrompt = buildSelfScorePrompt(prompt, projectPath);
                
                // 调用 AI 服务进行评分分析
                BigDecimal aiScore = aiSelfScoreService.analyzeAndScore(scoreAnalysisPrompt, prompt);
                
                // 保存 AI 评分结果
                if (aiScore != null && aiScore.compareTo(BigDecimal.ONE) >= 0 && aiScore.compareTo(BigDecimal.TEN) <= 0) {
                    wecreatePromptService.scorePromptByTraceId(
                            prompt.getTraceId(), 
                            aiScore, 
                            "AI_SELF_SCORE"
                    );
                    log.info("AI 自评分完成: traceId={}, 评分={}", prompt.getTraceId(), aiScore);
                } else {
                    log.warn("AI 评分结果无效: traceId={}, 评分={}", prompt.getTraceId(), aiScore);
                }
            }

        } catch (Exception e) {
            log.error("AI 自评分失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 构建 AI 自评分提示词
     * 要求 AI 根据任务执行情况，从多个维度进行综合评分（1-10分）
     */
    private String buildSelfScorePrompt(WecreatePrompt prompt, String projectPath) {
        StringBuilder promptBuilder = new StringBuilder();
        
        promptBuilder.append("【AI 自评分任务】\n\n");
        promptBuilder.append("请你对刚刚执行的任务进行自我评分，评分范围为 1-10 分。\n\n");
        
        promptBuilder.append("【评分维度】\n");
        promptBuilder.append("1. 任务完成质量（40%权重）：是否达成任务目标，输出结果是否符合预期\n");
        promptBuilder.append("2. 执行流程规范性（30%权重）：是否按照正确的开发流程和执行步骤进行\n");
        promptBuilder.append("3. 技术实现质量（30%权重）：代码质量、技术方案合理性、是否引入新问题\n\n");
        
        promptBuilder.append("【评分标准】\n");
        promptBuilder.append("1-2分：任务完全失败，未能达成任何目标\n");
        promptBuilder.append("3-4分：任务部分失败，存在严重问题\n");
        promptBuilder.append("5-6分：任务基本完成，但存在一些缺陷或不足\n");
        promptBuilder.append("7-8分：任务完成良好，基本达成目标，有少量可改进之处\n");
        promptBuilder.append("9-10分：任务完成优秀，完全达成目标，执行流程规范，代码质量高\n\n");
        
        promptBuilder.append("【本次任务信息】\n");
        promptBuilder.append("任务请求：").append(prompt.getPromptContent() != null ? prompt.getPromptContent() : "N/A").append("\n");
        promptBuilder.append("工作目录：").append(prompt.getWorkDirectory() != null ? prompt.getWorkDirectory() : "N/A").append("\n");
        promptBuilder.append("执行时间：").append(prompt.getExecutionTime() != null ? prompt.getExecutionTime() + "ms" : "N/A").append("\n");
        
        if (prompt.getGitChangedFiles() != null && prompt.getGitChangedFiles() > 0) {
            promptBuilder.append("Git 变更：").append(prompt.getGitChangedFiles()).append(" 个文件, 新增 ")
                    .append(prompt.getGitInsertions()).append(" 行, 删除 ").append(prompt.getGitDeletions()).append(" 行\n");
        } else {
            promptBuilder.append("Git 变更：无代码变更\n");
        }
        
        if (prompt.getDescription() != null && !prompt.getDescription().isEmpty()) {
            String desc = prompt.getDescription();
            if (desc.length() > 500) {
                desc = desc.substring(0, 500) + "...";
            }
            promptBuilder.append("执行描述：").append(desc).append("\n");
        }
        
        promptBuilder.append("\n【评分要求】\n");
        promptBuilder.append("1. 请综合评估上述信息，给出一个 1-10 的整数评分\n");
        promptBuilder.append("2. 评分应该客观公正，基于实际执行情况\n");
        promptBuilder.append("3. 请输出评分结果，格式为：SCORE: <分数>\n");
        promptBuilder.append("4. 例如：SCORE: 8\n\n");
        
        promptBuilder.append("请开始评分：");
        
        return promptBuilder.toString();
    }
}
