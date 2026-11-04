package com.example.travelplatform.itinerary.dto;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
public class ItineraryUpdateRequest {
    private String title;
    private String destinationId;
    private String startDate;
    private String endDate;
    private String notes;
    private String status;
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title=value; }
    public String getDestinationId() { return destinationId; }
    public void setDestinationId(String value) { this.destinationId=value; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String value) { this.startDate=value; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String value) { this.endDate=value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { this.notes=value; }
    public String getStatus() { return status; }
    @JsonSetter(nulls=Nulls.FAIL)
    public void setStatus(String value) { this.status=value; }
}
