package com.wecreate.config.exception;

/**
 * 评分值无效异常类
 * 当评分值不在有效范围(0-10)内时抛出
 */
public class InvalidScoreException extends RuntimeException {
    
    public InvalidScoreException() {
        super("评分值必须在0-10范围内");
    }
    
    public InvalidScoreException(String message) {
        super(message);
    }
}