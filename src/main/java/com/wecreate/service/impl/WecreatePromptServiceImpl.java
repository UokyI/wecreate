package com.wecreate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wecreate.config.exception.InvalidScoreException;
import com.wecreate.config.exception.PromptNotFoundException;
import com.wecreate.dto.PromptQueryDTO;
import com.wecreate.dto.PromptScoreDTO;
import com.wecreate.entity.WecreatePrompt;
import com.wecreate.mapper.WecreatePromptMapper;
import com.wecreate.service.WecreatePromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
public class WecreatePromptServiceImpl extends ServiceImpl<WecreatePromptMapper, WecreatePrompt> implements WecreatePromptService {


    @Override
    public Page<WecreatePrompt> searchPromptsWithConditions(PromptQueryDTO queryDTO) {
        Page<WecreatePrompt> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        QueryWrapper<WecreatePrompt> queryWrapper = new QueryWrapper<>();

        // 关键词搜索
        if (queryDTO.getKeyword() != null && !queryDTO.getKeyword().trim().isEmpty()) {
            queryWrapper.like("PROMPT_CONTENT", queryDTO.getKeyword());
        }

        // IP筛选
        if (queryDTO.getIp() != null && !queryDTO.getIp().trim().isEmpty()) {
            queryWrapper.eq("REQUEST_IP", queryDTO.getIp());
        }

        // 时间段筛选
        if (queryDTO.getStartTime() != null) {
            queryWrapper.ge("CREATE_TIME", queryDTO.getStartTime());
        }
        if (queryDTO.getEndTime() != null) {
            queryWrapper.le("CREATE_TIME", queryDTO.getEndTime());
        }

        // 项目ID筛选
        if (queryDTO.getProjectId() != null && !queryDTO.getProjectId().trim().isEmpty()) {
            queryWrapper.eq("PROJECT_ID", queryDTO.getProjectId());
        }

        // 按创建时间降序排列
        queryWrapper.orderByDesc("CREATE_TIME");

        return this.page(page, queryWrapper);
    }

    @Override
    public boolean scorePromptByTraceId(String traceId, BigDecimal score, String userId) {
        // 根据traceId查找记录
        QueryWrapper<WecreatePrompt> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("TRACE_ID", traceId);
        WecreatePrompt prompt = this.getOne(queryWrapper);

        if (prompt != null) {
            // 更新评分和评分人
            prompt.setScore(score);
            prompt.setScoreUser(userId);
            prompt.setLmTime(LocalDateTime.now());
            return this.updateById(prompt);
        }

        return false;
    }

    @Override
    public boolean scorePrompt(PromptScoreDTO promptScoreDTO, String clientIP) {
        String traceId = promptScoreDTO.getTraceId();
        BigDecimal score = promptScoreDTO.getScore();
        String userId = promptScoreDTO.getUserId();

        // 验证score范围是否在0-10之间
        if (score != null && (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.TEN) > 0)) {
            throw new InvalidScoreException();
        }

        // 如果没有传评分人，则设定为客户端IP
        if (userId == null || userId.isEmpty()) {
            userId = clientIP;
        }

        // 尝试评分，如果找不到记录则抛出异常
        boolean result = scorePromptByTraceId(traceId, score, userId);
        if (!result) {
            throw new PromptNotFoundException();
        }
        return true;
    }
}