package com.wecreate.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wecreate.config.annotation.LogTrace;
import com.wecreate.dto.ApiResult;
import com.wecreate.dto.PromptQueryDTO;
import com.wecreate.dto.PromptScoreDTO;
import com.wecreate.entity.WecreateLog;
import com.wecreate.entity.WecreatePrompt;
import com.wecreate.service.WecreateLogService;
import com.wecreate.service.WecreatePromptService;
import com.wecreate.utils.IPUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@LogTrace
@RestController
@RequestMapping("/api/prompts")
public class PromptController {

    @Autowired
    private WecreatePromptService promptService;

    @Autowired
    private WecreateLogService logService;

    /**
     * 根据ID获取提示记录
     */
    @GetMapping("/{id}")
    public WecreatePrompt getPromptById(@PathVariable Long id) {
        return promptService.getById(id);
    }

    /**
     * 获取所有提示记录
     */
    @GetMapping
    public List<WecreatePrompt> getAllPrompts() {
        return promptService.list();
    }

    /**
     * 获取所有不同的请求IP地址
     */
    @GetMapping("/ips")
    public List<String> getAllIps() {
        QueryWrapper<WecreatePrompt> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("DISTINCT REQUEST_IP");
        List<WecreatePrompt> prompts = promptService.list(queryWrapper);
        return prompts.stream()
                .map(WecreatePrompt::getRequestIp)
                .filter(ip -> ip != null && !ip.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 统一分页查询接口，支持关键词搜索、IP筛选和时间段筛选
     */
    @PostMapping("/page")
    public Page<WecreatePrompt> getPromptsByPage(@RequestBody PromptQueryDTO queryDTO) {
        return promptService.searchPromptsWithConditions(queryDTO);
    }

    /**
     * 根据提示词的traceId获取关联的日志记录
     */
    @PostMapping("/logs")
    public List<WecreateLog> getLogsByPromptId(@RequestBody Map<String, Object> payload) {
        BigDecimal id = new BigDecimal(payload.get("id").toString());
        WecreatePrompt prompt = promptService.getById(id);
        if (prompt != null && prompt.getTraceId() != null) {
            QueryWrapper<WecreateLog> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("TRACE_ID", prompt.getTraceId());
            queryWrapper.orderByAsc("CREATE_TIME");
            return logService.list(queryWrapper);
        }
        return new ArrayList<>();
    }

    /**
     * 更新提示记录
     */
    @PutMapping
    public boolean updatePrompt(@RequestBody WecreatePrompt prompt) {
        prompt.setLmTime(LocalDateTime.now());
        return promptService.updateById(prompt);
    }

    /**
     * 根据ID删除提示记录
     */
    @DeleteMapping("/{id}")
    public boolean deletePrompt(@PathVariable Long id) {
        return promptService.removeById(id);
    }

    /**
     * 为提示词评分
     */
    @PostMapping("/score")
    public ApiResult scorePrompt(@RequestBody PromptScoreDTO promptScoreDTO, HttpServletRequest request) {
        String clientIP = IPUtils.getClientIP(request);
        boolean result = promptService.scorePrompt(promptScoreDTO, clientIP);
        if (result) {
            return ApiResult.ok("评分成功");
        } else {
            return ApiResult.fail("评分失败，未找到对应的提示词记录");
        }
    }

}