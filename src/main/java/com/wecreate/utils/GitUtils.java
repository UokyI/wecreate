package com.wecreate.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class GitUtils {

    /**
     * 检查目录是否为Git仓库
     *
     * @param directory 工作目录
     * @return 是否为Git仓库
     */
    public static boolean isGitRepository(String directory) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.directory(new File(directory));
            processBuilder.command("git", "rev-parse", "--is-inside-work-tree");

            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            return exitCode == 0;
        } catch (Exception e) {
            log.debug("检查Git仓库状态时出错: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取最新提交的哈希值
     *
     * @param directory 工作目录
     * @return 最新提交的哈希值
     */
    public static String getLastCommitHash(String directory) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.directory(new File(directory));
            processBuilder.command("git", "rev-parse", "HEAD");

            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String commitHash = reader.readLine();
            int exitCode = process.waitFor();

            if (exitCode == 0 && commitHash != null) {
                return commitHash.trim();
            }
        } catch (Exception e) {
            log.error("获取最新提交哈希时出错: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * 获取Git统计信息
     *
     * @param directory 工作目录
     * @return Git统计信息数组 [changedFiles, insertions, deletions, totalChanges] 或 null（如果没有变更）
     */
    public static int[] getGitStats(String directory) {
        try {
            // 检查是否存在未提交的变更
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.directory(new File(directory));
            processBuilder.command("git", "show", "--stat");

            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            String lastLine = null;
            while ((line = reader.readLine()) != null) {
                lastLine = line;
            }

            int exitCode = process.waitFor();

            // 如果没有输出或者最后一行为空，则没有变更
            if (exitCode != 0 || lastLine == null || lastLine.trim().isEmpty()) {
                return null;
            }

            // 解析统计信息
            // 示例输出: " 1 file changed, 10 insertions(+), 5 deletions(-)"
            // 示例输出: " 5 files changed, 201 insertions(+)"
            // 示例输出: " 2 files changed, 10 deletions(-)"
            Pattern pattern = Pattern.compile("(\\d+)\\s+file[s]?\\s+changed(?:[^\\d]+(\\d*)\\s+insertion[s]?\\([^)]*\\))?(?:[^\\d]+(\\d*)\\s+deletion[s]?\\([^)]*\\))?");
            Matcher matcher = pattern.matcher(lastLine.trim());

            if (matcher.find()) {
                int changedFiles = Integer.parseInt(matcher.group(1));
                // 处理可能不存在的插入行数，默认为0
                int insertions = matcher.group(2) != null && !matcher.group(2).isEmpty() ?
                        Integer.parseInt(matcher.group(2)) : 0;
                // 处理可能不存在的删除行数，默认为0
                int deletions = matcher.group(3) != null && !matcher.group(3).isEmpty() ?
                        Integer.parseInt(matcher.group(3)) : 0;
                int totalChanges = insertions + deletions;

                return new int[]{changedFiles, insertions, deletions, totalChanges};
            }

            return null;
        } catch (Exception e) {
            log.error("获取Git统计信息时出错: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 检查是否存在最近的Git Commit
     *
     * @param directory 工作目录
     * @return 是否存在最近的Commit
     */
    public static boolean hasRecentCommits(String directory) {
        return getRecentCommitHash(directory) != null;
    }

    /**
     * 获取最近5分钟内的最新commit哈希值
     *
     * @param directory 工作目录
     * @return 最新commit的哈希值，如果没有则返回null
     */
    public static String getRecentCommitHash(String directory) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.directory(new File(directory));
            processBuilder.command("git", "log", "--since=5.minutes", "--format=%H", "-n", "1");

            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String commitHash = reader.readLine();
            int exitCode = process.waitFor();

            if (exitCode == 0 && commitHash != null && !commitHash.trim().isEmpty()) {
                return commitHash.trim();
            }
        } catch (Exception e) {
            log.debug("获取最近commit hash时出错: {}", e.getMessage());
        }
        return null;
    }
}