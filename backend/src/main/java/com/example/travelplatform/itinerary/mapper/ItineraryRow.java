package com.example.travelplatform.itinerary.mapper;
import com.example.travelplatform.itinerary.entity.Itinerary;
public class ItineraryRow extends Itinerary {
    private long dayCount;
    private long itemCount;
    public long getDayCount(){return dayCount;}
    public void setDayCount(long value){dayCount=value;}
    public long getItemCount(){return itemCount;}
    public void setItemCount(long value){itemCount=value;}
}
