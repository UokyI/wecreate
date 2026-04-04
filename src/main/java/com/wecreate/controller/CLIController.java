package com.wecreate.controller;

import com.wecreate.config.annotation.LogTrace;
import com.wecreate.dto.ApiResult;
import com.wecreate.dto.BatchRequestItem;
import com.wecreate.dto.RequestsDTO;
import com.wecreate.service.QwenCodeCLIService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@LogTrace
@RestController
@RequestMapping("/api/cli")
public class CLIController {

    @Autowired
    private QwenCodeCLIService codeCliService;

    @PostMapping(value = "/async/{project}/{userId}/{projectId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult executeAsyncRequest(@PathVariable String project, @PathVariable String userId, @PathVariable String projectId, @RequestBody(required = false) String request) {
        log.info("{} 接收到请求: {}", MDC.get("rip"), request);
        codeCliService.processProjectRequestASync(project, userId, request, MDC.get("traceId"), MDC.get("rip"), projectId);
        return ApiResult.ok("success", "已接受需求，任务执行中，任务追踪ID: " + MDC.get("traceId"));
    }

    @PostMapping(value = "/async/batch/sequential/{userId}/{projectId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult executeSequentialBatchAsyncRequest(
            @PathVariable String userId,
            @PathVariable String projectId,
            @RequestBody RequestsDTO requestsDTO ,
            HttpServletRequest httpServletRequest) {
        List<BatchRequestItem> batchRequests = requestsDTO.getRequests();
        // 将当前 projectId 填充到每个请求项中（前端无需传递 project 字段）
        if (batchRequests != null) {
            for (BatchRequestItem item : batchRequests) {
                if (item.getProject() == null || item.getProject().isEmpty()) {
                    item.setProject(projectId);
                }
            }
        }
        log.info("{} 接收到顺序批量请求，项目数量: {}", MDC.get("rip"), batchRequests.size());
        String batchTraceId = MDC.get("traceId");
        codeCliService.processBatchSequentialRequest(batchRequests, userId, batchTraceId, MDC.get("rip"), projectId);
        return ApiResult.ok(batchTraceId, batchTraceId);
    }

    @PostMapping(value = "/execute/{projectId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeRequest(@PathVariable String projectId,
                                     @RequestParam(required = false, defaultValue = "qwen3.6-plus") String model,
                                     @RequestBody(required = false) String request,
                                     HttpServletRequest httpServletRequest) {
        log.info("{} 接收到执行请求，项目ID: {}, 模型: {}", MDC.get("rip"), projectId, model);
        // 将所有逻辑委托给Service处理
        return codeCliService.processProjectRequestByProjectId(projectId, request, MDC.get("rip"), model);
    }

    @PostMapping(value = "/execute/file/{projectId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeRequestFromFile(@PathVariable String projectId,
                                             @RequestParam(required = false, defaultValue = "qwen3.6-plus") String model,
                                             @RequestBody String requestContent,
                                             HttpServletRequest httpServletRequest) {
        log.info("{} 接收到文件内容执行请求，项目ID: {}, 模型: {}", MDC.get("rip"), projectId, model);
        return codeCliService.processProjectRequestByProjectId(projectId, requestContent, MDC.get("rip"), model);
    }

    @PostMapping(value = "/sync/{project}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult executeSyncRequest(@PathVariable String project, @RequestBody(required = false) String request, HttpServletRequest httpServletRequest) {
        log.info("{} 接收到同步请求: {}", MDC.get("rip"), request);
        List<String> responseMessages = new ArrayList<>();
        boolean success = codeCliService.processRequestSync(project, request, responseMessages, MDC.get("rip"), "System");
        if (success) {
            return ApiResult.ok("success", responseMessages);
        } else {
            return ApiResult.error("error", responseMessages);
        }
    }
}
