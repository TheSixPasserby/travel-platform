package com.example.travelplatform.user.vo;
import com.example.travelplatform.user.entity.User;
public record PublicProfileVO(String id, String nickname, String avatarUrl, String bio) {
    public static PublicProfileVO from(User user) {
        return new PublicProfileVO(user.getId().toString(),user.getNickname(),user.getAvatarUrl(),user.getBio());
    }
}
