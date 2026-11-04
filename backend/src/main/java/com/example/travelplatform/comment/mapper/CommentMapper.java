package com.example.travelplatform.comment.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.travelplatform.comment.entity.GuideComment;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<GuideComment> {
    @Select("SELECT COUNT(*) FROM guide_comments WHERE guide_id=#{id} AND status='NORMAL' AND is_deleted=0")
    long countPublic(long id);
    @Select("SELECT * FROM guide_comments WHERE guide_id=#{id} AND status='NORMAL' AND is_deleted=0 ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
    List<GuideComment> page(@Param("id") long id,@Param("limit") long limit,@Param("offset") long offset);
    @Select("SELECT * FROM guide_comments WHERE id=#{id}")
    GuideComment find(long id);
    @Select("SELECT * FROM guide_comments WHERE id=#{id} FOR UPDATE")
    GuideComment lock(long id);

    String ADMIN_FILTER="""
        WHERE c.is_deleted=0
        <if test="keyword != null">AND c.content LIKE #{keyword} ESCAPE '!'</if>
        <if test="status != null">AND c.status=#{status}</if>
        """;
    @Select("<script>SELECT COUNT(*) FROM guide_comments c "+ADMIN_FILTER+"</script>")
    long countAdmin(@Param("keyword") String keyword,@Param("status") String status);
    @Select("<script>SELECT c.*,g.title AS guide_title FROM guide_comments c LEFT JOIN guides g ON g.id=c.guide_id "+ADMIN_FILTER+" ORDER BY c.created_at DESC,c.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<AdminCommentRow> adminPage(@Param("keyword") String keyword,@Param("status") String status,@Param("limit") long limit,@Param("offset") long offset);
}
