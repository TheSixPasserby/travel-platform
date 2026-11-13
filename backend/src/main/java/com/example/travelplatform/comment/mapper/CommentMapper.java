package com.example.travelplatform.comment.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.travelplatform.comment.entity.GuideComment;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<GuideComment> {
    @Select("SELECT COUNT(*) FROM guide_comments WHERE guide_id=#{id} AND status='NORMAL' AND is_deleted=0 AND root_id IS NULL")
    long countPublic(long id);
    @Select("SELECT * FROM guide_comments WHERE guide_id=#{id} AND status='NORMAL' AND is_deleted=0 AND root_id IS NULL ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
    List<GuideComment> page(@Param("id") long id,@Param("limit") long limit,@Param("offset") long offset);
    @Select("SELECT * FROM guide_comments WHERE id=#{id}")
    GuideComment find(long id);
    @Select("SELECT * FROM guide_comments WHERE id=#{id} FOR UPDATE")
    GuideComment lock(long id);

    @Select("SELECT * FROM users WHERE id=#{id} FOR SHARE")
    com.example.travelplatform.user.entity.User lockAuthor(long id);
    @Insert("INSERT INTO comment_write_locks(user_id) VALUES (#{id}) ON DUPLICATE KEY UPDATE user_id=user_id")
    void serializeWrites(long id);
    @Select("SELECT COUNT(*) FROM guide_comments WHERE user_id=#{user} AND created_at >= #{since}")
    long recent(@Param("user") long user, @Param("since") java.time.LocalDateTime since);
    @Select("SELECT COUNT(*) FROM guide_comments WHERE root_id=#{id} AND status='NORMAL' AND is_deleted=0")
    long countReplies(long id);
    @Select("SELECT * FROM guide_comments WHERE root_id=#{id} AND status='NORMAL' AND is_deleted=0 ORDER BY created_at,id LIMIT #{limit} OFFSET #{offset}")
    List<GuideComment> replies(@Param("id") long id,@Param("limit") long limit,@Param("offset") long offset);
    @Insert("INSERT INTO comment_mentions(comment_id,user_id) VALUES (#{comment},#{user})")
    void mention(@Param("comment") long comment,@Param("user") long user);
    @Select("SELECT u.* FROM users u JOIN comment_mentions m ON m.user_id=u.id WHERE m.comment_id=#{id} AND u.status='ACTIVE' AND u.is_deleted=0 ORDER BY u.id")
    List<com.example.travelplatform.user.entity.User> mentions(long id);
    @Select("SELECT COUNT(*) FROM guide_comments c JOIN guide_comments target ON target.id=#{id} WHERE c.guide_id=target.guide_id AND c.root_id IS NULL AND c.status='NORMAL' AND c.is_deleted=0 AND (c.created_at>target.created_at OR (c.created_at=target.created_at AND c.id>target.id))")
    long newerRoots(long id);
    @Select("SELECT COUNT(*) FROM guide_comments c JOIN guide_comments target ON target.id=#{id} WHERE c.root_id=target.root_id AND c.status='NORMAL' AND c.is_deleted=0 AND (c.created_at<target.created_at OR (c.created_at=target.created_at AND c.id<target.id))")
    long earlierReplies(long id);

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
