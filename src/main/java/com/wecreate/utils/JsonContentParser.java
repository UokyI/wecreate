package com.wecreate.utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;


/**
 * JSON内容解析工具类
 */
@Slf4j
public class JsonContentParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析Qwen输出内容，提取有效的文本信息
     *
     * @param line 输出内容，可能是字符串或JSON格式
     * @return 提取的文本内容
     */
    public static String parseContent(String line) {
        // 判断是否为JSON格式
        if (line.startsWith("{") && line.endsWith("}")) {
            try {
                JsonNode jsonNode = objectMapper.readTree(line);

                // 处理result类型，只提取token信息
                if (jsonNode.has("type") && "result".equals(jsonNode.get("type").asText())) {
                    StringBuilder tokenInfo = new StringBuilder();
                    JsonNode usageNode = jsonNode.get("usage");
                    if (usageNode != null) {
                        long inputTokens = 0L;
                        long outputTokens = 0L;
                        long cachedTokens = 0L;
                        if (usageNode.has("input_tokens")) {
                            inputTokens = usageNode.get("input_tokens").asLong();
                            tokenInfo.append("输入消耗token: ").append(usageNode.get("input_tokens").asInt()).append("\n");
                        }
                        if (usageNode.has("output_tokens")) {
                            outputTokens = usageNode.get("output_tokens").asLong();
                            tokenInfo.append("输出消耗token: ").append(usageNode.get("output_tokens").asInt()).append("\n");
                        }
                        if (usageNode.has("cache_read_input_tokens")) {
                            cachedTokens = usageNode.get("cache_read_input_tokens").asLong();
                            tokenInfo.append("缓存命中的token: ").append(usageNode.get("cache_read_input_tokens").asInt()).append("\n");
                        }
                        if (usageNode.has("total_tokens")) {
                            tokenInfo.append("总token: ").append(usageNode.get("total_tokens").asInt()).append("\n");
                        }
                        // 分项费用
//                        double inputCost = calculateInputCost(inputTokens, cachedTokens);
//                        double outputCost = calculateOutputCost(outputTokens);
//                        tokenInfo.append("输入费用: " + String.format("%.2f", inputCost) + " 元").append("\n");
//                        tokenInfo.append("输出费用: " + String.format("%.2f", outputCost) + " 元").append("\n");
//                        double totalCost = calculateTotalCost(inputTokens, outputTokens, cachedTokens);
//                        tokenInfo.append("总费用（估算）: " + String.format("%.2f", totalCost) + " 元").append("\n");
                        log.info(tokenInfo.toString());
                    }
                    return tokenInfo.toString().trim();
                }

                // 优先处理message类型
                if (jsonNode.has("message") && jsonNode.get("message").has("content")) {
                    JsonNode contentNode = jsonNode.get("message").get("content");

                    if (contentNode.isArray()) {
                        StringBuilder result = new StringBuilder();
                        for (JsonNode item : contentNode) {
                            if (item.has("type") && "text".equals(item.get("type").asText())) {
                                result.append(item.get("text").asText());
                            } else if (item.has("type") && "tool_result".equals(item.get("type").asText())) {
                                // 处理tool_result类型，提取content字段
                                if (item.has("content")) {
                                    String content = item.get("content").asText();
                                    // 移除系统提示部分
                                    int systemReminderStart = content.indexOf("<system-reminder>");
                                    if (systemReminderStart != -1) {
                                        content = content.substring(0, systemReminderStart).trim();
                                    }
                                    result.append(content);
                                }
                            } else if (item.has("type") && "tool_use".equals(item.get("type").asText())) {
                                // 处理tool_use类型，提取input中的todos信息
                                if (item.has("input") && item.get("input").has("todos")) {
                                    JsonNode todosNode = item.get("input").get("todos");
                                    if (todosNode.isArray()) {
                                        for (JsonNode todo : todosNode) {
                                            if (todo.has("content")) {
                                                result.append(todo.get("content").asText()).append("\n");
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        return result.toString().trim();
                    } else if (contentNode.isObject() && contentNode.has("type") && "text".equals(contentNode.get("type").asText())) {
                        return contentNode.get("text").asText();
                    }
                }

                // 处理system类型，返回初始化信息
                if (jsonNode.has("type") && "system".equals(jsonNode.get("type").asText())) {
                    StringBuilder systemInfo = new StringBuilder();
                    systemInfo.append("系统初始化完成\n");
                    systemInfo.append("工作目录: ").append(jsonNode.get("cwd").asText()).append("\n");
                    systemInfo.append("支持的工具: ").append(jsonNode.get("tools").toString()).append("\n");
                    systemInfo.append("模型版本: ").append(jsonNode.get("qwen_code_version").asText());
                    return systemInfo.toString();
                }
            } catch (Exception e) {
                // 如果解析失败，返回原始内容
                log.warn("JSON解析失败: {}", line);
            }
        }

        // 返回原始内容
        return line;
    }
    
    /**
     * 从JSON行中提取token信息
     * @param line JSON格式的响应行
     * @return TokenInfo对象，包含各种token的数量
     */
    public static TokenInfo extractTokenInfo(String line) {
        if (line.startsWith("{") && line.endsWith("}")) {
            try {
                JsonNode jsonNode = objectMapper.readTree(line);

                // 检查是否为result类型，包含usage信息
                if (jsonNode.has("type") && "result".equals(jsonNode.get("type").asText())) {
                    JsonNode usageNode = jsonNode.get("usage");
                    if (usageNode != null) {
                        TokenInfo tokenInfo = new TokenInfo();
                        
                        if (usageNode.has("input_tokens")) {
                            tokenInfo.setInputTokens(usageNode.get("input_tokens").asInt());
                        }
                        
                        if (usageNode.has("output_tokens")) {
                            tokenInfo.setOutputTokens(usageNode.get("output_tokens").asInt());
                        }
                        
                        if (usageNode.has("cache_read_input_tokens")) {
                            tokenInfo.setCachedTokens(usageNode.get("cache_read_input_tokens").asInt());
                        }
                        
                        if (usageNode.has("total_tokens")) {
                            tokenInfo.setTotalTokens(usageNode.get("total_tokens").asInt());
                        }
                        
                        return tokenInfo;
                    }
                }
            } catch (Exception e) {
                log.warn("提取token信息失败: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * Token信息封装类
     */
    public static class TokenInfo {
        private Integer inputTokens;
        private Integer outputTokens;
        private Integer cachedTokens;
        private Integer totalTokens;

        public Integer getInputTokens() {
            return inputTokens;
        }

        public void setInputTokens(Integer inputTokens) {
            this.inputTokens = inputTokens;
        }

        public Integer getOutputTokens() {
            return outputTokens;
        }

        public void setOutputTokens(Integer outputTokens) {
            this.outputTokens = outputTokens;
        }

        public Integer getCachedTokens() {
            return cachedTokens;
        }

        public void setCachedTokens(Integer cachedTokens) {
            this.cachedTokens = cachedTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
        }

        public boolean hasAnyToken() {
            return inputTokens != null || outputTokens != null || cachedTokens != null || totalTokens != null;
        }
    }
}