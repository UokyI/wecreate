package com.wecreate.controller;

import com.wecreate.config.annotation.LogTrace;
import com.wecreate.service.PathService;
import com.wecreate.utils.IPUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@LogTrace
@Slf4j
@RestController
@RequestMapping("/api/userpath")
public class UserPathController {

    @Autowired
    private PathService pathService;

    @Value("${server.port:8080}")
    private int serverPort;


    @GetMapping("/info")
    public ResponseEntity<Map<String, String>> getUserInfo(HttpServletRequest request, String url) {
        Map<String, String> response = pathService.getUserInfo(request, url);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取局域网访问地址（IP + 端口 + 协议）
     */
    @GetMapping("/lan-address")
    public ResponseEntity<Map<String, String>> getLanAddress(HttpServletRequest request) {
        Map<String, String> response = new HashMap<>();
        String scheme = request.getScheme();
        String localIp = IPUtils.getActualLocalIP();
        if (localIp != null) {
            response.put("url", scheme + "://" + localIp + ":" + serverPort);
            response.put("ip", localIp);
            response.put("port", String.valueOf(serverPort));
            response.put("scheme", scheme);
        } else {
            // 如果获取不到局域网 IP，返回 localhost
            response.put("url", scheme + "://localhost:" + serverPort);
            response.put("ip", "localhost");
            response.put("port", String.valueOf(serverPort));
            response.put("scheme", scheme);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 获取访问者 IP 地址
     */
    @GetMapping("/ip")
    public ResponseEntity<Map<String, String>> getIP(HttpServletRequest request) {
        Map<String, String> response = new HashMap<>();
        String ip = IPUtils.getClientIP(request);
        response.put("ip", ip);
        
        // 如果是本地地址，也返回实际 IP
        if (IPUtils.isLocalAddress(ip)) {
            String actualIP = IPUtils.getActualLocalIP();
            if (actualIP != null) {
                response.put("actualIP", actualIP);
            }
        }
        
        log.info("获取访问者 IP: {}", ip);
        return ResponseEntity.ok(response);
    }

}