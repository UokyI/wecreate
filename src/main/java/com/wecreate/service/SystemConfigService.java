package com.wecreate.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wecreate.entity.SystemConfig;
import com.wecreate.mapper.SystemConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置服务
 */
@Slf4j
@Service
public class SystemConfigService {

    @Autowired
    private SystemConfigMapper systemConfigMapper;

    // 内存缓存配置
    private static final Map<String, SystemConfig> configCache = new HashMap<>();

    // 配置键常量
    public static final String KEY_QWEN_CLI_PATH = "QWEN_CLI_PATH";
    public static final String KEY_PROJECT_CLONE_PARENT_PATH = "PROJECT_CLONE_PARENT_PATH";
    public static final String KEY_MEMORY_ENABLED = "MEMORY_ENABLED";

    @Value("${application.qwen-path:}")
    private String configQwenPath;

    @Value("${application.project-clone-parent-path:}")
    private String configProjectCloneParentPath;

    /**
     * 应用启动时初始化配置数据
     */
    @PostConstruct
    public void init() {
        log.info("开始初始化系统配置...");
        
        // 初始化 Qwen CLI 路径配置
        String qwenPath = getQwenPathFromWhereCommand();
        if (qwenPath == null || qwenPath.isEmpty()) {
            qwenPath = configQwenPath;
        }
        if (qwenPath == null || qwenPath.isEmpty()) {
            qwenPath = "D:\\qwen.cmd";
        }
        initConfigIfNotExists(
            KEY_QWEN_CLI_PATH,
            qwenPath,
            "Qwen Code CLI 可执行文件路径（通过 where qwen 命令获取）"
        );
        
        // 初始化项目克隆父路径配置
        String cloneParentPath = configProjectCloneParentPath;
        if (cloneParentPath == null || cloneParentPath.isEmpty()) {
            cloneParentPath = "D:\\flow\\workspace";
        }
        initConfigIfNotExists(
            KEY_PROJECT_CLONE_PARENT_PATH,
            cloneParentPath,
            "项目克隆的默认父路径"
        );

        // 初始化记忆功能开关配置（默认 N，关闭状态）
        initConfigIfNotExists(
            KEY_MEMORY_ENABLED,
            "N",
            "项目执行记忆功能开关（Y=开启，N=关闭）。开启后，每次执行任务前会读取项目记忆文件，执行后会自动整理记忆。"
        );

        // 加载所有配置到缓存
        loadAllConfigsToCache();
        
        log.info("系统配置初始化完成");
    }

    /**
     * 执行 where qwen 命令获取 qwen 路径
     */
    private String getQwenPathFromWhereCommand() {
        try {
            log.info("执行 'where qwen' 命令获取 Qwen CLI 路径...");
            ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", "where qwen");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
            String line;
            String firstPath = null;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && line.endsWith(".cmd")) {
                    firstPath = line;
                    log.info("通过 where qwen 命令找到路径：{}", firstPath);
                    break;
                }
            }
            
            process.waitFor();
            return firstPath;
        } catch (IOException e) {
            log.warn("执行 'where qwen' 命令失败：{}", e.getMessage());
        } catch (InterruptedException e) {
            log.warn("执行 'where qwen' 命令被中断：{}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("执行 'where qwen' 命令发生异常：{}", e.getMessage());
        }
        return null;
    }

    /**
     * 如果配置不存在则初始化
     */
    private void initConfigIfNotExists(String key, String defaultValue, String description) {
        QueryWrapper<SystemConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("CONFIG_KEY", key);
        SystemConfig existingConfig = systemConfigMapper.selectOne(wrapper);
        
        if (existingConfig == null) {
            SystemConfig config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(defaultValue);
            config.setDescription(description);
            config.setCreateUser("System");
            config.setLmUser("System");
            systemConfigMapper.insert(config);
            log.info("初始化配置：{} = {}", key, defaultValue);
        } else {
            log.info("配置已存在：{} = {}", key, existingConfig.getConfigValue());
        }
    }

    /**
     * 加载所有配置到缓存
     */
    private void loadAllConfigsToCache() {
        List<SystemConfig> allConfigs = systemConfigMapper.selectList(null);
        configCache.clear();
        for (SystemConfig config : allConfigs) {
            configCache.put(config.getConfigKey(), config);
        }
        log.info("已加载 {} 条配置到缓存", configCache.size());
    }

    /**
     * 根据 key 获取配置值
     */
    public String getConfigValue(String key) {
        // 先从缓存获取
        SystemConfig config = configCache.get(key);
        if (config != null) {
            return config.getConfigValue();
        }
        
        // 缓存未命中，从数据库查询
        QueryWrapper<SystemConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("CONFIG_KEY", key);
        config = systemConfigMapper.selectOne(wrapper);
        
        if (config != null) {
            configCache.put(key, config);
            return config.getConfigValue();
        }
        
        return null;
    }

    /**
     * 获取 Qwen CLI 路径
     */
    public String getQwenCLIPath() {
        return getConfigValue(KEY_QWEN_CLI_PATH);
    }

    /**
     * 获取项目克隆父路径
     */
    public String getProjectCloneParentPath() {
        return getConfigValue(KEY_PROJECT_CLONE_PARENT_PATH);
    }

    /**
     * 判断记忆功能是否启用
     * @return true 表示记忆功能开启，false 表示关闭（默认）
     */
    public boolean isMemoryEnabled() {
        String value = getConfigValue(KEY_MEMORY_ENABLED);
        return "Y".equalsIgnoreCase(value);
    }

    /**
     * 更新配置值
     */
    public void updateConfigValue(String key, String value, String updateUser) {
        QueryWrapper<SystemConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("CONFIG_KEY", key);
        SystemConfig config = systemConfigMapper.selectOne(wrapper);
        
        if (config != null) {
            config.setConfigValue(value);
            config.setLmUser(updateUser);
            systemConfigMapper.updateById(config);
            
            // 更新缓存
            configCache.put(key, config);
            log.info("更新配置：{} = {}", key, value);
        } else {
            log.warn("配置不存在，无法更新：{}", key);
        }
    }

    /**
     * 刷新缓存
     */
    public void refreshCache() {
        loadAllConfigsToCache();
    }
}
