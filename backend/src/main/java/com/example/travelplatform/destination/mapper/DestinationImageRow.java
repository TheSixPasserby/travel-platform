package com.example.travelplatform.destination.mapper;
import java.time.LocalDateTime;
public record DestinationImageRow(Long id,Long destinationId,String imageUrl,String altText,int sortOrder,
        String sourceUrl,String author,String license,String licenseUrl,LocalDateTime retrievedAt) { }
