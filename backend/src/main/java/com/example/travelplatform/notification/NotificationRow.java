package com.example.travelplatform.notification;
import java.time.LocalDateTime;
public record NotificationRow(long id,long actorId,long guideId,long commentId,String type,String content,LocalDateTime createdAt,LocalDateTime readAt) { }
