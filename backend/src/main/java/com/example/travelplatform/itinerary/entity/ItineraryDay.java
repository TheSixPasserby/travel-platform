package com.example.travelplatform.itinerary.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
@TableName("itinerary_days")
public class ItineraryDay {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long itineraryId;
    private Integer dayNo;
    private LocalDate travelDate;
    private String title;
    private String notes;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public Long getItineraryId() { return itineraryId; }
    public void setItineraryId(Long value) { this.itineraryId=value; }
    public Integer getDayNo() { return dayNo; }
    public void setDayNo(Integer value) { this.dayNo=value; }
    public LocalDate getTravelDate() { return travelDate; }
    public void setTravelDate(LocalDate value) { this.travelDate=value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title=value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { this.notes=value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { this.sortOrder=value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt=value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt=value; }
}
