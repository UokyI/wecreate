package com.wecreate.service;

import com.wecreate.utils.IPUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class PathService {

    /**
     * 根据请求获取用户信息和可操作路径
     *
     * @param request HttpServletRequest对象
     * @param url     请求URL
     * @return 包含用户信息和路径的Map
     */
    public Map<String, String> getUserInfo(HttpServletRequest request, String url) {
        Map<String, String> response = new HashMap<>();
        try {
            // 获取客户端IP地址
            String ip = IPUtils.getClientIP(request);
            response.put("ip", ip);
            response.put("path", "-");
            response.put("status", "success");
            log.info("{} PathService.getUserInfo 获取操作路径：{}", ip, response.get("path"));
        } catch (Exception e) {
            log.error("获取用户信息时发生错误: ", e);
            response.put("status", "error");
            response.put("message", "系统错误: " + e.getMessage());
        }

        return response;
    }
}