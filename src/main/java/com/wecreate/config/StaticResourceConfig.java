package com.wecreate.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Autowired
    private StaticResourceProperties staticResourceProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 静态资源映射
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/").setCachePeriod(0); // 禁用缓存以便于开发调试
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (!staticResourceProperties.isEnabled()) {
            registry.addInterceptor(new StaticResourceInterceptor()).addPathPatterns("/**/*.html").excludePathPatterns("/pro/index.html", "/debug.html");
        }
    }

    private class StaticResourceInterceptor extends HandlerInterceptorAdapter {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            if (!staticResourceProperties.isEnabled()) {
                // 检查请求的路径是否是被排除的路径
                String requestURI = request.getRequestURI();
                if ("/pro/index.html".equals(requestURI) || "/debug.html".equals(requestURI)) {
                    return true; // 允许访问
                }

                // 对于其他静态HTML文件，返回403 Forbidden
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Static resources access is disabled");
                return false;
            }
            return true;
        }
    }
}