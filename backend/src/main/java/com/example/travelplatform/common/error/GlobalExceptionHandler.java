package com.example.travelplatform.common.error;

import com.example.travelplatform.common.api.ApiResponse;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

/**
 * 全局异常处理：把所有异常收敛为 {@link ApiResponse} 包体与合适的 HTTP 状态。
 * 对外响应绝不包含堆栈、SQL、内部路径、异常类名或凭据；细节只写服务端日志。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String INVALID_BODY_MESSAGE = "请求体格式不正确，请检查字段类型与是否包含未知字段";

    /** 字段级错误项，供前端定位表单字段。 */
    public record FieldErrorItem(String field, String message) {
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotLogin(NotLoginException exception) {
        // 记录服务端类型（如 token 过期、被顶下线），对外只给统一文案。
        log.debug("未登录访问被拦截: type={}", exception.getType());
        return build(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getMessage(), null);
    }

    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(RuntimeException exception) {
        log.debug("权限校验未通过: {}", exception.getClass().getSimpleName());
        return build(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage(), null);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(ApiException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? errorCode.getMessage()
                : exception.getMessage();
        return build(errorCode, message, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        return validationFailure(exception.getBindingResult().getFieldErrors());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Object>> handleBindException(BindException exception) {
        return validationFailure(exception.getBindingResult().getFieldErrors());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException exception) {
        List<FieldErrorItem> fieldErrors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldErrorItem(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getMessage(),
                Map.of("fieldErrors", fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotReadable(HttpMessageNotReadableException exception) {
        log.debug("请求体解析失败: {}", exception.getMessage());
        return build(ErrorCode.VALIDATION_FAILED, INVALID_BODY_MESSAGE, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParameter(MissingServletRequestParameterException exception) {
        return build(ErrorCode.VALIDATION_FAILED,
                "缺少必需的请求参数: " + exception.getParameterName(), null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return build(ErrorCode.VALIDATION_FAILED, "请求参数类型不正确", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        return build(ErrorCode.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE.getMessage(), null);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingPart(MissingServletRequestPartException exception) {
        return build(ErrorCode.VALIDATION_FAILED, "缺少图片文件", null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnsupportedMethod(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(exception.getHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(ErrorCode.VALIDATION_FAILED.getCode(), "请求方法不支持", null));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotAcceptable(HttpMediaTypeNotAcceptableException exception) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(ErrorCode.VALIDATION_FAILED.getCode(), "不支持请求的响应类型", null));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicateKey(DuplicateKeyException exception) {
        log.warn("唯一约束冲突: {}", exception.getMessage());
        return build(ErrorCode.DUPLICATE_KEY, ErrorCode.DUPLICATE_KEY.getMessage(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("数据完整性冲突: {}", exception.getMessage());
        return build(ErrorCode.STATE_CONFLICT, ErrorCode.STATE_CONFLICT.getMessage(), null);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Object>> handleConcurrentWrite(PessimisticLockingFailureException exception) {
        return build(ErrorCode.STATE_CONFLICT, "数据正在更新，请稍后重试", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResource(NoResourceFoundException exception) {
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception exception) {
        // 只记录在服务端日志，响应中不暴露任何内部细节。
        log.error("未处理的服务端异常", exception);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), null);
    }

    private static ResponseEntity<ApiResponse<Object>> validationFailure(List<FieldError> fieldErrors) {
        List<FieldErrorItem> items = fieldErrors.stream()
                .map(error -> new FieldErrorItem(error.getField(), error.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getMessage(),
                Map.of("fieldErrors", items));
    }

    private static ResponseEntity<ApiResponse<Object>> build(ErrorCode errorCode, String message, Object data) {
        HttpStatus status = errorCode.getStatus();
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(errorCode.getCode(), message, data));
    }
}
