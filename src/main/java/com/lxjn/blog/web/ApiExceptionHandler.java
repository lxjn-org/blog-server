package com.lxjn.blog.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 返回业务层明确声明的错误码和公开消息。 */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(ApiException exception) {
        ErrorCode errorCode = exception.errorCode();
        return ResponseEntity.status(errorCode.status())
                .body(ApiResponse.of(errorCode, exception.getMessage(), exception.data()));
    }

    /** 屏蔽数据库连接细节，方便前端识别服务暂不可用。 */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Object>> handleDatabase(DataAccessException exception) {
        log.warn("数据库操作失败，异常类型 {}", exception.getClass().getSimpleName());
        return handleBusiness(new ApiException(ErrorCode.SERVICE_UNAVAILABLE));
    }

    /** 对未知异常记录内部日志，并返回通用错误。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception exception) {
        log.error("未处理的请求异常", exception);
        return handleBusiness(new ApiException(ErrorCode.INTERNAL_ERROR));
    }

    /** 统一处理 Spring MVC 的校验、解析及路由异常，同时保留标准 HTTP 响应头。 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorCode code = ErrorCode.fromStatus(status);
        String message = code.message();
        if (exception instanceof MethodArgumentNotValidException validation) {
            var field = validation.getBindingResult().getFieldError();
            if (field != null) message = "字段 " + field.getField() + " 校验失败";
        }
        Object response = ApiResponse.of(code, message, null);
        return super.handleExceptionInternal(exception, response, headers, status, request);
    }
}
