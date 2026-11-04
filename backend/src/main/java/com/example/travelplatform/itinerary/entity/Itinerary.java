package com.example.travelplatform.itinerary.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
@TableName("itineraries")
public class Itinerary {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long userId;
    private Long destinationId;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String notes;
    private String status;
    @TableLogic(value="0",delval="1")
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId=value; }
    public Long getDestinationId() { return destinationId; }
    public void setDestinationId(Long value) { this.destinationId=value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title=value; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate value) { this.startDate=value; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate value) { this.endDate=value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { this.notes=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status=value; }
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer value) { this.isDeleted=value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt=value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt=value; }
}
