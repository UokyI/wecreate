package com.wecreate.service;

import com.wecreate.entity.WecreatePrompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 自评分服务
 * 负责调用 AI 对任务执行情况进行自动评分
 * 评分范围：1-10分
 */
@Slf4j
@Service
public class AISelfScoreService {

    @Autowired
    private SystemConfigService systemConfigService;

    /**
     * AI 评分结果解析正则表达式
     * 匹配格式：SCORE: <数字> 或 score: <数字>
     */
    private static final Pattern SCORE_PATTERN = Pattern.compile("(?i)SCORE:\\s*(\\d+)");

    /**
     * 分析并评分
     * 
     * @param scorePrompt AI 评分提示词
     * @param promptRecord 任务记录
     * @return 评分结果（1-10分），如果评分失败返回 null
     */
    public BigDecimal analyzeAndScore(String scorePrompt, WecreatePrompt promptRecord) {
        try {
            log.info("开始调用 AI 进行自评分: traceId={}", promptRecord.getTraceId());

            // 获取 Qwen CLI 路径
            String qwenPath = getQwenPath();
            if (qwenPath == null || qwenPath.isEmpty()) {
                log.error("Qwen CLI 路径未配置，无法进行 AI 评分");
                return null;
            }

            // 工作目录
            String workDir = promptRecord.getWorkDirectory();
            if (workDir == null || workDir.isEmpty()) {
                log.error("工作目录为空，无法进行 AI 评分");
                return null;
            }

            // 创建临时评分提示文件
            Path tempScorePromptFile = Files.createTempFile("ai_selfscore_prompt_", ".txt");
            try {
                // Java 8 兼容写法
                try (FileWriter writer = new FileWriter(tempScorePromptFile.toFile())) {
                    writer.write(scorePrompt);
                }
                tempScorePromptFile.toFile().deleteOnExit();

                // 构建 AI 评分命令
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.directory(new File(workDir));
                
                // 使用轻量级模式调用 Qwen CLI（仅用于评分）
                processBuilder.command("cmd", "/c", 
                        qwenPath + " --approval-mode=yolo < " + tempScorePromptFile.toAbsolutePath());

                log.info("执行 AI 评分命令，工作目录: {}", workDir);

                // 执行评分命令
                Process process = processBuilder.start();
                
                // 读取 AI 输出
                StringBuilder outputBuilder = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputBuilder.append(line).append("\n");
                        log.debug("AI 评分输出: {}", line);
                    }
                }

                // 读取错误输出
                StringBuilder errorBuilder = new StringBuilder();
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorBuilder.append(line).append("\n");
                    }
                }

                // 等待进程完成（设置超时 5 分钟）
                if (!process.waitFor(300, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.warn("AI 评分超时，正在强制终止进程");
                    process.destroyForcibly();
                    return null;
                }

                int exitCode = process.exitValue();
                log.info("AI 评分命令执行完成，退出码: {}", exitCode);

                if (exitCode != 0) {
                    log.error("AI 评分命令执行失败，退出码: {}, 错误: {}", exitCode, errorBuilder.toString());
                    return null;
                }

                // 解析评分结果
                String aiOutput = outputBuilder.toString();
                BigDecimal score = parseScoreFromOutput(aiOutput);

                if (score != null) {
                    log.info("AI 评分解析成功: traceId={}, 评分={}", promptRecord.getTraceId(), score);
                } else {
                    log.warn("未能从 AI 输出中解析到评分: {}", aiOutput);
                }

                return score;

            } finally {
                // 删除临时文件
                Files.deleteIfExists(tempScorePromptFile);
            }

        } catch (Exception e) {
            log.error("AI 自评分异常: traceId={}, 错误: {}", promptRecord.getTraceId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从 AI 输出中解析评分结果
     * 期望格式：SCORE: <数字>
     * 
     * @param aiOutput AI 输出内容
     * @return 评分（1-10分），如果解析失败返回 null
     */
    private BigDecimal parseScoreFromOutput(String aiOutput) {
        if (aiOutput == null || aiOutput.isEmpty()) {
            return null;
        }

        try {
            Matcher matcher = SCORE_PATTERN.matcher(aiOutput);
            if (matcher.find()) {
                String scoreStr = matcher.group(1);
                int score = Integer.parseInt(scoreStr);
                
                // 验证评分范围
                if (score >= 1 && score <= 10) {
                    return new BigDecimal(score);
                } else {
                    log.warn("AI 评分超出范围: {}, 期望范围: 1-10", score);
                    return null;
                }
            }
        } catch (NumberFormatException e) {
            log.error("解析评分结果失败: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 获取 Qwen CLI 路径
     */
    private String getQwenPath() {
        String path = systemConfigService.getQwenCLIPath();
        if (path == null || path.isEmpty()) {
            log.warn("未找到 Qwen CLI 路径配置，使用默认值");
            return "D:\\node-v22.14.0-win-x64\\qwen.cmd";
        }
        return path;
    }
}
