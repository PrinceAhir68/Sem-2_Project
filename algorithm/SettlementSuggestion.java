package com.expensesplitter.algorithm;

import java.math.BigDecimal;

public class SettlementSuggestion {
    private final int fromUserId;
    private final String fromUserName;
    private final int toUserId;
    private final String toUserName;
    private final BigDecimal amount;

    public SettlementSuggestion(int fromUserId, String fromUserName,
                                int toUserId, String toUserName, BigDecimal amount) {
        this.fromUserId = fromUserId;
        this.fromUserName = fromUserName;
        this.toUserId = toUserId;
        this.toUserName = toUserName;
        this.amount = amount;
    }

    public int getFromUserId() { return fromUserId; }
    public String getFromUserName() { return fromUserName; }
    public int getToUserId() { return toUserId; }
    public String getToUserName() { return toUserName; }
    public BigDecimal getAmount() { return amount; }

    @Override
    public String toString() {
        return fromUserName + " pays " + toUserName + " ₹" + amount;
    }
}
