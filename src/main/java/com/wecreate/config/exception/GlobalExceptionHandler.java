package com.wecreate.config.exception;

import com.wecreate.dto.ApiResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理评分值无效异常
     * @param e 评分值无效异常
     * @return 标准的ApiResult响应
     */
    @ExceptionHandler(InvalidScoreException.class)
    public ApiResult handleInvalidScoreException(InvalidScoreException e) {
        return ApiResult.fail(e.getMessage());
    }
    
    /**
     * 处理提示记录未找到异常
     * @param e 提示记录未找到异常
     * @return 标准的ApiResult响应
     */
    @ExceptionHandler(PromptNotFoundException.class)
    public ApiResult handlePromptNotFoundException(PromptNotFoundException e) {
        return ApiResult.fail(e.getMessage());
    }
    
    /**
     * 处理其他所有异常
     * @param e 异常
     * @return 标准的ApiResult响应
     */
    @ExceptionHandler(Exception.class)
    public ApiResult handleGeneralException(Exception e) {
        return ApiResult.error("系统内部错误: " + e.getMessage());
    }
}