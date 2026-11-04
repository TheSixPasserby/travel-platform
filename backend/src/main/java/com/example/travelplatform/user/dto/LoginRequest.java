package com.example.travelplatform.user.dto;

/**
 * 登录请求（POST /api/v1/auth/login）。
 *
 * <p>只接受 username 与 password 两个字段；多余或权限字段由严格反序列化拒绝为 400。
 * 密码不 trim，按原样参与 BCrypt 校验。
 *
 * @param username 原始账号（策略会 strip 并小写后查询）
 * @param password 原始密码
 */
public record LoginRequest(String username, String password) {
}
