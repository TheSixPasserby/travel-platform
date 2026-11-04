package com.example.travelplatform.user.vo;

/**
 * 登录成功响应 data：{@code POST /api/v1/auth/login}。
 *
 * <p>tokenName 固定为 {@code satoken}，客户端把它作为请求头名、tokenValue 作为请求头值。
 * user 与 me 接口使用同一安全投影，登录响应不额外暴露内部字段。
 *
 * @param tokenName  令牌请求头名，固定 satoken
 * @param tokenValue 不透明令牌值，仅当前会话有效
 * @param user       当前用户安全资料
 */
public record LoginVO(String tokenName, String tokenValue, CurrentUserVO user) {

    /** 本项目唯一的令牌请求头名。 */
    public static final String TOKEN_NAME = "satoken";
}
