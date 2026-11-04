package com.example.travelplatform.itinerary.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.travelplatform.itinerary.entity.ItineraryDay;
import org.apache.ibatis.annotations.*;
import java.time.*;
import java.util.List;

@Mapper
public interface DayMapper extends BaseMapper<ItineraryDay> {
    @Select("SELECT * FROM itinerary_days WHERE id=#{id}")
    ItineraryDay find(long id);
    @Select("SELECT * FROM itinerary_days WHERE itinerary_id=#{id} ORDER BY travel_date,id")
    List<ItineraryDay> forRoot(long id);
    @Select("SELECT COUNT(*) FROM itinerary_days WHERE itinerary_id=#{root} AND travel_date=#{date} AND (#{except} IS NULL OR id != #{except})")
    long conflicts(@Param("root") long root,@Param("date") LocalDate date,@Param("except") Long except);
    @Update("UPDATE itinerary_days SET travel_date=#{travelDate},day_no=#{dayNo},sort_order=#{sortOrder},title=#{title},notes=#{notes},updated_at=#{updatedAt} WHERE id=#{id}")
    int change(ItineraryDay value);
    @Update("UPDATE itinerary_days SET day_no=day_no+1000 WHERE itinerary_id=#{root}")
    int stageNumbers(long root);
    @Update("UPDATE itinerary_days SET day_no=DATEDIFF(travel_date,#{start})+1,sort_order=DATEDIFF(travel_date,#{start}),updated_at=#{time} WHERE itinerary_id=#{root}")
    int renumber(@Param("root") long root,@Param("start") LocalDate start,@Param("time") LocalDateTime time);
}
