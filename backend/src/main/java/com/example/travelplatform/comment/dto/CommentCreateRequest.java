package com.example.travelplatform.comment.dto;
import java.util.List;
public record CommentCreateRequest(String content, String replyToId, List<String> mentionUserIds) {
    public CommentCreateRequest(String content) { this(content, null, null); }
}
