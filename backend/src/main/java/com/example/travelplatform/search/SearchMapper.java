package com.example.travelplatform.search;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SearchMapper {
    String PUBLIC_GUIDE = "g.status='PUBLISHED' AND g.is_deleted=0 AND u.status='ACTIVE' AND u.is_deleted=0";
    String PUBLIC_DESTINATION = "d.status='ACTIVE' AND d.is_deleted=0 AND (d.destination_type='CITY' OR (d.destination_type='SCENIC_SPOT' AND EXISTS (SELECT 1 FROM destinations p WHERE p.id=d.parent_id AND p.destination_type='CITY' AND p.status='ACTIVE' AND p.is_deleted=0)))";
    String GUIDE_MATCH = """
        (g.title LIKE #{pattern} ESCAPE '!' OR g.summary LIKE #{pattern} ESCAPE '!'
          OR EXISTS (SELECT 1 FROM destinations d WHERE d.id=g.destination_id AND
        """ + PUBLIC_DESTINATION + """
            AND d.name LIKE #{pattern} ESCAPE '!')
          OR EXISTS (SELECT 1 FROM guide_destinations gd JOIN destinations d ON d.id=gd.destination_id
            WHERE gd.guide_id=g.id AND
        """ + PUBLIC_DESTINATION + """
            AND d.name LIKE #{pattern} ESCAPE '!')
          OR EXISTS (SELECT 1 FROM guide_tags gt JOIN tags t ON t.id=gt.tag_id
            WHERE gt.guide_id=g.id AND t.name LIKE #{pattern} ESCAPE '!'))
        """;

    @Select("<script>SELECT results.* FROM (" + """
        SELECT 'GUIDE' AS kind,g.id,g.title,g.summary AS subtitle,g.cover_image_url,NULL AS username,
          g.published_at AS result_time,
          CASE WHEN g.title=#{keyword} THEN 2 WHEN g.title LIKE #{pattern} ESCAPE '!' THEN 1 ELSE 0 END AS score
        FROM guides g JOIN users u ON u.id=g.author_id WHERE
        """ + PUBLIC_GUIDE + " AND " + GUIDE_MATCH + """
        UNION ALL
        SELECT d.destination_type AS kind,d.id,d.name AS title,CONCAT_WS(' · ',d.province,d.city) AS subtitle,
          d.cover_image_url,NULL AS username,d.created_at AS result_time,
          CASE WHEN d.name=#{keyword} THEN 2 WHEN d.name LIKE #{pattern} ESCAPE '!' THEN 1 ELSE 0 END AS score
        FROM destinations d WHERE
        """ + PUBLIC_DESTINATION + """
          AND (d.name LIKE #{pattern} ESCAPE '!' OR d.city LIKE #{pattern} ESCAPE '!' OR d.province LIKE #{pattern} ESCAPE '!')
        UNION ALL
        SELECT 'CREATOR' AS kind,u.id,u.nickname AS title,u.bio AS subtitle,u.avatar_url AS cover_image_url,u.username,
          u.created_at AS result_time,
          CASE WHEN u.nickname=#{keyword} OR u.username=#{keyword} THEN 2
            WHEN u.nickname LIKE #{pattern} ESCAPE '!' OR u.username LIKE #{pattern} ESCAPE '!' THEN 1 ELSE 0 END AS score
        FROM users u WHERE u.status='ACTIVE' AND u.is_deleted=0
          AND (u.nickname LIKE #{pattern} ESCAPE '!' OR u.username LIKE #{pattern} ESCAPE '!' OR u.bio LIKE #{pattern} ESCAPE '!')
        UNION ALL
        SELECT 'TAG' AS kind,t.id,t.name AS title,NULL AS subtitle,NULL AS cover_image_url,NULL AS username,
          t.created_at AS result_time,CASE WHEN t.name=#{keyword} THEN 2 ELSE 1 END AS score
        FROM tags t WHERE t.name LIKE #{pattern} ESCAPE '!'
          AND EXISTS (SELECT 1 FROM guide_tags gt JOIN guides g ON g.id=gt.guide_id JOIN users u ON u.id=g.author_id
            WHERE gt.tag_id=t.id AND
        """ + PUBLIC_GUIDE + """
          )
        ) results WHERE result_time &lt;= #{snapshot}
        <if test="kind != null">AND kind=#{kind}</if>
        <if test="cursor != null">
          AND
          <if test="!latest">(score &lt; #{cursor.score} OR (score=#{cursor.score} AND</if>
          (result_time &lt; #{cursor.time} OR (result_time=#{cursor.time} AND
            (kind &lt; #{cursor.kind} OR (kind=#{cursor.kind} AND id &lt; #{cursor.id}))))
          <if test="!latest">))</if>
        </if>
        ORDER BY <if test="!latest">score DESC,</if> result_time DESC,kind DESC,id DESC
        LIMIT #{limit}</script>
        """)
    List<SearchRow> search(@Param("keyword") String keyword, @Param("pattern") String pattern,
                           @Param("kind") String kind, @Param("latest") boolean latest,
                           @Param("snapshot") LocalDateTime snapshot, @Param("cursor") SearchCursor cursor,
                           @Param("limit") long limit);
}
