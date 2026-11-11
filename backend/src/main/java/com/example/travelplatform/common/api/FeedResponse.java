package com.example.travelplatform.common.api;

import java.util.List;

public record FeedResponse<T>(List<T> records, String nextCursor, boolean hasMore) { }
