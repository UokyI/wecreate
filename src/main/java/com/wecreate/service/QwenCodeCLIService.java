package com.wecreate.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wecreate.dto.BatchRequestItem;
import com.wecreate.dto.BatchResultItem;
import com.wecreate.entity.WecreateLog;
import com.wecreate.entity.WecreatePrompt;
import com.wecreate.mapper.WecreateLogMapper;
import com.wecreate.mapper.WecreatePromptMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.wecreate.utils.JsonContentParser.parseContent;

@Slf4j
@Service
public class QwenCodeCLIService {

    @Autowired
    private SystemConfigService systemConfigService;

    // 注入全局线程池
    @Autowired
    @Qualifier("globalTaskExecutor")
    private Executor executorService;

    // 注入QwenService
    @Autowired
    private QwenService qwenService;

    @Autowired
    private WecreateLogMapper wecreateLogMapper;

    @Autowired
    private WecreatePromptMapper wecreatePromptMapper;

    @Autowired
    private com.wecreate.mapper.WecreateProjectMapper wecreateProjectMapper;

    @Autowired
    private MemoryService memoryService;

    // 增加信号量许可数以匹配更大的线程池
    private final Semaphore semaphore = new Semaphore(20);

    private final Semaphore syncSemaphore = new Semaphore(5);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private void processRequest(String request, SseEmitter emitter, String path, String userId, String projectId, String model) throws IOException {
        sendSseEvent(emitter, "message", "开始处理请求", userId);
        sendSseEvent(emitter, "message", "工作目录: " + path, userId);

        try {
            // 确保工作目录存在
            File workDir = new File(path);
            if (!workDir.exists()) {
                workDir.mkdirs();
                sendSseEvent(emitter, "message", "创建工作目录: " + path, userId);
            }

            // 尝试解析JSON请求体
            String userRequest = request;
            try {
                JsonNode jsonNode = objectMapper.readTree(request);
                if (jsonNode.has("request")) {
                    userRequest = jsonNode.get("request").asText();
                }
            } catch (Exception e) {
                // 如果不是JSON格式，就直接使用原始请求
                log.info("请求不是JSON格式，使用原始请求内容");
                userRequest = request;
            }

            sendSseEvent(emitter, "message", "解析后的请求: " + userRequest, userId);

            // 调用Qwen Code CLI并流式处理响应
            callQwenCodeCLIWithStreaming(userRequest, emitter, path, userId, projectId, model);
        } catch (Exception e) {
            sendSseEvent(emitter, "error", "处理出错: " + e.getMessage(), userId);
            e.printStackTrace();
        }
    }

    // 新增同步处理方法用于普通HTTP POST接口
    public boolean processRequestSync(String request, List<String> responseMessages, String path, String userId, String projectId) {
        responseMessages.add("开始处理请求");
        responseMessages.add("工作目录: " + path);

        try {
            // 确保工作目录存在
            File workDir = new File(path);
            if (!workDir.exists()) {
                workDir.mkdirs();
                responseMessages.add("创建工作目录: " + path);
            }

            // 尝试解析JSON请求体
            String userRequest = request;
            try {
                JsonNode jsonNode = objectMapper.readTree(request);
                if (jsonNode.has("request")) {
                    userRequest = jsonNode.get("request").asText();
                }
            } catch (Exception e) {
                // 如果不是JSON格式，就直接使用原始请求
                log.info("请求不是JSON格式，使用原始请求内容");
                userRequest = request;
            }

            responseMessages.add("解析后的请求: " + userRequest);

            String currentTraceId = MDC.get("traceId");
            String currentRIP = MDC.get("rip");
            Consumer<String[]> onAccept = (data) -> {
                try {
                    MDC.put("traceId", currentTraceId);
                    MDC.put("rip", currentRIP);
                    sendSseEvent(null, data[0], data[1], userId);
                } finally {
                    MDC.remove("traceId");
                    MDC.remove("rip");
                }
            };
            // 调用Qwen Code CLI并收集响应
            qwenService.callQwenCodeCLISync(userRequest, responseMessages, path, onAccept, userId, projectId);
            responseMessages.add("指令执行完成!");
            return true;
        } catch (Exception e) {
            responseMessages.add("处理出错: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean processRequestSync(String project, String request, List<String> responseMessages, String ip, String userId) {
        boolean acquired = false;
        try {
            // 获取信号量许可，限制并发数量
            acquired = syncSemaphore.tryAcquire(30, TimeUnit.SECONDS);
            if (!acquired) {
                responseMessages.add("系统繁忙，请稍后再试");
                return false;
            }
            String path = "d:\\workspace\\custom\\" + project;
            com.wecreate.utils.FileUtils.ensurePathExists(path);
            return processRequestSync(request, responseMessages, path, userId, "System");
        } catch (InterruptedException e) {
            log.error("获取执行许可时被中断", e);
            Thread.currentThread().interrupt();
            responseMessages.add("系统错误: 请求被中断");
            return false;
        } finally {
            // 确保信号量被释放
            if (acquired) {
                syncSemaphore.release();
            }
        }
    }

    @Async
    public void processProjectRequestASync(String project, String userId, String request, String currentTraceId, String ip, String projectId) {
        String path = "d:\\workspace\\custom\\" + project;
        com.wecreate.utils.FileUtils.ensurePathExists(path);

        AtomicBoolean emitterCompleted = new AtomicBoolean(false);

        // 发送初始连接确认消息
        try {
            MDC.put("traceId", currentTraceId);
            MDC.put("rip", ip);
            sendSseEvent(null, "connected", "已连接到服务器", userId);
        } catch (Exception e) {
            log.error("发送连接确认消息失败", e);
        } finally {
            MDC.remove("traceId");
            MDC.remove("rip");
        }

        // 使用全局线程池执行任务以避免无限制创建线程
        executorService.execute(() -> {
            boolean acquired = false;
            try {
                MDC.put("traceId", currentTraceId);
                MDC.put("rip", ip);
                // 增加信号量许可数以匹配更大的线程池
                acquired = semaphore.tryAcquire(30, TimeUnit.SECONDS);
                if (!acquired) {
                    log.warn("获取执行许可超时");
                    if (!emitterCompleted.get()) {
                        try {
                            sendSseEvent(null, "error", "系统繁忙，请稍后再试", userId);
                        } catch (Exception sendException) {
                            log.error("发送错误消息失败", sendException);
                        }
                    }
                    return;
                }

                try {
                    processRequest(request != null ? request : "", null, path, userId, projectId, "qwen3.6-plus");
                } catch (Exception e) {
                    log.error("处理请求时发生错误: {}", e.getMessage(), e);
                    if (!emitterCompleted.get()) {
                        try {
                            sendSseEvent(null, "error", "系统错误: " + e.getMessage(), userId);
                        } catch (Exception sendException) {
                            log.error("发送错误消息失败", sendException);
                        }
                    }
                }
            } catch (InterruptedException e) {
                log.error("获取执行许可时被中断", e);
                Thread.currentThread().interrupt();
            } finally {
                try {
                    // 确保信号量被释放
                    if (acquired) {
                        semaphore.release();
                    }
                } catch (Exception completeException) {
                    log.error("完成SseEmitter失败", completeException);
                } finally {
                    MDC.remove("traceId");
                    MDC.remove("rip");
                }
            }
        });
    }

    /**
     * 根据 projectId 和指定工作路径执行异步请求（批量派送使用）
     */
    @Async
    public void processProjectRequestASyncWithProjectId(String projectId, String workPath, String userId, String request, String currentTraceId, String ip) {
        com.wecreate.utils.FileUtils.ensurePathExists(workPath);

        AtomicBoolean emitterCompleted = new AtomicBoolean(false);

        // 记忆功能：任务执行前读取项目记忆
        String memoryContext = null;
        try {
            memoryContext = memoryService.readMemoriesBeforeTask(workPath);
            if (memoryContext != null && !memoryContext.isEmpty()) {
                log.info("异步任务读取到项目记忆，长度: {} 字符", memoryContext.length());
                request = prependMemoryToRequest(request, memoryContext);
            }
        } catch (Exception e) {
            log.warn("异步任务读取项目记忆失败，继续执行: {}", e.getMessage());
        }

        // 创建 final 副本供 lambda 使用
        final String finalRequest = request != null ? request : "";
        final String finalWorkPath = workPath;

        // 发送初始连接确认消息
        try {
            MDC.put("traceId", currentTraceId);
            MDC.put("rip", ip);
            sendSseEvent(null, "connected", "批量任务 - 已连接到服务器", userId);
        } catch (Exception e) {
            log.error("发送连接确认消息失败", e);
        } finally {
            MDC.remove("traceId");
            MDC.remove("rip");
        }

        // 使用全局线程池执行任务以避免无限制创建线程
        executorService.execute(() -> {
            boolean acquired = false;
            try {
                MDC.put("traceId", currentTraceId);
                MDC.put("rip", ip);
                acquired = semaphore.tryAcquire(30, TimeUnit.SECONDS);
                if (!acquired) {
                    log.warn("获取执行许可超时");
                    if (!emitterCompleted.get()) {
                        try {
                            sendSseEvent(null, "error", "系统繁忙，请稍后再试", userId);
                        } catch (Exception sendException) {
                            log.error("发送错误消息失败", sendException);
                        }
                    }
                    return;
                }

                try {
                    processRequest(finalRequest, null, finalWorkPath, userId, projectId, "qwen3.6-plus");
                } catch (Exception e) {
                    log.error("处理请求时发生错误: {}", e.getMessage(), e);
                    if (!emitterCompleted.get()) {
                        try {
                            sendSseEvent(null, "error", "系统错误: " + e.getMessage(), userId);
                        } catch (Exception sendException) {
                            log.error("发送错误消息失败", sendException);
                        }
                    }
                } finally {
                    // 记忆功能：任务执行后整理记忆
                    try {
                        memoryService.consolidateMemoryAfterTask(finalWorkPath, projectId, currentTraceId);
                    } catch (Exception e) {
                        log.warn("异步任务整理项目记忆失败: {}", e.getMessage());
                    }
                }
            } catch (InterruptedException e) {
                log.error("获取执行许可时被中断", e);
                Thread.currentThread().interrupt();
            } finally {
                try {
                    if (acquired) {
                        semaphore.release();
                    }
                } catch (Exception completeException) {
                    log.error("完成SseEmitter失败", completeException);
                } finally {
                    MDC.remove("traceId");
                    MDC.remove("rip");
                }
            }
        });
    }

    @Async
    public void processBatchSequentialRequest(List<BatchRequestItem> batchRequests, String userId, String batchTraceId, String rip, String projectId) {
        // 根据 projectId 查询项目，获取本地路径（批量任务都在同一个项目路径下顺序执行）
        com.wecreate.entity.WecreateProject project = wecreateProjectMapper.selectById(new java.math.BigDecimal(projectId));
        String workPath = (project != null && project.getLocalPath() != null) ? project.getLocalPath() : ("d:\\workspace\\custom\\" + projectId);
        com.wecreate.utils.FileUtils.ensurePathExists(workPath);
        log.info("批量任务工作目录: {}", workPath);

        // 创建异步任务处理批量请求
        CompletableFuture.runAsync(() -> {
            List<BatchResultItem> results = new ArrayList<>();
            for (int i = 0; i < batchRequests.size(); i++) {
                BatchRequestItem item = batchRequests.get(i);
                String itemTraceId = batchTraceId + "-item-" + (i + 1);  // 为每个项目生成唯一追踪ID
                try {
                    log.info("开始处理批次 {} 中的第 {} 个任务: {}", batchTraceId, i + 1, item.getRequest());
                    // 顺序执行每个异步请求，使用当前页面的 projectId 和对应的工作路径
                    processProjectRequestASyncWithProjectId(projectId, workPath, userId, item.getRequest(), itemTraceId, rip);
                    // 等待当前任务完成（如果需要确认完成状态）
                    // 等待当前任务完成（等待最多30分钟）
                    boolean isCompleted = waitForTaskCompletion(itemTraceId, 30 * 60 * 1000); // 最多等待30分钟
                    if (isCompleted) {
                        results.add(new BatchResultItem(item.getProject(), "success",
                                "任务 " + (i + 1) + " 已提交处理，追踪ID: " + itemTraceId));
                    }
                } catch (Exception e) {
                    log.error("处理第 {} 个任务时发生错误: {}", i + 1, e.getMessage());
                    results.add(new BatchResultItem(item.getProject(), "error", e.getMessage()));
                }
            }
            log.info("批次 {} 所有任务处理完成", batchTraceId);
        });
    }

    // 添加等待任务完成的方法
// 添加等待任务完成的方法
    private boolean waitForTaskCompletion(String traceId, long timeoutMs) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            // 检查任务是否完成 - 查询是否有EXECUTION_TIME字段不为空的记录
            long completedCount = wecreatePromptMapper.selectCount(
                    new QueryWrapper<WecreatePrompt>()
                            .eq("trace_id", traceId)
                            .isNotNull("EXECUTION_TIME")
            );

            if (completedCount > 0) {
                log.info("任务 {} 已完成", traceId);
                return true; // 任务完成，返回true
            }

            try {
                Thread.sleep(5 * 60 * 1000); // 每5分钟检查一次
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("等待任务完成时被中断，任务ID: {}", traceId);
                return false; // 被中断，返回false
            }
        }

        log.warn("任务 {} 在 {} 毫秒内未完成", traceId, timeoutMs);
        return false; // 超时未完成，返回false
    }


    // 处理 /api/cli/{project}/execute 接口逻辑的方法
    public SseEmitter processProjectRequest(String project, String request, String ip) {
        // project 参数已经是完整的项目本地路径（例如 D:\workspace\wecreate）
        String path = project;
        com.wecreate.utils.FileUtils.ensurePathExists(path);
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        AtomicBoolean emitterCompleted = new AtomicBoolean(false);

        // 发送初始连接确认消息
        try {
            sendSseEvent(emitter, "connected", "已连接到服务器");
        } catch (Exception e) {
            log.error("发送连接确认消息失败", e);
        }
        String currentTraceId = MDC.get("traceId");
        String currentRip = MDC.get("rip");
        // 使用全局线程池执行任务以避免无限制创建线程
        executorService.execute(() -> {
            boolean acquired = false;
            try {
                MDC.put("traceId", currentTraceId);
                MDC.put("rip", currentRip);
                // 增加信号量许可数以匹配更大的线程池
                acquired = semaphore.tryAcquire(30, TimeUnit.SECONDS);
                if (!acquired) {
                    log.warn("获取执行许可超时");
                    if (!emitterCompleted.get()) {
                        try {
                            sendSseEvent(emitter, "error", "系统繁忙，请稍后再试");
                        } catch (Exception sendException) {
                            log.error("发送错误消息失败", sendException);
                        }
                    }
                    return;
                }

                try {
                    log.info("开始处理请求: processRequest ");
                    processRequest(request != null ? request : "", emitter, path, "System", "System", "qwen3.6-plus");
                } catch (Exception e) {
                    log.error("处理请求时发生错误: {}", e.getMessage(), e);
                    if (!emitterCompleted.get()) {
                        try {
                            sendSseEvent(emitter, "error", "系统错误: " + e.getMessage());
                        } catch (Exception sendException) {
                            log.error("发送错误消息失败", sendException);
                        }
                    }
                }
            } catch (InterruptedException e) {
                log.error("获取执行许可时被中断", e);
                Thread.currentThread().interrupt();
            } finally {
                try {
                    // 确保信号量被释放
                    if (acquired) {
                        semaphore.release();
                    }

                    // 确保emitter被完成
                    if (!emitterCompleted.get()) {
                        emitter.complete();
                        emitterCompleted.set(true);
                    }
                } catch (Exception completeException) {
                    log.error("完成SseEmitter失败", completeException);
                } finally {
                    MDC.remove("traceId");
                    MDC.remove("rip");
                }
            }
        });

        // 监听emitter的完成事件
        emitter.onCompletion(() -> {
            emitterCompleted.set(true);
        });

        emitter.onError((e) -> {
            log.error("SseEmitter发生错误", e);
            emitterCompleted.set(true);
        });

        return emitter;
    }

    /**
     * 根据项目ID处理执行请求
     *
     * @param projectId 项目ID
     * @param request   请求内容
     * @param ip        请求IP
     * @return SSE Emitter
     */
    public SseEmitter processProjectRequestByProjectId(String projectId, String request, String ip, String model) {
        // 根据项目ID查询项目，获取本地路径
        com.wecreate.entity.WecreateProject project = wecreateProjectMapper.selectById(new java.math.BigDecimal(projectId));
        if (project == null) {
            SseEmitter emitter = new SseEmitter(0L);
            try {
                sendSseEvent(emitter, "error", "项目不存在，项目ID: " + projectId);
            } catch (Exception e) {
                log.error("发送错误消息失败", e);
            }
            emitter.complete();
            return emitter;
        }

        String path = project.getLocalPath();
        if (path == null || path.isEmpty()) {
            SseEmitter emitter = new SseEmitter(0L);
            try {
                sendSseEvent(emitter, "error", "项目本地路径未配置，项目ID: " + projectId);
            } catch (Exception e) {
                log.error("发送错误消息失败", e);
            }
            emitter.complete();
            return emitter;
        }

        // 记忆功能：任务执行前读取项目记忆
        String memoryContext = null;
        try {
            memoryContext = memoryService.readMemoriesBeforeTask(path);
            if (memoryContext != null && !memoryContext.isEmpty()) {
                log.info("读取到项目记忆，长度: {} 字符", memoryContext.length());
                // 将记忆内容拼接到请求前面
                request = prependMemoryToRequest(request, memoryContext);
            }
        } catch (Exception e) {
            log.warn("读取项目记忆失败，继续执行任务: {}", e.getMessage());
        }

        com.wecreate.utils.FileUtils.ensurePathExists(path);
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        AtomicBoolean emitterCompleted = new AtomicBoolean(false);
        String currentTraceId = MDC.get("traceId");

        // 创建 final 副本供 lambda 使用
        final String finalRequest = request != null ? request : "";
        final String finalPath = path;

        // 发送初始连接确认消息
        try {
            sendSseEvent(emitter, "connected", "已连接到服务器，项目名称: " + project.getProjectName() + "，使用模型: " + model);
        } catch (Exception e) {
            log.error("发送连接确认消息失败", e);
        }
        String currentRip = MDC.get("rip");
        // 使用全局线程池执行任务以避免无限制创建线程
        executorService.execute(() -> {
            boolean acquired = false;
            try {
                MDC.put("traceId", currentTraceId);
                MDC.put("rip", currentRip);
                // 增加信号量许可数以匹配更大的线程池
                acquired = semaphore.tryAcquire(30, TimeUnit.SECONDS);
                if (!acquired) {
                    log.warn("获取执行许可超时");
                    if (!emitterCompleted.get()) {
                        try {
                            sendSseEvent(emitter, "error", "系统繁忙，请稍后再试");
                        } catch (Exception sendException) {
                            log.error("发送错误消息失败", sendException);
                        }
                    }
                    return;
                }

                try {
                    log.info("开始处理项目执行请求，项目ID: {}, 项目名称: {}, 路径: {}, 模型: {}", projectId, project.getProjectName(), finalPath, model);
                    processRequest(finalRequest, emitter, finalPath, "System", projectId, model);
                } catch (Exception e) {
                    log.error("处理项目执行请求时发生错误: {}", e.getMessage(), e);
                    if (!emitterCompleted.get()) {
                        try {
                            sendSseEvent(emitter, "error", "系统错误: " + e.getMessage());
                        } catch (Exception sendException) {
                            log.error("发送错误消息失败", sendException);
                        }
                    }
                } finally {
                    // 记忆功能：任务执行后整理记忆（异步执行，不阻塞响应）
                    try {
                        String traceId = MDC.get("traceId");
                        memoryService.consolidateMemoryAfterTask(finalPath, projectId, traceId);
                    } catch (Exception e) {
                        log.warn("整理项目记忆失败: {}", e.getMessage());
                    }
                }
            } catch (InterruptedException e) {
                log.error("获取执行许可时被中断", e);
                Thread.currentThread().interrupt();
            } finally {
                try {
                    // 确保信号量被释放
                    if (acquired) {
                        semaphore.release();
                    }

                    // 确保emitter被完成
                    if (!emitterCompleted.get()) {
                        emitter.complete();
                        emitterCompleted.set(true);
                    }
                } catch (Exception completeException) {
                    log.error("完成SseEmitter失败", completeException);
                } finally {
                    MDC.remove("traceId");
                    MDC.remove("rip");
                }
            }
        });

        // 监听emitter的完成事件
        emitter.onCompletion(() -> {
            emitterCompleted.set(true);
        });

        emitter.onError((e) -> {
            log.error("SseEmitter发生错误", e);
            emitterCompleted.set(true);
        });

        return emitter;
    }

    /**
     * 将记忆内容拼接到请求前面
     * 记忆内容作为系统级上下文，AI 会参考这些记忆来更好地理解项目和任务
     */
    private String prependMemoryToRequest(String request, String memoryContext) {
        String memoryPrefix = "【项目历史记忆参考】\n\n" + memoryContext + "\n\n--- 以上为项目历史记忆，以下为当前任务请求 ---\n\n";

        // 尝试解析JSON请求
        try {
            JsonNode jsonNode = objectMapper.readTree(request);
            if (jsonNode.has("request")) {
                String originalRequest = jsonNode.get("request").asText();
                // 创建新的JSON，将记忆内容拼接到request字段前面
                com.fasterxml.jackson.databind.node.ObjectNode newNode = objectMapper.createObjectNode();
                ((com.fasterxml.jackson.databind.node.ObjectNode) jsonNode).fields().forEachRemaining(entry -> {
                    if (!"request".equals(entry.getKey())) {
                        newNode.set(entry.getKey(), entry.getValue());
                    }
                });
                newNode.put("request", memoryPrefix + originalRequest);
                return objectMapper.writeValueAsString(newNode);
            }
        } catch (Exception e) {
            // 不是JSON格式，直接拼接
            return memoryPrefix + request;
        }
        return request;
    }

    private void callQwenCodeCLIWithStreaming(String userRequest, SseEmitter emitter, String path, String userId, String projectId, String model) {
        // 用于监控最后输出时间的变量
        final long[] lastOutputTime = {System.currentTimeMillis()};
        final boolean[] processFinished = {false};
        final AtomicBoolean outputDetected = new AtomicBoolean(false);

        // 在 execute 方法前获取当前 traceId
        String currentTraceId = MDC.get("traceId");
        // 在 execute 方法前获取当前 traceId
        String currentRIp = MDC.get("rip");

        Consumer<String> onMessage = (line) -> {
            try {
                MDC.put("traceId", currentTraceId);
                MDC.put("rip", currentRIp);
                lastOutputTime[0] = System.currentTimeMillis(); // 更新最后输出时间
                outputDetected.set(true);
                log.info("qwen 输出 : {}", line);
                String newParseContent = parseContent(line);
                sendSseEvent(emitter, "message", newParseContent, userId);
            } finally {
                MDC.remove("traceId");
                MDC.remove("rip");
            }
        };
        Consumer<String> onBeaseInfo = (line) -> {
            try {
                MDC.put("traceId", currentTraceId);
                MDC.put("rip", currentRIp);
                sendSseEvent(emitter, "message", line, userId);
            } finally {
                MDC.remove("traceId");
                MDC.remove("rip");
            }
        };

        Consumer<String> onWarning = (line) -> {
            try {
                MDC.put("traceId", currentTraceId);
                MDC.put("rip", currentRIp);
                lastOutputTime[0] = System.currentTimeMillis(); // 更新最后输出时间
                outputDetected.set(true);
                sendSseEvent(emitter, "warning", line, userId);
            } finally {
                MDC.remove("traceId");
                MDC.remove("rip");
            }
        };

        Consumer<String> onSystem = (line) -> {
            try {
                MDC.put("traceId", currentTraceId);
                MDC.put("rip", currentRIp);
                sendSseEvent(emitter, "system", line, userId);
            } finally {
                MDC.remove("traceId");
                MDC.remove("rip");
            }
        };

        Consumer<String> onError = (line) -> {
            try {
                MDC.put("traceId", currentTraceId);
                MDC.put("rip", currentRIp);
                log.info("onError {}", currentTraceId);
                lastOutputTime[0] = System.currentTimeMillis(); // 更新最后输出时间
                outputDetected.set(true);
                sendSseEvent(emitter, "error", line, userId);
            } finally {
                MDC.remove("traceId");
                MDC.remove("rip");
            }
        };

        // 监控线程：检查是否超过120秒没有输出
        Thread monitorThread = new Thread(() -> {
            try {
                MDC.put("traceId", currentTraceId);
                MDC.put("rip", currentRIp);
                // 等待首次输出
                long startTime = System.currentTimeMillis();
                while (!outputDetected.get() && !processFinished[0]) {
                    try {
                        Thread.sleep(1000);
                        // 如果2分钟内没有任何输出，则认为cli进程在后端执行
                        if (System.currentTimeMillis() - startTime > 120000) { // 2分钟
                            log.warn("进程在2分钟内未产生任何输出");
                            sendSseEvent(emitter, "warning", "Qwen CLI进程后端执行中，请稍等...", userId);
                            sendSseEvent(emitter, "fresh", "", userId);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.info("间隔-监控线程被正常中断");
                        // 线程被中断时退出循环
                        break;
                    }
                }

                // 正常监控输出间隔
                while (!processFinished[0]) {
                    try {
                        Thread.sleep(5000); // 每5秒检查一次
                        long timeSinceLastOutput = System.currentTimeMillis() - lastOutputTime[0];

                        // 如果超过120秒没有输出，发送提醒但不终止进程
                        if (timeSinceLastOutput > 120000) { // 120秒
                            log.warn("进程超过120秒没有输出，可能处于长时间处理中");
                            sendSseEvent(emitter, "warning", "Qwen处理中，已超过120秒无新输出，请耐心等待", userId);
                        }

                        // 检查总执行时间是否超过30分钟
                        if (System.currentTimeMillis() - startTime > 1800000) { // 30分钟
                            log.warn("进程执行时间超过20分钟，强制终止 destroyProcessTree ");
                            sendSseEvent(emitter, "error", "Qwen命令执行超时(30分钟)，正在终止进程", userId);
                            // 注意：这里我们不能直接终止进程，因为进程在QwenService中运行
                            processFinished[0] = true;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.info("监控线程被正常中断");
                        // 线程被中断时退出循环
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("监控线程发生错误", e);
            } finally {
                MDC.remove("traceId");
                MDC.remove("rip");
            }
        });
        monitorThread.start();

        try {
            MDC.put("traceId", currentTraceId);
            MDC.put("rip", currentRIp);
            // 调用QwenService执行实际的CLI命令
            qwenService.callQwenCodeCLIWithStreaming(userRequest, path, onMessage, onWarning, onBeaseInfo, onSystem, onError, userId, projectId, model);
            processFinished[0] = true; // 标记进程已完成
        } catch (Exception e) {
            MDC.put("traceId", currentTraceId);
            MDC.put("rip", currentRIp);
            log.error(" {} Qwen CLI执行错误", currentTraceId, e);
            sendSseEvent(emitter, "error", "处理出错: " + e.getMessage(), userId);
        } finally {
            // 确保监控线程被中断
            monitorThread.interrupt();
            try {
                // 等待监控线程结束
                monitorThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("等待监控线程结束时被中断", e);
            }
            MDC.remove("traceId");
            MDC.remove("rip");
        }
    }

    /**
     * 安全地发送SSE事件，避免在emitter已完成时发送消息
     */
    private void sendSseEvent(SseEmitter emitter, String eventName, String data, String userId) {
        String traceID = MDC.get("traceId");
        try {
            if (null != emitter) {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            }
        } catch (IllegalStateException e) {
            log.warn("无法发送SSE事件 {}，因为emitter已完成: {}", eventName, e.getMessage());
        } catch (IOException e) {
            log.error("发送SSE事件 {} 失败: {}", eventName, e.getMessage());
        } finally {
            if (null != data && !data.isEmpty() && !"\n".equals(data) && !"warning".equals(eventName)) {
                log.info("traceId {}", traceID);
                WecreateLog log = new WecreateLog();
                log.setTraceId(traceID);
                log.setType(eventName);
                log.setContent(data);
                log.setCreateUser(userId != null ? userId : "System");
                log.setLmUser(userId != null ? userId : "System");
                wecreateLogMapper.insert(log);
            }
        }
    }

    /**
     * 安全地发送SSE事件，避免在emitter已完成时发送消息
     */
    private void sendSseEvent(SseEmitter emitter, String eventName, String data) {
        sendSseEvent(emitter, eventName, data, "System");
    }
}