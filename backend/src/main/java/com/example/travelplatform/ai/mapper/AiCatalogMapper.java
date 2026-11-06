package com.example.travelplatform.ai.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AiCatalogMapper {
    record GuideSource(Long id, String title, String content) { }

    @Select("""
        SELECT g.id,g.title,LEFT(g.content,1600) AS content FROM guides g
        WHERE g.status='PUBLISHED' AND g.is_deleted=0 AND
        (g.destination_id=#{cityId} OR EXISTS (
          SELECT 1 FROM guide_destinations gd JOIN destinations d ON d.id=gd.destination_id
          WHERE gd.guide_id=g.id AND d.parent_id=#{cityId} AND d.destination_type='SCENIC_SPOT' AND d.is_deleted=0))
        ORDER BY g.published_at DESC,g.id DESC LIMIT 5
        """)
    List<GuideSource> publishedGuides(long cityId);
}
