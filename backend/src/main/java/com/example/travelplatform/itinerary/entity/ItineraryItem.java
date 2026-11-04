package com.example.travelplatform.itinerary.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
@TableName("itinerary_items")
public class ItineraryItem {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long itineraryDayId;
    private Long destinationId;
    private String placeName;
    private String addressText;
    private LocalTime startTime;
    private LocalTime endTime;
    private String notes;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public Long getItineraryDayId() { return itineraryDayId; }
    public void setItineraryDayId(Long value) { this.itineraryDayId=value; }
    public Long getDestinationId() { return destinationId; }
    public void setDestinationId(Long value) { this.destinationId=value; }
    public String getPlaceName() { return placeName; }
    public void setPlaceName(String value) { this.placeName=value; }
    public String getAddressText() { return addressText; }
    public void setAddressText(String value) { this.addressText=value; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime value) { this.startTime=value; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime value) { this.endTime=value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { this.notes=value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { this.sortOrder=value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt=value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt=value; }
}
