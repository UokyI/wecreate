package com.wecreate.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wecreate.dto.*;
import com.wecreate.entity.WecreatePrompt;

import java.math.BigDecimal;
import java.util.List;

public interface WecreatePromptService extends IService<WecreatePrompt> {

    /**
     * 分页查询提示词记录，支持关键词搜索、IP筛选和时间段筛选
     */
    Page<WecreatePrompt> searchPromptsWithConditions(PromptQueryDTO queryDTO);

    /**
     * 根据traceId为提示词评分
     */
    boolean scorePromptByTraceId(String traceId, BigDecimal score, String userId);

    /**
     * 为提示词评分
     */
    boolean scorePrompt(PromptScoreDTO promptScoreDTO, String clientIP);

}