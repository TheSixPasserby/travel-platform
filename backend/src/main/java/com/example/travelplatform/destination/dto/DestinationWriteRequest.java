package com.example.travelplatform.destination.dto;

/**
 * 目的地创建/更新请求的原始输入，全部字段默认 null。
 */
public class DestinationWriteRequest {

    private String name;

    private String destinationType;

    private String parentId;

    private String country;

    private String province;

    private String city;

    private String description;

    private String coverImageUrl;

    private String recommendedSeasons;

    private String status;

    private Integer sortOrder;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDestinationType() {
        return destinationType;
    }

    public void setDestinationType(String destinationType) {
        this.destinationType = destinationType;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public String getRecommendedSeasons() {
        return recommendedSeasons;
    }

    public void setRecommendedSeasons(String recommendedSeasons) {
        this.recommendedSeasons = recommendedSeasons;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    private final java.util.Set<String> providedDetails = new java.util.HashSet<>();
    public boolean detailProvided(String field) { return providedDetails.contains(field); }
    private String address;
    public String getAddress() { return address; }
    public void setAddress(String value) { this.address = value; providedDetails.add("address"); }
    private String longitude;
    public String getLongitude() { return longitude; }
    public void setLongitude(String value) { this.longitude = value; providedDetails.add("longitude"); }
    private String latitude;
    public String getLatitude() { return latitude; }
    public void setLatitude(String value) { this.latitude = value; providedDetails.add("latitude"); }
    private Integer recommendedDurationMinutes;
    public Integer getRecommendedDurationMinutes() { return recommendedDurationMinutes; }
    public void setRecommendedDurationMinutes(Integer value) { this.recommendedDurationMinutes = value; providedDetails.add("recommendedDurationMinutes"); }
    private Integer recommendedDays;
    public Integer getRecommendedDays() { return recommendedDays; }
    public void setRecommendedDays(Integer value) { this.recommendedDays = value; providedDetails.add("recommendedDays"); }
    private String openingHours;
    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String value) { this.openingHours = value; providedDetails.add("openingHours"); }
    private String ticketInfo;
    public String getTicketInfo() { return ticketInfo; }
    public void setTicketInfo(String value) { this.ticketInfo = value; providedDetails.add("ticketInfo"); }
    private String officialUrl;
    public String getOfficialUrl() { return officialUrl; }
    public void setOfficialUrl(String value) { this.officialUrl = value; providedDetails.add("officialUrl"); }
    private String sourceUrl;
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String value) { this.sourceUrl = value; providedDetails.add("sourceUrl"); }
    private String verifiedAt;
    public String getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(String value) { this.verifiedAt = value; providedDetails.add("verifiedAt"); }
    private java.util.List<DestinationImageRequest> images;
    public java.util.List<DestinationImageRequest> getImages() { return images; }
    public void setImages(java.util.List<DestinationImageRequest> value) { images=value; providedDetails.add("images"); }
}
