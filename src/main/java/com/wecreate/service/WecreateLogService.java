package com.wecreate.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wecreate.entity.WecreateLog;

public interface WecreateLogService extends IService<WecreateLog> {
    /**
     * 关键词模糊分页查询
     * @param keyword 查询关键词
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    Page<WecreateLog> searchLogs(String keyword, Integer pageNum, Integer pageSize);
}