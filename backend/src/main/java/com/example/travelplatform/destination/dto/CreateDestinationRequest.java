package com.example.travelplatform.destination.dto;

/**
 * 创建目的地请求，仅用于区分“字段缺失”与“显式 null”。
 */
public class CreateDestinationRequest extends DestinationWriteRequest {

    public CreateDestinationRequest() {
        setDestinationType("CITY");
        setCountry("中国");
        setStatus("ACTIVE");
        setSortOrder(0);
    }
}
