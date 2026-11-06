package com.example.travelplatform.guide.mapper;
import com.example.travelplatform.guide.vo.GuideSummaryVO;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface GuideDestinationsMapper {
    @Select("SELECT destination_id FROM guide_destinations WHERE guide_id=#{id} ORDER BY destination_id") List<Long> ids(long id);
    @Select("SELECT CAST(d.id AS CHAR) AS id,d.name,CASE WHEN d.is_deleted=1 THEN 'INACTIVE' ELSE d.status END AS status FROM guide_destinations gd JOIN destinations d ON d.id=gd.destination_id WHERE gd.guide_id=#{id} ORDER BY d.id")
    List<GuideSummaryVO.DestinationRef> references(long id);
    @Delete("DELETE FROM guide_destinations WHERE guide_id=#{id}") void clear(long id);
    @Insert("INSERT INTO guide_destinations(guide_id,destination_id) VALUES(#{guideId},#{destinationId})")
    void add(@Param("guideId") long guideId,@Param("destinationId") long destinationId);
}
