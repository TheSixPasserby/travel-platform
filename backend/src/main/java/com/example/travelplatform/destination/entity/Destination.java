package com.example.travelplatform.destination.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 目的地实体，对应 destinations 表。
 */
@TableName("destinations")
public class Destination {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String destinationType;

    private String country;

    private String province;

    private String city;

    private String description;

    private String coverImageUrl;

    private String recommendedSeasons;

    private String status;

    private Long parentId;

    private Integer sortOrder;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    private String address;
    public String getAddress() { return address; }
    public void setAddress(String value) { this.address = value; }
    private java.math.BigDecimal longitude;
    public java.math.BigDecimal getLongitude() { return longitude; }
    public void setLongitude(java.math.BigDecimal value) { this.longitude = value; }
    private java.math.BigDecimal latitude;
    public java.math.BigDecimal getLatitude() { return latitude; }
    public void setLatitude(java.math.BigDecimal value) { this.latitude = value; }
    private Integer recommendedDurationMinutes;
    public Integer getRecommendedDurationMinutes() { return recommendedDurationMinutes; }
    public void setRecommendedDurationMinutes(Integer value) { this.recommendedDurationMinutes = value; }
    private Integer recommendedDays;
    public Integer getRecommendedDays() { return recommendedDays; }
    public void setRecommendedDays(Integer value) { this.recommendedDays = value; }
    private String openingHours;
    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String value) { this.openingHours = value; }
    private String ticketInfo;
    public String getTicketInfo() { return ticketInfo; }
    public void setTicketInfo(String value) { this.ticketInfo = value; }
    private String officialUrl;
    public String getOfficialUrl() { return officialUrl; }
    public void setOfficialUrl(String value) { this.officialUrl = value; }
    private String sourceUrl;
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String value) { this.sourceUrl = value; }
    private LocalDateTime verifiedAt;
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime value) { this.verifiedAt = value; }
}
