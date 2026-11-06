package com.example.travelplatform.destination.vo;
import com.example.travelplatform.destination.mapper.DestinationImageRow;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
public record DestinationImageVO(String id,String url,String altText,int sortOrder,String sourceUrl,String author,
        String license,String licenseUrl,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime retrievedAt) {
    public static DestinationImageVO from(DestinationImageRow r){return new DestinationImageVO(r.id().toString(),r.imageUrl(),r.altText(),r.sortOrder(),r.sourceUrl(),r.author(),r.license(),r.licenseUrl(),r.retrievedAt().atOffset(ZoneOffset.ofHours(8)));}
}
