package com.example.travelplatform.common.api;

/**
 * 统一响应包：成功 code=0、message=success；失败使用稳定整数业务码。
 * data 即使在失败或为空时也保留字段（序列化为 null），便于前端统一处理。
 *
 * @param <T> data 类型
 */
public class ApiResponse<T> {

    /** 成功业务码。 */
    public static final int SUCCESS_CODE = 0;

    /** 成功默认消息。 */
    public static final String SUCCESS_MESSAGE = "success";

    private final int code;
    private final String message;
    private final T data;

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static <T> ApiResponse<T> failure(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
