package com.tgaws.business.warn.entity;

import java.time.LocalDateTime;

/**
 * 处置反馈实体（warn_dispose_feedback 行，FR-403 分次反馈）。
 */
public class DisposeFeedbackEntity {

    private Long id;
    private Long taskId;
    private Long feedbackUserId;
    private String content;
    private String images;
    private LocalDateTime feedbackTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getFeedbackUserId() { return feedbackUserId; }
    public void setFeedbackUserId(Long feedbackUserId) { this.feedbackUserId = feedbackUserId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }
    public LocalDateTime getFeedbackTime() { return feedbackTime; }
    public void setFeedbackTime(LocalDateTime feedbackTime) { this.feedbackTime = feedbackTime; }
}
