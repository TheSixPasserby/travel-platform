package com.example.travelplatform.search;

import java.time.LocalDateTime;

public record SearchRow(String kind, long id, String title, String subtitle,
                        String coverImageUrl, String username, LocalDateTime resultTime, int score) { }
