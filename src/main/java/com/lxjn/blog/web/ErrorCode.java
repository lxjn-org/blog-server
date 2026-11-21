package com.lxjn.blog.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public enum ErrorCode {
    SUCCESS(0, HttpStatus.OK, "ok"),
    BAD_REQUEST(40000, HttpStatus.BAD_REQUEST, "请求参数有误"),
    UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "请先登录"),
    FORBIDDEN(40300, HttpStatus.FORBIDDEN, "无权访问"),
    NOT_FOUND(40400, HttpStatus.NOT_FOUND, "资源不存在"),
    METHOD_NOT_ALLOWED(40500, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不受支持"),
    CONFLICT(40900, HttpStatus.CONFLICT, "数据冲突"),
    PAYLOAD_TOO_LARGE(41300, HttpStatus.CONTENT_TOO_LARGE, "请求体过大"),
    UNSUPPORTED_MEDIA_TYPE(41500, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求内容类型不受支持"),
    CAPTCHA_FAILED(42200, HttpStatus.UNPROCESSABLE_CONTENT, "人机验证未通过"),
    TOO_MANY_REQUESTS(42900, HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁"),
    INTERNAL_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误"),
    SERVICE_UNAVAILABLE(50000, HttpStatus.SERVICE_UNAVAILABLE, "业务数据库暂不可用"),
    DATABASE_NOT_INITIALIZED(50301, HttpStatus.SERVICE_UNAVAILABLE, "请先配置并初始化业务数据库");

    private final int code;
    private final HttpStatus status;
    private final String message;

    /** 声明业务码、对应 HTTP 状态及默认公开消息。 */
    ErrorCode(int code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    /** 获取接口约定的业务码。 */
    public int code() {
        return code;
    }

    /** 获取该错误默认使用的 HTTP 状态。 */
    public HttpStatus status() {
        return status;
    }

    /** 获取可返回给客户端的消息。 */
    public String message() {
        return message;
    }

    /** 将框架 HTTP 状态映射到统一业务码。 */
    public static ErrorCode fromStatus(HttpStatusCode status) {
        for (ErrorCode error : values()) {
            if (error.status.equals(status)) return error;
        }
        return status.is4xxClientError() ? BAD_REQUEST : INTERNAL_ERROR;
    }
}
