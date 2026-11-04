package com.example.travelplatform.guide.mapper;
import com.example.travelplatform.guide.entity.Guide;
public class GuideRow extends Guide {
    private long likeCount;
    public long getLikeCount() { return likeCount; }
    public void setLikeCount(long v) { this.likeCount=v; }
    private long favoriteCount;
    public long getFavoriteCount() { return favoriteCount; }
    public void setFavoriteCount(long v) { this.favoriteCount=v; }
    private long commentCount;
    public long getCommentCount() { return commentCount; }
    public void setCommentCount(long v) { this.commentCount=v; }
}
