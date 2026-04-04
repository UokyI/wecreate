package com.wecreate.cron;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Project：WeCreate
 * Date：2026/1/24
 * Time：13:03
 * Description：定时清理长时间运行的僵死CLI进程产生的Java进程，避免内存占用堆叠
 *
 * @author UokyI
 * @version 1.0
 */
@Slf4j
@Configuration
public class CleanCronJob {

    /**
     * 每天早上7点、中午12点、下午6点执行一次清理任务
     */
    @Scheduled(cron = "0 0 7,12,18 * * ?")
    public void cleanLongRunningProcesses() {
        log.info("开始执行定时清理长时间运行的进程任务");

        try {
            // 清理运行时间超过4小时的Java进程（非javaw）
            cleanLongRunningJavaProcesses();

            // 清理运行时间超过4小时的Cmd进程
            cleanLongRunningCmdProcesses();

            // 清理运行时间超过4小时的Cron进程
            cleanLongRunningCronProcesses();

            log.info("定时清理长时间运行的进程任务完成");
        } catch (Exception e) {
            log.error("执行定时清理任务时发生错误", e);
        }
    }

    /**
     * 检测当前服务器（目前是windows）中java 进程（非javaw）运行时间超过4h时，kill其进程
     */
    public void cleanLongRunningJavaProcesses() {
        log.info("开始检查长时间运行的Java进程（非javaw）");
        try {
            // 使用wmic命令获取所有Java进程（非javaw）及其创建时间
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "wmic process where \"Name='java.exe'\" get ProcessId,CreationDate /format:csv");
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                // 跳过CSV标题行
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                line = line.trim();
                if (line.isEmpty() || line.startsWith("Node,CreationDate,ProcessId")) {
                    continue;
                }

                // CSV格式: 节点名称,进程ID,创建日期
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    try {
                        String creationDateStr = parts[1].trim();
                        String processIdStr = parts[2].trim();

                        if (!processIdStr.isEmpty() && !creationDateStr.isEmpty()) {
                            long processId = Long.parseLong(processIdStr);
                            long creationTime = parseWmicDate(creationDateStr);

                            if (creationTime > 0) {
                                long currentTime = System.currentTimeMillis();
                                long processAge = currentTime - creationTime;
                                long fourHoursInMillis = 4 * 60 * 60 * 1000; // 4小时的毫秒数

                                if (processAge > fourHoursInMillis) {
                                    log.info("发现长时间运行的Java进程 {}，创建时间: {}，已运行: {} 小时，正在终止进程", processId, creationDateStr, processAge / (1000 * 60 * 60));

                                    killProcess(processId);
                                }
                            }
                        }
                    } catch (NumberFormatException e) {
                        // 忽略无法解析的行
                        log.debug("无法解析进程信息行: {}", line);
                    }
                }
            }

            process.waitFor();
            log.info("检查长时间运行的Java进程完成");
        } catch (Exception e) {
            log.error("检查长时间运行的Java进程时发生错误", e);
        }
    }

    /**
     * 检测当前服务器（目前是windows）中cmd进程运行时间超过4h时，kill其进程
     */
    public void cleanLongRunningCmdProcesses() {
        log.info("开始检查长时间运行的Cmd进程");
        try {
            // 使用wmic命令获取所有cmd进程及其创建时间
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "wmic process where \"Name='cmd.exe'\" get ProcessId,CreationDate /format:csv");
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                // 跳过CSV标题行
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // CSV格式: 节点名称,进程ID,创建日期
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    try {
                        String processIdStr = parts[1].trim();
                        String creationDateStr = parts[2].trim();

                        if (!processIdStr.isEmpty() && !creationDateStr.isEmpty()) {
                            long processId = Long.parseLong(processIdStr);
                            long creationTime = parseWmicDate(creationDateStr);

                            if (creationTime > 0) {
                                long currentTime = System.currentTimeMillis();
                                long processAge = currentTime - creationTime;
                                long fourHoursInMillis = 4 * 60 * 60 * 1000; // 4小时的毫秒数

                                if (processAge > fourHoursInMillis) {
                                    log.info("发现长时间运行的Cmd进程 {}，创建时间: {}，已运行: {} 小时，正在终止进程", processId, creationDateStr, processAge / (1000 * 60 * 60));

                                    killProcess(processId);
                                }
                            }
                        }
                    } catch (NumberFormatException e) {
                        // 忽略无法解析的行
                        log.debug("无法解析进程信息行: {}", line);
                    }
                }
            }

            process.waitFor();
            log.info("检查长时间运行的Cmd进程完成");
        } catch (Exception e) {
            log.error("检查长时间运行的Cmd进程时发生错误", e);
        }
    }

    /**
     * 检测当前服务器（目前是windows）中cron进程运行时间超过4h时，kill其进程
     */
    public void cleanLongRunningCronProcesses() {
        log.info("开始检查长时间运行的Cron进程");
        try {
            // 在Windows系统中，通常没有cron进程，但可能有计划任务相关的进程，如taskeng.exe或schtasks.exe
            // 但根据需求，我们按字面意思查找包含"cron"的进程
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "wmic process where \"Name LIKE '%cron%'\" get ProcessId,CreationDate /format:csv");
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                // 跳过CSV标题行
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // CSV格式: 节点名称,进程ID,创建日期
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    try {
                        String processIdStr = parts[1].trim();
                        String creationDateStr = parts[2].trim();

                        if (!processIdStr.isEmpty() && !creationDateStr.isEmpty()) {
                            long processId = Long.parseLong(processIdStr);
                            long creationTime = parseWmicDate(creationDateStr);

                            if (creationTime > 0) {
                                long currentTime = System.currentTimeMillis();
                                long processAge = currentTime - creationTime;
                                long fourHoursInMillis = 4 * 60 * 60 * 1000; // 4小时的毫秒数

                                if (processAge > fourHoursInMillis) {
                                    log.info("发现长时间运行的Cron相关进程 {}，创建时间: {}，已运行: {} 小时，正在终止进程", processId, creationDateStr, processAge / (1000 * 60 * 60));

                                    killProcess(processId);
                                }
                            }
                        }
                    } catch (NumberFormatException e) {
                        // 忽略无法解析的行
                        log.debug("无法解析进程信息行: {}", line);
                    }
                }
            }

            process.waitFor();
            log.info("检查长时间运行的Cron进程完成");
        } catch (Exception e) {
            log.error("检查长时间运行的Cron进程时发生错误", e);
        }
    }

    /**
     * 解析WMIC返回的日期时间格式
     * WMIC格式示例: 20251224130345.123456+480 (YYYYMMDDHHMMSS.mmmmmm+timezone)
     */
    private long parseWmicDate(String wmicDate) {
        if (wmicDate == null || wmicDate.trim().isEmpty()) {
            return 0;
        }

        // 使用正则表达式提取日期时间部分
        Pattern pattern = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})\\.(\\d{6})");
        Matcher matcher = pattern.matcher(wmicDate.trim());

        if (matcher.find()) {
            try {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int day = Integer.parseInt(matcher.group(3));
                int hour = Integer.parseInt(matcher.group(4));
                int minute = Integer.parseInt(matcher.group(5));
                int second = Integer.parseInt(matcher.group(6));

                // 创建LocalDateTime对象并转换为毫秒
                java.time.LocalDateTime localDateTime = java.time.LocalDateTime.of(year, month, day, hour, minute, second);
                return localDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception e) {
                log.error("解析WMIC日期时发生错误: {}", wmicDate, e);
                return 0;
            }
        }

        return 0;
    }

    /**
     * 终止指定进程ID的进程
     */
    private void killProcess(long processId) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "taskkill /F /PID " + processId);
            Process process = processBuilder.start();
            process.waitFor();

            log.info("已终止进程: {}", processId);
        } catch (IOException | InterruptedException e) {
            log.error("终止进程 {} 时发生错误", processId, e);
        }
    }
}