package com.example.travelplatform.administration.vo;
import com.example.travelplatform.user.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.*;
public record AdminUserVO(String id,String username,String nickname,String avatarUrl,String role,String status,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime createdAt,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime updatedAt) {
    public static AdminUserVO from(User user){
        return new AdminUserVO(user.getId().toString(),user.getUsername(),user.getNickname(),user.getAvatarUrl(),user.getRole(),user.getStatus(),user.getCreatedAt().atOffset(ZoneOffset.ofHours(8)),user.getUpdatedAt().atOffset(ZoneOffset.ofHours(8)));
    }
}
