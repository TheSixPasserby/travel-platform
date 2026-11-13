package com.example.travelplatform.search;

public record SearchHit(String kind, String id, String title, String subtitle,
                        String coverImageUrl, String username, String href) { }
