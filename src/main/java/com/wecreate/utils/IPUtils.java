package com.wecreate.utils;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Project：WeCreate
 * Date：2025/11/21
 * Time：14:56
 * Description：TODO
 *
 * @author UokyI
 * @version 1.0
 */
@Slf4j
public class IPUtils {

    public static String getClientIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多个IP的情况，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0];
        }
        String clientIp = ip;
        // 如果是本地回环地址，尝试获取本机实际IP
        if (isLocalAddress(ip)) {
            String actualIP = getActualLocalIP();
            if (actualIP != null) {
                clientIp = actualIP;
            }
        }
        return clientIp;
    }

    // 判断是否为本地地址
    public static boolean isLocalAddress(String ip) {
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
    }

    // 获取本机实际IP地址
    public static String getActualLocalIP() {
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            String hostAddress = localhost.getHostAddress();
            // 排除回环地址
            if (!hostAddress.startsWith("127.")) {
                return hostAddress;
            }
        } catch (UnknownHostException e) {
            log.warn("无法获取本机IP地址: ", e);
        }
        return null;
    }

}
