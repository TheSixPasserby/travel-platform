package com.example.travelplatform.guide.dto;

import java.util.List;

/** Shared author-editable fields; author and persistence state are never accepted. */
public class GuideWriteRequest {
    private String title;
    private String content;
    private String summary;
    private String destinationId;
    private String coverImageUrl;
    private Integer travelDays;
    private String budgetPerPerson;
    private String season;
    private List<String> tagIds;
    private List<GuideImageRequest> images;
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public String getContent() { return content; }
    public void setContent(String value) { this.content = value; }
    public String getSummary() { return summary; }
    public void setSummary(String value) { this.summary = value; }
    public String getDestinationId() { return destinationId; }
    public void setDestinationId(String value) { this.destinationId = value; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String value) { this.coverImageUrl = value; }
    public Integer getTravelDays() { return travelDays; }
    public void setTravelDays(Integer value) { this.travelDays = value; }
    public String getBudgetPerPerson() { return budgetPerPerson; }
    public void setBudgetPerPerson(String value) { this.budgetPerPerson = value; }
    public String getSeason() { return season; }
    public void setSeason(String value) { this.season = value; }
    public List<String> getTagIds() { return tagIds; }
    public void setTagIds(List<String> value) { this.tagIds = value; }
    public List<GuideImageRequest> getImages() { return images; }
    public void setImages(List<GuideImageRequest> value) { this.images = value; }
}
