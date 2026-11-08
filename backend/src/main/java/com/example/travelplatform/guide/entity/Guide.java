package com.example.travelplatform.guide.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
@TableName("guides")
public class Guide {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long authorId;
    private Long destinationId;
    private String title;
    private String summary;
    private String coverImageUrl;
    private String content;
    private Integer travelDays;
    private BigDecimal budgetPerPerson;
    private String season;
    private String status;
    private String ipRegion;
    private LocalDateTime publishedAt;
    @TableLogic(value="0", delval="1")
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long v) { this.authorId = v; }
    public Long getDestinationId() { return destinationId; }
    public void setDestinationId(Long v) { this.destinationId = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getSummary() { return summary; }
    public void setSummary(String v) { this.summary = v; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String v) { this.coverImageUrl = v; }
    public String getContent() { return content; }
    public void setContent(String v) { this.content = v; }
    public Integer getTravelDays() { return travelDays; }
    public void setTravelDays(Integer v) { this.travelDays = v; }
    public BigDecimal getBudgetPerPerson() { return budgetPerPerson; }
    public void setBudgetPerPerson(BigDecimal v) { this.budgetPerPerson = v; }
    public String getSeason() { return season; }
    public void setSeason(String v) { this.season = v; }
    public String getIpRegion() { return ipRegion; }
    public void setIpRegion(String value) { this.ipRegion=value; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime v) { this.publishedAt = v; }
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer v) { this.isDeleted = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
