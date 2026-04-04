package com.wecreate.controller;

import com.wecreate.config.annotation.LogTrace;
import com.wecreate.dto.ApiResult;
import com.wecreate.entity.WecreateProject;
import com.wecreate.service.WecreateProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 项目管理控制器
 */
@LogTrace
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private WecreateProjectService projectService;

    /**
     * 获取所有项目列表
     */
    @GetMapping
    public ApiResult getAllProjects() {
        List<WecreateProject> projects = projectService.getAllProjects();
        // 为每个项目添加 IP 信息
        List<Map<String, Object>> projectsWithIP = projects.stream().map(project -> {
            Map<String, Object> projectMap = new HashMap<>();
            projectMap.put("id", project.getId());
            projectMap.put("projectName", project.getProjectName());
            projectMap.put("gitUrl", project.getGitUrl());
            projectMap.put("localPath", project.getLocalPath());
            projectMap.put("description", project.getDescription());
            projectMap.put("createUser", project.getCreateUser());
            projectMap.put("createTime", project.getCreateTime());
            projectMap.put("lmUser", project.getLmUser());
            projectMap.put("lmTime", project.getLmTime());
            projectMap.put("status", project.getStatus());
            projectMap.put("remark", project.getRemark());
            return projectMap;
        }).collect(Collectors.toList());
        return ApiResult.ok(projectsWithIP);
    }

    /**
     * 根据 ID 获取项目
     */
    @GetMapping("/{id}")
    public ApiResult getProjectById(@PathVariable BigDecimal id) {
        WecreateProject project = projectService.getProjectById(id);
        if (project == null) {
            return ApiResult.fail("项目不存在");
        }
        return ApiResult.ok(project);
    }

    /**
     * 新增项目并自动 clone
     */
    @PostMapping
    public ApiResult createProject(@RequestBody WecreateProject project) {
        try {
            projectService.saveProjectWithClone(project);
            return ApiResult.ok("项目创建成功");
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(e.getMessage());
        } catch (RuntimeException e) {
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResult.fail("创建项目失败：" + e.getMessage());
        }
    }

    /**
     * 更新项目信息
     */
    @PutMapping
    public ApiResult updateProject(@RequestBody WecreateProject project) {
        try {
            boolean result = projectService.updateProject(project);
            return result ? ApiResult.ok("更新成功") : ApiResult.fail("更新失败");
        } catch (RuntimeException e) {
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResult.fail("更新项目失败：" + e.getMessage());
        }
    }

    /**
     * 删除项目
     */
    @DeleteMapping("/{id}")
    public ApiResult deleteProject(@PathVariable BigDecimal id) {
        try {
            boolean result = projectService.deleteProjectWithLocalFiles(id);
            return result ? ApiResult.ok("删除成功") : ApiResult.fail("删除失败");
        } catch (RuntimeException e) {
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResult.fail("删除项目失败：" + e.getMessage());
        }
    }

    /**
     * 重新 clone 项目
     */
    @PostMapping("/{id}/reclone")
    public ApiResult recloneProject(@PathVariable BigDecimal id) {
        try {
            boolean result = projectService.recloneProject(id);
            return result ? ApiResult.ok("重新克隆成功") : ApiResult.fail("重新克隆失败");
        } catch (RuntimeException e) {
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResult.fail("重新克隆失败：" + e.getMessage());
        }
    }
}
