package com.example.travelplatform.comment.mapper;
import com.example.travelplatform.comment.entity.GuideComment;
public class AdminCommentRow extends GuideComment {
    private String guideTitle;
    public String getGuideTitle(){return guideTitle;}
    public void setGuideTitle(String value){guideTitle=value;}
}
