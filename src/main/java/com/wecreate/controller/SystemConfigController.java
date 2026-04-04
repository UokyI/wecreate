package com.wecreate.controller;

import com.wecreate.config.annotation.LogTrace;
import com.wecreate.dto.ApiResult;
import com.wecreate.entity.WecreateProject;
import com.wecreate.service.MemoryService;
import com.wecreate.service.SystemConfigService;
import com.wecreate.service.WecreateProjectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 系统配置管理 Controller
 */
@Slf4j
@LogTrace
@RestController
@RequestMapping("/api/config")
public class SystemConfigController {

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private MemoryService memoryService;

    @Autowired
    private WecreateProjectService wecreateProjectService;

    /**
     * 获取所有系统配置列表
     */
    @GetMapping("/list")
    public ApiResult getConfigList() {
        try {
            List<Map<String, Object>> configs = new ArrayList<>();

            // 记忆功能开关
            configs.add(buildConfigItem(
                    SystemConfigService.KEY_MEMORY_ENABLED,
                    "记忆功能开关",
                    systemConfigService.getConfigValue(SystemConfigService.KEY_MEMORY_ENABLED),
                    "Y/N，控制是否在项目执行时启用记忆功能",
                    "select"
            ));

            // Qwen CLI 路径
            configs.add(buildConfigItem(
                    SystemConfigService.KEY_QWEN_CLI_PATH,
                    "Qwen CLI 路径",
                    systemConfigService.getConfigValue(SystemConfigService.KEY_QWEN_CLI_PATH),
                    "Qwen Code CLI 可执行文件路径",
                    "text"
            ));

            // 项目克隆父路径
            configs.add(buildConfigItem(
                    SystemConfigService.KEY_PROJECT_CLONE_PARENT_PATH,
                    "项目克隆父路径",
                    systemConfigService.getConfigValue(SystemConfigService.KEY_PROJECT_CLONE_PARENT_PATH),
                    "项目克隆的默认父路径",
                    "text"
            ));

            return ApiResult.ok(configs);
        } catch (Exception e) {
            log.error("获取配置列表失败", e);
            return ApiResult.error("获取配置列表失败: " + e.getMessage());
        }
    }

    /**
     * 更新配置值
     */
    @PostMapping("/update")
    public ApiResult updateConfig(@RequestBody Map<String, String> params) {
        try {
            String key = params.get("key");
            String value = params.get("value");
            String updateUser = params.getOrDefault("updateUser", "System");

            if (key == null || key.isEmpty()) {
                return ApiResult.error("配置键不能为空");
            }

            systemConfigService.updateConfigValue(key, value, updateUser);
            return ApiResult.ok("配置更新成功");
        } catch (Exception e) {
            log.error("更新配置失败", e);
            return ApiResult.error("更新配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取项目的记忆文件列表
     */
    @GetMapping("/memory/list/{projectId}")
    public ApiResult getMemoryFiles(@PathVariable String projectId) {
        try {
            WecreateProject project = wecreateProjectService.getById(new java.math.BigDecimal(projectId));
            if (project == null) {
                return ApiResult.error("项目不存在");
            }

            List<String> files = memoryService.listMemoryFiles(project.getLocalPath());
            return ApiResult.ok(files);
        } catch (Exception e) {
            log.error("获取记忆文件列表失败", e);
            return ApiResult.error("获取记忆文件列表失败: " + e.getMessage());
        }
    }

    /**
     * 读取指定记忆文件内容
     */
    @GetMapping("/memory/read/{projectId}")
    public ApiResult readMemoryFile(@PathVariable String projectId, @RequestParam String fileName) {
        try {
            WecreateProject project = wecreateProjectService.getById(new java.math.BigDecimal(projectId));
            if (project == null) {
                return ApiResult.error("项目不存在");
            }

            String content = memoryService.readMemoryFile(project.getLocalPath(), fileName);
            Map<String, String> result = new HashMap<>();
            result.put("fileName", fileName);
            result.put("content", content != null ? content : "");
            return ApiResult.ok(result);
        } catch (Exception e) {
            log.error("读取记忆文件失败", e);
            return ApiResult.error("读取记忆文件失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildConfigItem(String key, String label, String value, String description, String type) {
        Map<String, Object> item = new HashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("value", value);
        item.put("description", description);
        item.put("type", type);
        return item;
    }

    /**
     * 导出项目记忆文件夹为 ZIP 压缩包
     */
    @GetMapping("/memory/export/{projectId}")
    public ResponseEntity<byte[]> exportMemory(@PathVariable String projectId) {
        try {
            WecreateProject project = wecreateProjectService.getById(new BigDecimal(projectId));
            if (project == null) {
                return ResponseEntity.badRequest().build();
            }

            String projectPath = project.getLocalPath();
            if (projectPath == null || projectPath.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            File memoryDir = new File(projectPath, ".memory");
            if (!memoryDir.exists() || !memoryDir.isDirectory()) {
                return ResponseEntity.badRequest().build();
            }

            // 创建 ZIP 输出流
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                zipDirectory(memoryDir, "", zos);
            }

            // 返回 ZIP 文件
            String fileName = project.getProjectName() + "_memory_" + System.currentTimeMillis() + ".zip";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", new String(fileName.getBytes("UTF-8"), "ISO-8859-1"));
            headers.setContentLength(baos.size());

            return ResponseEntity.ok().headers(headers).body(baos.toByteArray());
        } catch (Exception e) {
            log.error("导出记忆文件失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 导入记忆 ZIP 压缩包，覆盖项目内的记忆文件夹
     */
    @PostMapping("/memory/import/{projectId}")
    public ApiResult importMemory(@PathVariable String projectId, @RequestParam("file") MultipartFile file) {
        try {
            WecreateProject project = wecreateProjectService.getById(new BigDecimal(projectId));
            if (project == null) {
                return ApiResult.error("项目不存在");
            }

            String projectPath = project.getLocalPath();
            if (projectPath == null || projectPath.isEmpty()) {
                return ApiResult.error("项目本地路径未配置");
            }

            if (file.isEmpty()) {
                return ApiResult.error("上传文件为空");
            }

            File memoryDir = new File(projectPath, ".memory");

            // 先删除旧的记忆文件夹
            if (memoryDir.exists()) {
                deleteDirectory(memoryDir);
            }

            // 创建新的记忆文件夹
            memoryDir.mkdirs();

            // 解压 ZIP 文件到记忆文件夹
            try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
                ZipEntry entry;
                byte[] buffer = new byte[4096];
                while ((entry = zis.getNextEntry()) != null) {
                    File newFile = new File(memoryDir, entry.getName());

                    // 安全检查：防止路径遍历攻击
                    if (!newFile.getCanonicalPath().startsWith(memoryDir.getCanonicalPath())) {
                        log.warn("检测到非法路径: {}", entry.getName());
                        zis.closeEntry();
                        continue;
                    }

                    if (entry.isDirectory()) {
                        newFile.mkdirs();
                    } else {
                        newFile.getParentFile().mkdirs();
                        try (FileOutputStream fos = new FileOutputStream(newFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                    zis.closeEntry();
                }
            }

            log.info("成功导入记忆文件到项目: {}, 路径: {}", project.getProjectName(), memoryDir);
            return ApiResult.ok("记忆文件导入成功");
        } catch (Exception e) {
            log.error("导入记忆文件失败", e);
            return ApiResult.error("导入失败: " + e.getMessage());
        }
    }

    /**
     * 检查项目是否有记忆文件
     */
    @GetMapping("/memory/has/{projectId}")
    public ApiResult hasMemory(@PathVariable String projectId) {
        try {
            WecreateProject project = wecreateProjectService.getById(new BigDecimal(projectId));
            if (project == null) {
                return ApiResult.ok(false);
            }

            File memoryDir = new File(project.getLocalPath(), ".memory");
            boolean exists = memoryDir.exists() && memoryDir.isDirectory() && memoryDir.list() != null && memoryDir.list().length > 0;
            return ApiResult.ok(exists);
        } catch (Exception e) {
            log.error("检查记忆文件失败", e);
            return ApiResult.ok(false);
        }
    }

    // ========== 私有方法 ==========

    /**
     * 递归压缩目录
     */
    private void zipDirectory(File dir, String parentPath, ZipOutputStream zos) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String path = parentPath + file.getName();
            if (file.isDirectory()) {
                zos.putNextEntry(new ZipEntry(path + "/"));
                zos.closeEntry();
                zipDirectory(file, path + "/", zos);
            } else {
                zos.putNextEntry(new ZipEntry(path));
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    /**
     * 递归删除目录
     */
    private boolean deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        return dir.delete();
    }
}
