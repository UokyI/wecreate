package com.wecreate.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wecreate.entity.WecreateLog;
import com.wecreate.service.WecreateLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class CLILogController {

    @Autowired
    private WecreateLogService wecreateLogService;

    /**
     * 根据ID获取日志记录
     */
    @GetMapping("/{id}")
    public WecreateLog getLogById(@PathVariable Long id) {
        return wecreateLogService.getById(id);
    }

    /**
     * 获取所有日志记录
     */
    @GetMapping
    public List<WecreateLog> getAllLogs() {
        return wecreateLogService.list();
    }

    /**
     * 分页查询日志记录
     */
    @GetMapping("/page")
    public Page<WecreateLog> getLogsByPage(@RequestParam(defaultValue = "1") Integer pageNum,
                                       @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<WecreateLog> page = new Page<>(pageNum, pageSize);
        return wecreateLogService.page(page);
    }

    /**
     * 根据traceId查询日志记录
     */
    @GetMapping("/trace/{traceId}")
    public List<WecreateLog> getLogsByTraceId(@PathVariable String traceId) {
        QueryWrapper<WecreateLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("TRACE_ID", traceId);
        return wecreateLogService.list(queryWrapper);
    }

    /**
     * 根据traceId查询日志记录 (POST方式)
     */
    @PostMapping("/trace")
    public List<WecreateLog> getLogsByTraceId(@RequestBody Map<String, String> payload) {
        String traceId = payload.get("traceId");
        QueryWrapper<WecreateLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("TRACE_ID", traceId);

        String asc = payload.get("asc");
        if (asc.equals("true")) {
            queryWrapper.orderByAsc("CREATE_TIME");
        } else {
            queryWrapper.orderByDesc("CREATE_TIME");
        }
        return wecreateLogService.list(queryWrapper);
    }

    /**
     * 关键词模糊分页查询
     */
    @GetMapping("/search")
    public Page<WecreateLog> searchLogs(@RequestParam(required = false) String keyword,
                                    @RequestParam(defaultValue = "1") Integer pageNum,
                                    @RequestParam(defaultValue = "10") Integer pageSize) {
        return wecreateLogService.searchLogs(keyword, pageNum, pageSize);
    }

    /**
     * 根据ID删除日志记录
     */
    @DeleteMapping("/{id}")
    public boolean deleteLog(@PathVariable Long id) {
        return wecreateLogService.removeById(id);
    }
}