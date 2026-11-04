package com.example.travelplatform.guide.dto;
public class CreateGuideRequest extends GuideWriteRequest {
    private String status = "DRAFT";
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
}
