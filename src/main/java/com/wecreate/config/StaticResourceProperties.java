package com.wecreate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// 静态资源配置属性类
@Component
@ConfigurationProperties(prefix = "application.static-resource")
public class StaticResourceProperties {
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}