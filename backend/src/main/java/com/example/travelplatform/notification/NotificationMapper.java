package com.example.travelplatform.notification;

import com.example.travelplatform.user.entity.User;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NotificationMapper {
    String VISIBLE = """
        FROM notifications n JOIN guide_comments c ON c.id=n.comment_id
        JOIN guides g ON g.id=n.guide_id JOIN users a ON a.id=n.actor_id
        JOIN users owner ON owner.id=g.author_id
        WHERE n.recipient_id=#{user} AND c.status='NORMAL' AND c.is_deleted=0
          AND g.status='PUBLISHED' AND g.is_deleted=0
          AND a.status='ACTIVE' AND a.is_deleted=0 AND owner.status='ACTIVE' AND owner.is_deleted=0
          AND (c.root_id IS NULL OR EXISTS (SELECT 1 FROM guide_comments root WHERE root.id=c.root_id AND root.status='NORMAL' AND root.is_deleted=0))
        """;
    @Insert("INSERT INTO notifications(recipient_id,actor_id,guide_id,comment_id,type,created_at) VALUES (#{recipient},#{actor},#{guide},#{comment},#{type},#{time})")
    void insert(@Param("recipient") long recipient,@Param("actor") long actor,@Param("guide") long guide,
                @Param("comment") long comment,@Param("type") String type,@Param("time") LocalDateTime time);
    @Select("<script>SELECT COUNT(*) "+VISIBLE+"<if test='unread'> AND n.read_at IS NULL</if></script>")
    long count(@Param("user") long user,@Param("unread") boolean unread);
    @Select("<script>SELECT n.id,n.actor_id,n.guide_id,n.comment_id,n.type,c.content,n.created_at,n.read_at "+VISIBLE+"<if test='unread'> AND n.read_at IS NULL</if> ORDER BY n.created_at DESC,n.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<NotificationRow> page(@Param("user") long user,@Param("unread") boolean unread,@Param("limit") long limit,@Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM notifications WHERE id=#{id} AND recipient_id=#{user}")
    long owned(@Param("id") long id,@Param("user") long user);
    @Update("UPDATE notifications SET read_at=COALESCE(read_at,#{time}) WHERE id=#{id} AND recipient_id=#{user}")
    void mark(@Param("id") long id,@Param("user") long user,@Param("time") LocalDateTime time);
    @Update("UPDATE notifications SET read_at=#{time} WHERE recipient_id=#{user} AND read_at IS NULL")
    void markAll(@Param("user") long user,@Param("time") LocalDateTime time);
    @Select("SELECT * FROM users WHERE status='ACTIVE' AND is_deleted=0 AND (username LIKE #{pattern} ESCAPE '!' OR nickname LIKE #{pattern} ESCAPE '!') ORDER BY username,id LIMIT 8")
    List<User> suggestions(@Param("pattern") String pattern);
}
