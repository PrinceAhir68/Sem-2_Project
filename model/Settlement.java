package com.expensesplitter.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Settlement {
    private int settlementId;
    private int groupId;
    private int fromUser;
    private int toUser;
    private BigDecimal amount;
    private boolean settled;
    private LocalDateTime settledAt;
    private LocalDateTime createdAt;
    private String fromUserName;
    private String toUserName;

    public Settlement() {}

    public Settlement(int groupId, int fromUser, int toUser, BigDecimal amount) {
        this.groupId = groupId;
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.amount = amount;
    }

    public int getSettlementId() { return settlementId; }
    public void setSettlementId(int settlementId) { this.settlementId = settlementId; }

    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }

    public int getFromUser() { return fromUser; }
    public void setFromUser(int fromUser) { this.fromUser = fromUser; }

    public int getToUser() { return toUser; }
    public void setToUser(int toUser) { this.toUser = toUser; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public boolean isSettled() { return settled; }
    public void setSettled(boolean settled) { this.settled = settled; }

    public LocalDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(LocalDateTime settledAt) { this.settledAt = settledAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getFromUserName() { return fromUserName; }
    public void setFromUserName(String fromUserName) { this.fromUserName = fromUserName; }

    public String getToUserName() { return toUserName; }
    public void setToUserName(String toUserName) { this.toUserName = toUserName; }
}
