package com.wecreate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wecreate.entity.WecreateProject;
import com.wecreate.mapper.WecreateProjectMapper;
import com.wecreate.service.SystemConfigService;
import com.wecreate.service.WecreateProjectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目管理 Service 实现类
 */
@Slf4j
@Service
public class WecreateProjectServiceImpl extends ServiceImpl<WecreateProjectMapper, WecreateProject> implements WecreateProjectService {

    @Autowired
    private SystemConfigService systemConfigService;

    /**
     * 获取项目克隆父路径（从配置表）
     */
    private String getProjectCloneParentPath() {
        String path = systemConfigService.getProjectCloneParentPath();
        if (path == null || path.isEmpty()) {
            path = "D:\\yuan\\workspace\\ideaproj";
        }
        // 统一使用正斜杠
        return path.replace("\\", "/");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveProjectWithClone(WecreateProject project) {
        // 1. 生成项目名称（如果未提供）
        if (project.getProjectName() == null || project.getProjectName().trim().isEmpty()) {
            // 从 Git URL 提取项目名称
            String gitUrl = project.getGitUrl();
            if (gitUrl != null && !gitUrl.isEmpty()) {
                // 处理 https://github.com/user/repo.git 或 git@github.com:user/repo.git 格式
                String[] parts = gitUrl.replace(".git", "").split("/");
                project.setProjectName(parts[parts.length - 1]);
            } else {
                throw new IllegalArgumentException("项目名称和 Git 地址不能同时为空");
            }
        }

        // 2. 生成本地路径（使用配置表中的父路径）
        String localPath = getProjectCloneParentPath() + "/" + project.getProjectName();
        project.setLocalPath(localPath);

        // 3. 检查项目名称是否已存在
        QueryWrapper<WecreateProject> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("PROJECT_NAME", project.getProjectName());
        WecreateProject existing = this.getOne(queryWrapper);
        if (existing != null) {
            throw new RuntimeException("项目名称已存在：" + project.getProjectName());
        }

        // 4. 设置初始状态
        project.setStatus(0); // 未克隆
        project.setCreateTime(LocalDateTime.now());
        project.setLmTime(LocalDateTime.now());

        // 5. 保存到数据库
        boolean saved = this.save(project);
        if (!saved) {
            throw new RuntimeException("保存项目信息失败");
        }

        // 6. 执行 Git clone
        try {
            boolean cloneResult = executeGitClone(project.getGitUrl(), localPath);
            if (cloneResult) {
                project.setStatus(1); // 已克隆
            } else {
                project.setStatus(2); // 克隆失败
            }
            this.updateById(project);
        } catch (Exception e) {
            log.error("Git clone 失败：{}", e.getMessage(), e);
            project.setStatus(2);
            project.setRemark("克隆失败：" + e.getMessage());
            this.updateById(project);
            throw new RuntimeException("Git clone 失败：" + e.getMessage(), e);
        }

        return true;
    }

    @Override
    public boolean recloneProject(BigDecimal projectId) {
        WecreateProject project = this.getById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }

        // 删除本地已存在的项目目录
        File projectDir = new File(project.getLocalPath());
        if (projectDir.exists()) {
            deleteDirectory(projectDir);
        }

        // 重新执行 clone
        try {
            boolean cloneResult = executeGitClone(project.getGitUrl(), project.getLocalPath());
            if (cloneResult) {
                project.setStatus(1);
                project.setRemark(null);
            } else {
                project.setStatus(2);
                project.setRemark("重新克隆失败");
            }
            project.setLmTime(LocalDateTime.now());
            this.updateById(project);
            return cloneResult;
        } catch (Exception e) {
            log.error("重新克隆项目失败：{}", e.getMessage(), e);
            project.setStatus(2);
            project.setRemark("重新克隆失败：" + e.getMessage());
            project.setLmTime(LocalDateTime.now());
            this.updateById(project);
            throw new RuntimeException("重新克隆失败：" + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteProjectWithLocalFiles(BigDecimal projectId) {
        WecreateProject project = this.getById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }

        // 删除本地项目目录
        String localPath = project.getLocalPath();
        if (localPath != null && !localPath.isEmpty()) {
            File projectDir = new File(localPath);
            if (projectDir.exists()) {
                boolean deleted = deleteDirectory(projectDir);
                if (!deleted) {
                    log.warn("删除本地项目目录失败：{}", localPath);
                }
            }
        }

        // 从数据库删除
        boolean removed = this.removeById(projectId);
        if (!removed) {
            throw new RuntimeException("从数据库删除项目失败");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateProject(WecreateProject project) {
        WecreateProject existing = this.getById(project.getId());
        if (existing == null) {
            throw new RuntimeException("项目不存在");
        }

        // 不允许修改本地路径
        project.setLocalPath(existing.getLocalPath());
        project.setLmTime(LocalDateTime.now());

        return this.updateById(project);
    }

    @Override
    public WecreateProject getProjectById(BigDecimal id) {
        return this.getById(id);
    }

    @Override
    public List<WecreateProject> getAllProjects() {
        QueryWrapper<WecreateProject> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("CREATE_TIME");
        return this.list(queryWrapper);
    }

    /**
     * 执行 Git clone 命令
     */
    private boolean executeGitClone(String gitUrl, String localPath) throws IOException, InterruptedException {
        // 确保工作目录存在（使用配置表中的父路径）
        File workspaceDir = new File(getProjectCloneParentPath());
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs();
        }

        // 构建 git clone 命令
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("git", "clone", gitUrl, localPath);
        processBuilder.redirectErrorStream(true);

        log.info("执行 Git clone: git clone {} {}", gitUrl, localPath);

        Process process = processBuilder.start();

        // 读取输出
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("Git clone 输出：{}", line);
            }
        }

        // 等待命令执行完成，设置超时时间为 10 分钟
        boolean completed = process.waitFor(10, java.util.concurrent.TimeUnit.MINUTES);
        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("Git clone 超时（10 分钟）");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.error("Git clone 失败，退出码：{}", exitCode);
            return false;
        }

        // 验证克隆是否成功
        File projectDir = new File(localPath);
        if (!projectDir.exists() || !new File(localPath + "/.git").exists()) {
            log.error("Git clone 后目录验证失败");
            return false;
        }

        log.info("Git clone 成功：{}", localPath);
        return true;
    }

    /**
     * 递归删除目录
     * @param directory 要删除的目录
     * @return 是否删除成功
     */
    private boolean deleteDirectory(File directory) {
        if (!directory.exists()) {
            return true;
        }
        
        try {
            // 先获取所有文件和子目录
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        // 递归删除子目录
                        if (!deleteDirectory(file)) {
                            log.warn("删除子目录失败：{}", file.getAbsolutePath());
                        }
                    } else {
                        // 删除文件，尝试多次以应对文件占用
                        boolean deleted = tryDeleteFile(file, 3);
                        if (!deleted) {
                            log.warn("删除文件失败：{}", file.getAbsolutePath());
                        }
                    }
                }
            }
            
            // 最后删除目录本身
            return directory.delete();
        } catch (Exception e) {
            log.error("删除目录失败：{}", directory.getAbsolutePath(), e);
            return false;
        }
    }
    
    /**
     * 尝试多次删除文件（应对文件被占用的情况）
     * @param file 要删除的文件
     * @param maxAttempts 最大尝试次数
     * @return 是否删除成功
     */
    private boolean tryDeleteFile(File file, int maxAttempts) {
        for (int i = 0; i < maxAttempts; i++) {
            try {
                // 尝试设置文件可删除（某些情况下需要）
                file.setWritable(true);
                if (file.delete()) {
                    return true;
                }
                // 如果删除失败，等待一段时间后重试
                if (i < maxAttempts - 1) {
                    log.info("文件删除失败，{}ms 后重试：{}", (i + 1) * 500, file.getAbsolutePath());
                    Thread.sleep((i + 1) * 500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("删除文件时被中断：{}", file.getAbsolutePath(), e);
                return false;
            } catch (Exception e) {
                log.warn("删除文件失败（尝试 {}/{}）: {}", i + 1, maxAttempts, file.getAbsolutePath(), e);
            }
        }
        return false;
    }
}
