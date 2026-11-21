package com.lxjn.blog.web;

public record ApiResponse<T>(int code, String message, T data) {

    /** 构造标准成功响应。 */
    public static <T> ApiResponse<T> ok(T data) {
        return of(ErrorCode.SUCCESS, ErrorCode.SUCCESS.message(), data);
    }

    /** 将错误码、消息和可选业务数据封装为统一响应。 */
    public static <T> ApiResponse<T> of(ErrorCode code, String message, T data) {
        return new ApiResponse<>(code.code(), message, data);
    }
}
