package com.wecreate.config.aspect;

import com.wecreate.utils.IPUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 链路追踪
 *
 * @author
 */
@Aspect
@Component
@Slf4j
public class LogTraceAspect {

    /**
     * 与 logback-spring.xml 中的变量一致
     */
    private static final String TRACE_ID = "traceId";

    /**
     * 定义切点 切点为
     */
    @Pointcut("@within(com.wecreate.config.annotation.LogTrace)")
    public void printLog() {
    }

    /**
     * 环绕通知
     */
//    @Around(value = "printLog()")
//    public Object weblogAround(ProceedingJoinPoint joinPoint) throws Throwable {
//        // 方法执行前加上链路号
//        String traceId = UUID.randomUUID().toString().replaceAll("-", "");
//        MDC.put(TRACE_ID, traceId);
//        Object proceed = joinPoint.proceed();
//        MDC.remove(TRACE_ID);
//        return proceed;
//    }

    // 在 LogTraceAspect 中添加
    private static final ThreadLocal<String> TRACE_ID_THREAD_LOCAL = new InheritableThreadLocal<>();

    @Around(value = "printLog()")
    public Object weblogAround(ProceedingJoinPoint joinPoint) throws Throwable {
//        String traceId = UUID.randomUUID().toString().replaceAll("-", "");
        // 使用 ThreadLocalRandom 生成6位随机数，性能最佳
        String traceId = Instant.now().getEpochSecond() + String.format("%06d", ThreadLocalRandom.current().nextInt(900000) + 100000);
        
        // 获取用户IP地址
        String userIP = getClientIP();
        
        MDC.put(TRACE_ID, traceId);
        MDC.put("rip", userIP); // 使用用户IP作为rip的值
        TRACE_ID_THREAD_LOCAL.set(traceId);

        try {
            return joinPoint.proceed();
        } finally {
            MDC.remove(TRACE_ID);
            MDC.remove("rip"); // 清理rid键值对
            TRACE_ID_THREAD_LOCAL.remove();
        }
    }
    
    /**
     * 获取客户端IP地址
     * @return 客户端IP地址
     */
    private String getClientIP() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "unknown";
            }
            
            HttpServletRequest request = attributes.getRequest();
            return IPUtils.getClientIP(request);
        } catch (Exception e) {
            log.warn("获取客户端IP失败: ", e);
            return "unknown";
        }
    }

}