package com.wecreate.controller;

import com.wecreate.dto.ApiResult;
import com.wecreate.service.QwenCodeCLIService;
import com.wecreate.utils.IPUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private QwenCodeCLIService agentService;

    @GetMapping(value = "/net")
    public String executeRequest() {
        log.info("测试网络连通验证请求", getClass().getSimpleName());
        return "ok";
    }

    @PostMapping(value = "/test/execute", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult executeTestRequest(@RequestBody(required = false) String request, HttpServletRequest httpServletRequest) {
        String ip = IPUtils.getClientIP(httpServletRequest);
        log.info("{} {} 接收到请求: {}", ip, getClass().getSimpleName(), request);
        List<String> responseMessages = new ArrayList<>();

        try {
            // 直接同步执行任务
            agentService.processRequestSync(request != null ? request : "", responseMessages, "D:\\yuan\\workspace\\ideaproj\\11-11", "System", "System");
        } catch (Exception e) {
            log.error("处理请求时发生错误: {}", e.getMessage(), e);
            responseMessages.add("系统错误: " + e.getMessage());
        }
        return ApiResult.ok(responseMessages);
    }

    /**
     * 数据字典转为 JSON
     *
     * @return JSON 字符串
     */
//    @PostMapping("/file")
//    public ApiResult batch(@RequestPart("file") MultipartFile file) throws Exception {
//        return  ApiResult.ok(readDataDictionary(file.getInputStream(), file.getOriginalFilename()));
//    }
    @PostMapping(value = "/sync/execute", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult executeTestSyncRequest(@RequestBody(required = false) String request, HttpServletRequest httpServletRequest) {
        String ip = IPUtils.getClientIP(httpServletRequest);
        log.info("{} {} 接收到请求: {}", ip, getClass().getSimpleName(), request);
        agentService.processProjectRequestASync("test", "System", request, MDC.get("traceId"), MDC.get("rid"), "System");
        return ApiResult.ok("执行中");
    }

}