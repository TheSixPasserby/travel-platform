package com.example.travelplatform.guide.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.travelplatform.guide.entity.Guide;
import com.example.travelplatform.guide.vo.GuideImageVO;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface GuideMapper extends BaseMapper<Guide> {
    String RELATED_FILTER="""
        WHERE g.is_deleted=0 AND g.status='PUBLISHED' AND
        <choose><when test="type == 'CITY'">
          (g.destination_id=#{destination} OR EXISTS (SELECT 1 FROM guide_destinations gd JOIN destinations spot ON spot.id=gd.destination_id WHERE gd.guide_id=g.id AND spot.parent_id=#{destination} AND spot.destination_type='SCENIC_SPOT' AND spot.is_deleted=0))
        </when><when test="type == 'SCENIC_SPOT'">
          EXISTS (SELECT 1 FROM guide_destinations gd WHERE gd.guide_id=g.id AND gd.destination_id=#{destination})
        </when><otherwise>g.destination_id=#{destination}</otherwise></choose>
        <if test="keyword != null">AND (g.title LIKE #{keyword} ESCAPE '!' OR g.summary LIKE #{keyword} ESCAPE '!')</if>
        <if test="tag != null">AND EXISTS (SELECT 1 FROM guide_tags t WHERE t.guide_id=g.id AND t.tag_id=#{tag})</if>
        """;
    @Select("<script>SELECT COUNT(*) FROM guides g "+RELATED_FILTER+"</script>")
    long countRelated(@Param("destination") long destination,@Param("type") String type,@Param("keyword") String keyword,@Param("tag") Long tag);
    @Select("<script>SELECT g.*, "+COUNTS+" FROM guides g "+RELATED_FILTER+" ORDER BY g.published_at DESC,g.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<GuideRow> pageRelated(@Param("destination") long destination,@Param("type") String type,@Param("keyword") String keyword,@Param("tag") Long tag,@Param("limit") long limit,@Param("offset") long offset);
    String FILTER = """
        WHERE g.is_deleted=0
        <if test="filter.authorId != null">AND g.author_id=#{filter.authorId}</if>
        <if test="filter.status != null">AND g.status=#{filter.status}</if>
        <if test="filter.keywordPattern != null">AND (g.title LIKE #{filter.keywordPattern} ESCAPE '!' OR g.summary LIKE #{filter.keywordPattern} ESCAPE '!')</if>
        <if test="filter.destinationId != null">AND g.destination_id=#{filter.destinationId}</if>
        <if test="filter.tagId != null">AND EXISTS (SELECT 1 FROM guide_tags ft WHERE ft.guide_id=g.id AND ft.tag_id=#{filter.tagId})</if>
        """;
    String COUNTS = """
        (SELECT COUNT(*) FROM guide_likes l WHERE l.guide_id=g.id) AS like_count,
        (SELECT COUNT(*) FROM guide_favorites f WHERE f.guide_id=g.id) AS favorite_count,
        (SELECT COUNT(*) FROM guide_comments c WHERE c.guide_id=g.id AND c.status='NORMAL' AND c.is_deleted=0) AS comment_count
        """;

    @Select("<script>SELECT COUNT(*) FROM guides g " + FILTER + "</script>")
    long count(@Param("filter") GuideSearch filter);

    @Select("<script>SELECT g.*, " + COUNTS + " FROM guides g " + FILTER + """
        <choose>
          <when test="filter.authorId != null">ORDER BY g.created_at DESC,g.id DESC</when>
          <when test="filter.popular">ORDER BY like_count DESC,g.published_at DESC,g.id DESC</when>
          <otherwise>ORDER BY g.published_at DESC,g.id DESC</otherwise>
        </choose>
        LIMIT #{limit} OFFSET #{offset}</script>
        """)
    List<GuideRow> page(@Param("filter") GuideSearch filter, @Param("limit") long limit, @Param("offset") long offset);

    @Select("<script>SELECT g.*, "+COUNTS+" FROM guides g "+FILTER+" ORDER BY g.created_at DESC,g.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<GuideRow> adminPage(@Param("filter") GuideSearch filter,@Param("limit") long limit,@Param("offset") long offset);

    String FAVORITES_FILTER="""
        WHERE saved.user_id=#{userId} AND g.status='PUBLISHED' AND g.is_deleted=0
        <if test="keyword != null">AND (g.title LIKE #{keyword} ESCAPE '!' OR g.summary LIKE #{keyword} ESCAPE '!')</if>
        """;
    @Select("<script>SELECT COUNT(*) FROM guides g JOIN guide_favorites saved ON saved.guide_id=g.id "+FAVORITES_FILTER+"</script>")
    long countFavorites(@Param("userId") long userId,@Param("keyword") String keyword);
    @Select("<script>SELECT g.*, "+COUNTS+" FROM guides g JOIN guide_favorites saved ON saved.guide_id=g.id "+FAVORITES_FILTER+
            " ORDER BY saved.created_at DESC,saved.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<GuideRow> favorites(@Param("userId") long userId,@Param("keyword") String keyword,@Param("limit") long limit,@Param("offset") long offset);

    @Select("<script>SELECT g.*, "+COUNTS+" FROM guides g "+FILTER+" ORDER BY g.published_at DESC,g.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<GuideRow> publicAuthorPage(@Param("filter") GuideSearch filter,@Param("limit") long limit,@Param("offset") long offset);

    @Select("""
        SELECT g.*,
        """+COUNTS+"""
        FROM guides g
        WHERE g.is_deleted=0 AND g.status='PUBLISHED' AND g.id != #{id}
          AND ((#{destination} IS NOT NULL AND g.destination_id=#{destination})
            OR EXISTS (SELECT 1 FROM guide_tags gt JOIN guide_tags own ON own.tag_id=gt.tag_id
                       WHERE own.guide_id=#{id} AND gt.guide_id=g.id))
        ORDER BY CASE WHEN #{destination} IS NOT NULL AND g.destination_id=#{destination} THEN 0 ELSE 1 END,
                 like_count DESC,g.published_at DESC,g.id DESC
        LIMIT #{limit}
        """)
    List<GuideRow> recommendations(@Param("id") long id,@Param("destination") Long destination,@Param("limit") long limit);

    @Select("SELECT g.*, " + COUNTS + " FROM guides g WHERE g.id=#{id} AND g.is_deleted=0")
    GuideRow detail(long id);

    @Select("SELECT * FROM guides WHERE id=#{id} FOR UPDATE")
    Guide lock(long id);

    @Select("""
        <script>SELECT gt.guide_id,t.id,t.name FROM guide_tags gt JOIN tags t ON t.id=gt.tag_id
        WHERE gt.guide_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY t.id</script>
        """)
    List<GuideTagRow> tagsFor(@Param("ids") List<Long> ids);

    @Select("SELECT image_url AS url,alt_text AS altText,sort_order AS sortOrder FROM guide_images WHERE guide_id=#{id} ORDER BY sort_order,id")
    List<GuideImageVO> images(long id);

    @Select("SELECT id AS id,name AS name FROM tags ORDER BY id")
    List<com.example.travelplatform.guide.vo.TagVO> allTags();

    @Select("""
        <script>SELECT id FROM tags WHERE id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY id FOR SHARE</script>
        """)
    List<Long> lockTags(@Param("ids") List<Long> ids);

    @Select("SELECT * FROM destinations WHERE id=#{id} FOR UPDATE")
    com.example.travelplatform.destination.entity.Destination lockDestination(long id);

    @Delete("DELETE FROM guide_images WHERE guide_id=#{id}")
    void deleteImages(long id);
    @Delete("DELETE FROM guide_tags WHERE guide_id=#{id}")
    void deleteTags(long id);
    @Insert("INSERT INTO guide_images(guide_id,image_url,alt_text,sort_order) VALUES (#{guideId},#{url},#{altText},#{sortOrder})")
    void addImage(@Param("guideId") long guideId,@Param("url") String url,@Param("altText") String altText,@Param("sortOrder") int sortOrder);
    @Insert("INSERT INTO guide_tags(guide_id,tag_id) VALUES (#{guideId},#{tagId})")
    void addTag(@Param("guideId") long guideId,@Param("tagId") long tagId);
}
