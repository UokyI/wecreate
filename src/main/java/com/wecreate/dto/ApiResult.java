package com.wecreate.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ApiResult implements Serializable {

    private int code;
    private String msg;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    private Object data;

    public static ApiResult ok() {
        ApiResult apiResult = new ApiResult();
        apiResult.setCode(ApiStatus.OK.getCode());
        apiResult.setMsg(ApiStatus.OK.getMsg());
        apiResult.setData(null);
        apiResult.setTimestamp(LocalDateTime.now());
        return apiResult;
    }

    public static ApiResult ok(String msg) {
        ApiResult apiResult = new ApiResult();
        apiResult.setCode(ApiStatus.OK.getCode());
        apiResult.setMsg(msg);
        apiResult.setData(null);
        apiResult.setTimestamp(LocalDateTime.now());
        return apiResult;
    }

    public static <T> ApiResult ok(T data) {
        ApiResult apiResult = new ApiResult();
        apiResult.setCode(ApiStatus.OK.getCode());
        apiResult.setMsg(ApiStatus.OK.getMsg());
        apiResult.setData(data);
        apiResult.setTimestamp(LocalDateTime.now());
        return apiResult;
    }

    public static <T> ApiResult ok(String msg, T data) {
        ApiResult apiResult = new ApiResult();
        apiResult.setCode(ApiStatus.OK.getCode());
        apiResult.setMsg(msg);
        apiResult.setData(data);
        apiResult.setTimestamp(LocalDateTime.now());
        return apiResult;
    }

    public static ApiResult fail(){
        ApiResult apiResult = new ApiResult();
        apiResult.setCode(ApiStatus.FAIL.getCode());
        apiResult.setMsg(ApiStatus.FAIL.getMsg());
        apiResult.setData(null);
        apiResult.setTimestamp(LocalDateTime.now());
        return apiResult;
    }

    public static ApiResult fail(String failMsg) {
        ApiResult apiResult = new ApiResult();
        apiResult.setCode(ApiStatus.FAIL.getCode());
        apiResult.setMsg(failMsg);
        apiResult.setData(null);
        apiResult.setTimestamp(LocalDateTime.now());
        return apiResult;
    }

    public static <T> ApiResult unauthorized(String failMsg, T data) {
        ApiResult apiResult = new ApiResult();
        apiResult.setCode(ApiStatus.UNAUTHORIZED.getCode());
        apiResult.setMsg(failMsg);
        apiResult.setData(data);
        apiResult.setTimestamp(LocalDateTime.now());
        return apiResult;
    }

    public static <T> ApiResult fail(String failMsg, T data) {
        ApiResult apiResult = new ApiResult();
        apiResult.setCode(ApiStatus.FAIL.getCode());
        apiResult.setMsg(failMsg);
        apiResult.setData(data);
        apiResult.setTimestamp(LocalDateTime.now());
        return apiResult;
    }

    public static ApiResult error(){
        ApiResult apiResult = new ApiResult();
        apiResult.setCode(ApiStatus.ERROR.getCode());
        apiResult.setMsg(ApiStatus.ERROR.getMsg());
        apiResult.setData(null);
        apiResult.setTimestamp(LocalDateTime.now());
        return apiResult;
    }

    public static ApiResult error(String exMsg) {
        ApiResult apiResult = new ApiResult();
        apiResult.setCode(ApiStatus.ERROR.getCode());
        apiResult.setMsg(exMsg);
        apiResult.setData(null);
        apiResult.setTimestamp(LocalDateTime.now());
        return apiResult;
    }

    public static <T> ApiResult error(String exMsg, T data) {
        ApiResult apiResult = new ApiResult();
        apiResult.setCode(ApiStatus.ERROR.getCode());
        apiResult.setMsg(exMsg);
        apiResult.setData(data);
        apiResult.setTimestamp(LocalDateTime.now());
        return apiResult;
    }

    public static ApiResult instance(ApiStatus apiStatus) {
        ApiResult apiResult = new ApiResult();
        apiResult.setCode(apiStatus.getCode());
        apiResult.setMsg(apiStatus.getMsg());
        apiResult.setData(null);
        apiResult.setTimestamp(LocalDateTime.now());
        return apiResult;
    }

    public boolean isFail() {
        return this.code != ApiStatus.OK.getCode();
    }

    public boolean isSuccess() {
        return this.code == ApiStatus.OK.getCode();
    }
}
