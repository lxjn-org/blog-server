package com.lxjn.blog.web;

public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Object data;

    /** 使用默认公开消息抛出业务异常。 */
    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.message(), null);
    }

    /** 携带明确允许返回给调用方的消息和数据。 */
    public ApiException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }

    /** 获取异常对应的业务码和 HTTP 状态。 */
    public ErrorCode errorCode() {
        return errorCode;
    }

    /** 获取异常附带的公开业务数据。 */
    public Object data() {
        return data;
    }
}
