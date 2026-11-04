package com.example.travelplatform.interaction.mapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface InteractionMapper {
    @Select("""
        SELECT EXISTS(SELECT 1 FROM guide_likes WHERE user_id=#{userId} AND guide_id=#{guideId}) AS liked,
        EXISTS(SELECT 1 FROM guide_favorites WHERE user_id=#{userId} AND guide_id=#{guideId}) AS favorited
        """)
    InteractionFlags flags(@Param("userId") long userId,@Param("guideId") long guideId);
    @Insert("INSERT INTO guide_likes(user_id,guide_id) VALUES(#{userId},#{guideId}) ON DUPLICATE KEY UPDATE id=id")
    void like(@Param("userId") long userId,@Param("guideId") long guideId);
    @Insert("INSERT INTO guide_favorites(user_id,guide_id) VALUES(#{userId},#{guideId}) ON DUPLICATE KEY UPDATE id=id")
    void favorite(@Param("userId") long userId,@Param("guideId") long guideId);
    @Delete("DELETE FROM guide_likes WHERE user_id=#{userId} AND guide_id=#{guideId}")
    void unlike(@Param("userId") long userId,@Param("guideId") long guideId);
    @Delete("DELETE FROM guide_favorites WHERE user_id=#{userId} AND guide_id=#{guideId}")
    void unfavorite(@Param("userId") long userId,@Param("guideId") long guideId);
}
