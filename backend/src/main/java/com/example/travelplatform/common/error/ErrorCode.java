package com.example.travelplatform.common.error;

import org.springframework.http.HttpStatus;

/**
 * 稳定业务错误码。整数 code 与 HTTP 状态分离：HTTP 状态表达协议语义，
 * code 表达业务语义，前端按 code 分支处理。
 */
public enum ErrorCode {

    /** 参数校验失败。 */
    VALIDATION_FAILED(40001, HttpStatus.BAD_REQUEST, "请求参数校验失败"),
    /** 未登录或登录态失效。 */
    UNAUTHORIZED(40101, HttpStatus.UNAUTHORIZED, "未登录或登录状态已失效"),
    /** 用户名或密码错误。 */
    BAD_CREDENTIALS(40102, HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    /** 已登录但无权访问。 */
    FORBIDDEN(40301, HttpStatus.FORBIDDEN, "无权访问该资源"),
    /** 资源不存在。 */
    NOT_FOUND(40401, HttpStatus.NOT_FOUND, "请求的资源不存在"),
    /** 唯一约束冲突（重复用户名、重复点赞等）。 */
    DUPLICATE_KEY(40901, HttpStatus.CONFLICT, "数据已存在，请勿重复提交"),
    /** 状态冲突（当前状态不允许该操作）。 */
    STATE_CONFLICT(40902, HttpStatus.CONFLICT, "当前状态不允许该操作"),
    /** 上传内容过大。 */
    PAYLOAD_TOO_LARGE(41301, HttpStatus.PAYLOAD_TOO_LARGE, "上传内容超出大小限制"),
    /** 不支持的媒体类型。 */
    UNSUPPORTED_MEDIA_TYPE(41501, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的请求内容类型"),
    COMMENT_RATE_LIMIT(42901, HttpStatus.TOO_MANY_REQUESTS, "评论过于频繁，请稍后再试"),
    AI_UNAVAILABLE(50310, HttpStatus.SERVICE_UNAVAILABLE, "AI服务暂未配置或启用，请使用手动行程"),
    AI_DATA_INSUFFICIENT(42210, HttpStatus.UNPROCESSABLE_ENTITY, "可用城市或景点资料不足，请调整选择"),
    AI_RATE_LIMIT(42910, HttpStatus.TOO_MANY_REQUESTS, "生成请求过于频繁或仍在进行，请稍后再试"),
    AI_UPSTREAM_RATE_LIMIT(42911, HttpStatus.TOO_MANY_REQUESTS, "AI服务当前繁忙，请稍后重试"),
    AI_TIMEOUT(50410, HttpStatus.GATEWAY_TIMEOUT, "AI请求超时，已保留当前编辑内容"),
    AI_UPSTREAM_FAILED(50210, HttpStatus.BAD_GATEWAY, "AI服务调用失败，请稍后重试"),
    AI_INVALID_OUTPUT(50211, HttpStatus.BAD_GATEWAY, "AI结果未通过校验，请调整需求后重新生成"),
    /** 未预期的服务器内部错误，对外只暴露通用文案。 */
    INTERNAL_ERROR(50001, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误，请稍后重试");

    private final int code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(int code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
