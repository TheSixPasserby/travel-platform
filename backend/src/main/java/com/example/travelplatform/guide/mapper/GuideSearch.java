package com.example.travelplatform.guide.mapper;
public record GuideSearch(Long authorId, String status, String keywordPattern, Long destinationId, Long tagId, boolean popular) { }
