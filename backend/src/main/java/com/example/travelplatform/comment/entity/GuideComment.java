package com.example.travelplatform.comment.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("guide_comments")
public class GuideComment {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long guideId;
    private Long userId;
    private String content;
    private String status;
    private String ipRegion;
    @TableLogic(value="0",delval="1")
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public Long getGuideId() { return guideId; }
    public void setGuideId(Long value) { this.guideId=value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId=value; }
    public String getContent() { return content; }
    public void setContent(String value) { this.content=value; }
    public String getIpRegion() { return ipRegion; }
    public void setIpRegion(String value) { this.ipRegion=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status=value; }
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer value) { this.isDeleted=value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt=value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt=value; }
}
