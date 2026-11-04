package com.example.travelplatform.common.error;

/**
 * 可预期的业务异常。Service 层抛出，由 {@link GlobalExceptionHandler} 转换为统一响应。
 * 消息面向用户，不得包含 SQL、堆栈、内部路径或凭据。
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
