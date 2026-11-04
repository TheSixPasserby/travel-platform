package com.example.travelplatform.itinerary.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.travelplatform.itinerary.entity.Itinerary;
import com.example.travelplatform.destination.entity.Destination;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ItineraryMapper extends BaseMapper<Itinerary> {
    String COUNTS="""
        (SELECT COUNT(*) FROM itinerary_days d WHERE d.itinerary_id=i.id) AS day_count,
        (SELECT COUNT(*) FROM itinerary_items p JOIN itinerary_days d ON p.itinerary_day_id=d.id WHERE d.itinerary_id=i.id) AS item_count
        """;
    String FILTER="""
        WHERE i.user_id=#{userId} AND i.is_deleted=0
        <if test="keyword != null">AND i.title LIKE #{keyword} ESCAPE '!'</if>
        <if test="status != null">AND i.status=#{status}</if>
        """;
    @Select("<script>SELECT COUNT(*) FROM itineraries i "+FILTER+"</script>")
    long count(@Param("userId") long userId,@Param("keyword") String keyword,@Param("status") String status);
    @Select("<script>SELECT i.*, "+COUNTS+" FROM itineraries i "+FILTER+" ORDER BY i.created_at DESC,i.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<ItineraryRow> page(@Param("userId") long userId,@Param("keyword") String keyword,@Param("status") String status,@Param("size") long size,@Param("offset") long offset);
    @Select("SELECT i.*, "+COUNTS+" FROM itineraries i WHERE i.id=#{id} AND i.is_deleted=0")
    ItineraryRow detail(long id);
    @Select("SELECT * FROM itineraries WHERE id=#{id} FOR UPDATE")
    // Clear cached child locators before waiting on the root, so the post-lock RC reads reach MySQL.
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    Itinerary lock(long id);
    @Select("SELECT * FROM destinations WHERE id=#{id} FOR UPDATE")
    Destination lockDestination(long id);
    @Update("""
        UPDATE itineraries SET title=#{title},destination_id=#{destinationId},start_date=#{startDate},end_date=#{endDate},
        notes=#{notes},status=#{status},updated_at=#{updatedAt} WHERE id=#{id} AND is_deleted=0
        """)
    int change(Itinerary value);
    @Update("UPDATE itineraries SET is_deleted=1,updated_at=#{time} WHERE id=#{id} AND is_deleted=0")
    int softDelete(@Param("id") long id,@Param("time") LocalDateTime time);
    @Update("UPDATE itineraries SET updated_at=#{time} WHERE id=#{id} AND is_deleted=0")
    int touch(@Param("id") long id,@Param("time") LocalDateTime time);
}
