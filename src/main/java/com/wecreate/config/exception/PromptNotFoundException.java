package com.wecreate.config.exception;

/**
 * 提示记录未找到异常类
 * 当根据traceId找不到对应的提示记录时抛出
 */
public class PromptNotFoundException extends RuntimeException {
    
    public PromptNotFoundException() {
        super("未找到对应的提示词记录");
    }
    
    public PromptNotFoundException(String message) {
        super(message);
    }
}