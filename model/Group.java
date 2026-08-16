package com.expensesplitter.model;

import java.time.LocalDateTime;

public class Group {
    private int groupId;
    private String groupName;
    private int createdBy;
    private LocalDateTime createdAt;
    private String creatorName;

    public Group() {}

    public Group(String groupName, int createdBy) {
        this.groupName = groupName;
        this.createdBy = createdBy;
    }

    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
}
