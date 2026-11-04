package com.example.travelplatform.user.dto;

/**
 * 注册请求（POST /api/v1/auth/register）。
 *
 * <p>字段白名单由 Jackson 的 {@code fail-on-unknown-properties} 强制执行：
 * 夹带 role/userId/password_hash 等字段直接 400。字符串字段同样拒绝数值/布尔值的隐式转换。
 * 三个字段的取值规则（账号规范化、密码码点与字节边界、昵称 strip）由
 * {@link com.example.travelplatform.user.validation.UserInputPolicy} 在 Service 中校验；
 * {@code nickname} 缺省时由 Service 使用规范化账号作为默认昵称。
 *
 * @param username 原始账号（策略会 strip 并小写）
 * @param password 原始密码（策略不 trim）
 * @param nickname 可选昵称；为 null 表示缺省
 */
public record RegisterRequest(String username, String password, String nickname) {
}
