package com.example.travelplatform.user.vo;
/** Explicit public allowlist. No credentials, role, status, private collections or itineraries. */
public record CreatorProfileVO(String id,String username,String nickname,String avatarUrl,String bio,long publishedGuideCount,long likeCount,long favoriteCount) { }
