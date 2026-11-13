package com.example.travelplatform.sharing;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface PublicContentMapper {
    String DESTINATIONS = """
        FROM destinations d WHERE d.is_deleted=0 AND d.status='ACTIVE'
        AND (d.destination_type!='SCENIC_SPOT' OR EXISTS (SELECT 1 FROM destinations parent WHERE parent.id=d.parent_id AND parent.destination_type='CITY' AND parent.status='ACTIVE' AND parent.is_deleted=0))
        AND EXISTS (SELECT 1 FROM guides g WHERE g.author_id=#{author} AND g.status='PUBLISHED' AND g.is_deleted=0
          AND (g.destination_id=d.id OR EXISTS (SELECT 1 FROM guide_destinations gd WHERE gd.guide_id=g.id AND gd.destination_id=d.id)))
        """;
    record DestinationRow(long id,String name,String destinationType,String coverImageUrl,long guideCount) { }
    @Select("SELECT COUNT(*) "+DESTINATIONS)
    long countDestinations(@Param("author") long author);
    @Select("""
        SELECT d.id,d.name,d.destination_type,d.cover_image_url,
          (SELECT COUNT(*) FROM guides g WHERE g.author_id=#{author} AND g.status='PUBLISHED' AND g.is_deleted=0
            AND (g.destination_id=d.id OR EXISTS (SELECT 1 FROM guide_destinations gd WHERE gd.guide_id=g.id AND gd.destination_id=d.id))) AS guide_count
        """+DESTINATIONS+" ORDER BY guide_count DESC,d.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<DestinationRow> destinations(@Param("author") long author,@Param("limit") long limit,@Param("offset") long offset);
    @Select("SELECT g.id FROM guides g JOIN users u ON u.id=g.author_id WHERE g.status='PUBLISHED' AND g.is_deleted=0 AND u.status='ACTIVE' AND u.is_deleted=0 ORDER BY g.id LIMIT 5000")
    List<Long> guideIds();
    @Select("SELECT username FROM users WHERE status='ACTIVE' AND is_deleted=0 ORDER BY id LIMIT 5000")
    List<String> usernames();
}
