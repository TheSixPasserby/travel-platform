package com.example.travelplatform.destination.service;
import com.example.travelplatform.destination.dto.DestinationWriteRequest;
import com.example.travelplatform.destination.entity.Destination;
import java.time.ZoneOffset;
/** Preserve newly introduced fields for older clients; explicit null still clears a field. */
final class DestinationDetailsMerge {
    private DestinationDetailsMerge() { }
    static DestinationWriteRequest merge(DestinationWriteRequest r,Destination old){
        if(old==null)return r;
        DestinationWriteRequest out=new DestinationWriteRequest();
        out.setAddress(r.detailProvided("address")?r.getAddress():old.getAddress());
        out.setLongitude(r.detailProvided("longitude")?r.getLongitude():old.getLongitude()==null?null:old.getLongitude().toPlainString());
        out.setLatitude(r.detailProvided("latitude")?r.getLatitude():old.getLatitude()==null?null:old.getLatitude().toPlainString());
        out.setRecommendedDurationMinutes(r.detailProvided("recommendedDurationMinutes")?r.getRecommendedDurationMinutes():old.getRecommendedDurationMinutes());
        out.setRecommendedDays(r.detailProvided("recommendedDays")?r.getRecommendedDays():old.getRecommendedDays());
        out.setOpeningHours(r.detailProvided("openingHours")?r.getOpeningHours():old.getOpeningHours());
        out.setTicketInfo(r.detailProvided("ticketInfo")?r.getTicketInfo():old.getTicketInfo());
        out.setOfficialUrl(r.detailProvided("officialUrl")?r.getOfficialUrl():old.getOfficialUrl());
        out.setSourceUrl(r.detailProvided("sourceUrl")?r.getSourceUrl():old.getSourceUrl());
        out.setVerifiedAt(r.detailProvided("verifiedAt")?r.getVerifiedAt():old.getVerifiedAt()==null?null:old.getVerifiedAt().atOffset(ZoneOffset.ofHours(8)).toString());
        if(r.detailProvided("images"))out.setImages(r.getImages());
        return out;
    }
}
