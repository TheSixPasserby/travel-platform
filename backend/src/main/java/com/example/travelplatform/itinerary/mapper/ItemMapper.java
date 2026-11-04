package com.example.travelplatform.itinerary.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.travelplatform.itinerary.entity.ItineraryItem;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ItemMapper extends BaseMapper<ItineraryItem> {
    @Select("SELECT * FROM itinerary_items WHERE id=#{id}")
    ItineraryItem find(long id);
    @Select("SELECT * FROM itinerary_items WHERE itinerary_day_id=#{id} ORDER BY sort_order,id")
    List<ItineraryItem> forDay(long id);
    @Select("SELECT p.* FROM itinerary_items p JOIN itinerary_days d ON p.itinerary_day_id=d.id WHERE d.itinerary_id=#{id} ORDER BY p.itinerary_day_id,p.sort_order,p.id")
    List<ItineraryItem> forRoot(long id);
    @Select("SELECT COUNT(*) FROM itinerary_items p JOIN itinerary_days d ON p.itinerary_day_id=d.id WHERE d.itinerary_id=#{id}")
    long countRoot(long id);
    @Update("""
        UPDATE itinerary_items SET destination_id=#{destinationId},place_name=#{placeName},address_text=#{addressText},
        start_time=#{startTime},end_time=#{endTime},notes=#{notes},updated_at=#{updatedAt} WHERE id=#{id}
        """)
    int change(ItineraryItem value);
    @Update("UPDATE itinerary_items SET sort_order=#{position},updated_at=#{time} WHERE id=#{id} AND itinerary_day_id=#{day}")
    int position(@Param("id") long id,@Param("day") long day,@Param("position") int position,@Param("time") LocalDateTime time);
}
