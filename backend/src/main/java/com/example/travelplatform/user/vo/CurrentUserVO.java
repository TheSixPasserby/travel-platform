package com.example.travelplatform.user.vo;

import com.example.travelplatform.user.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 当前用户安全资料投影：{@code GET /auth/me}、{@code GET /users/me}、
 * 注册/登录/资料修改的响应 data 都使用同一个投影。
 *
 * <p>恰好 8 个字段：id/username/nickname/avatarUrl/bio/role/status/createdAt。
 * 密码、password_hash、isDeleted 等内部字段一律不出现在这里。
 *
 * <p>id 以十进制字符串返回，避免前端 Number 丢失大于 2^53-1 的精度。
 * createdAt 按 +08:00 输出 ISO 8601 毫秒时间。
 *
 * @param id        用户 ID 的十进制字符串
 * @param username  规范化账号
 * @param nickname  昵称
 * @param avatarUrl 受管头像相对路径，可为 null
 * @param bio       简介，可为 null
 * @param role      角色（USER/ADMIN）
 * @param status    状态（ACTIVE/DISABLED）
 * @param createdAt 创建时间（+08:00）
 */
public record CurrentUserVO(
        String id,
        String username,
        String nickname,
        String avatarUrl,
        String bio,
        String role,
        String status,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "GMT+8")
        OffsetDateTime createdAt) {

    /** 数据库与接口约定的时间偏移：Asia/Shanghai 固定 +08:00。 */
    private static final ZoneOffset RESPONSE_OFFSET = ZoneOffset.ofHours(8);

    /**
     * 把实体转换为安全投影。仅复制白名单字段，绝不透传 passwordHash 或 isDeleted。
     *
     * @param user 已持久化的用户实体；createdAt 由数据库回填
     * @return 安全投影
     */
    public static CurrentUserVO from(User user) {
        return new CurrentUserVO(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getRole(),
                user.getStatus(),
                toResponseTime(user.getCreatedAt()));
    }

    private static OffsetDateTime toResponseTime(LocalDateTime createdAt) {
        return createdAt == null ? null : createdAt.atOffset(RESPONSE_OFFSET);
    }
}
