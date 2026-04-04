package com.wecreate.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wecreate.entity.WecreateProject;

import java.math.BigDecimal;
import java.util.List;

/**
 * 项目管理 Service 接口
 */
public interface WecreateProjectService extends IService<WecreateProject> {

    /**
     * 保存项目并自动 clone
     * @param project 项目信息
     * @return 是否成功
     */
    boolean saveProjectWithClone(WecreateProject project);

    /**
     * 重新 clone 项目
     * @param projectId 项目 ID
     * @return 是否成功
     */
    boolean recloneProject(BigDecimal projectId);

    /**
     * 删除项目及其本地文件
     * @param projectId 项目 ID
     * @return 是否成功
     */
    boolean deleteProjectWithLocalFiles(BigDecimal projectId);

    /**
     * 更新项目信息
     * @param project 项目信息
     * @return 是否成功
     */
    boolean updateProject(WecreateProject project);

    /**
     * 根据 ID 获取项目
     * @param id 项目 ID
     * @return 项目信息
     */
    WecreateProject getProjectById(BigDecimal id);

    /**
     * 获取所有项目列表
     * @return 项目列表
     */
    List<WecreateProject> getAllProjects();
}
