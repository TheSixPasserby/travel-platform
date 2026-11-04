package com.example.travelplatform.administration.mapper;
import com.example.travelplatform.user.entity.User;
import com.example.travelplatform.administration.vo.AdminSummaryVO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdministrationMapper {
    // The administration query never selects a password hash or session credential.
    String USER_FIELDS="id,username,nickname,avatar_url,role,status,is_deleted,created_at,updated_at";
    String USER_FILTER="""
        WHERE is_deleted=0
        <if test="keyword != null">AND (username LIKE #{keyword} ESCAPE '!' OR nickname LIKE #{keyword} ESCAPE '!')</if>
        <if test="status != null">AND status=#{status}</if>
        <if test="role != null">AND role=#{role}</if>
        """;
    @Select("<script>SELECT COUNT(*) FROM users "+USER_FILTER+"</script>")
    long countUsers(@Param("keyword") String keyword,@Param("status") String status,@Param("role") String role);
    @Select("<script>SELECT "+USER_FIELDS+" FROM users "+USER_FILTER+" ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<User> users(@Param("keyword") String keyword,@Param("status") String status,@Param("role") String role,@Param("limit") long limit,@Param("offset") long offset);
    @Select("SELECT "+USER_FIELDS+" FROM users WHERE role='ADMIN' ORDER BY id FOR UPDATE")
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    List<User> lockAdministrators();
    @Select("SELECT "+USER_FIELDS+" FROM users WHERE id=#{id} FOR UPDATE")
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    User lockUser(long id);
    @Update("UPDATE users SET status=#{status},updated_at=#{time} WHERE id=#{id} AND is_deleted=0")
    int userStatus(@Param("id") long id,@Param("status") String status,@Param("time") LocalDateTime time);

    @Select("""
        SELECT (SELECT COUNT(*) FROM users WHERE is_deleted=0) AS user_count,
               (SELECT COUNT(*) FROM guides WHERE is_deleted=0) AS guide_count,
               (SELECT COUNT(*) FROM destinations WHERE is_deleted=0) AS destination_count,
               (SELECT COUNT(*) FROM guide_comments WHERE is_deleted=0) AS comment_count
        """)
    AdminSummaryVO summary();
}
