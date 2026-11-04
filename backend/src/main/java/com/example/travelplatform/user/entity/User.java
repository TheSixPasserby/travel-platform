package com.example.travelplatform.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户实体，与 database/schema.sql 的 users 表一一对应。
 *
 * <p>该对象只在 Service 与 Mapper 之间传递，绝不直接作为响应返回；对外一律使用 VO。
 * 刻意不提供包含 passwordHash 的 toString，避免哈希进入日志。
 */
@TableName("users")
public class User {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规范化后的唯一账号：strip + 小写，ASCII [a-z0-9_]{3,50}。 */
    private String username;

    /** BCrypt 密码哈希，禁止对外输出。 */
    private String passwordHash;

    /** 昵称。 */
    private String nickname;

    /** 头像受管相对路径，可为 null。 */
    private String avatarUrl;

    /** 个人简介，可为 null。 */
    private String bio;

    /** 角色：USER / ADMIN，由服务端赋值。 */
    private String role;

    /** 状态：ACTIVE / DISABLED，由服务端赋值。 */
    private String status;

    /** 逻辑删除标记（0 未删除、1 已删除），由 MyBatis-Plus 统一处理。 */
    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
