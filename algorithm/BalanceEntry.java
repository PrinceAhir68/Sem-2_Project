package com.expensesplitter.algorithm;

import java.math.BigDecimal;

public class BalanceEntry implements Comparable<BalanceEntry> {
    private final int userId;
    private final String userName;
    private BigDecimal amount;

    public BalanceEntry(int userId, String userName, BigDecimal amount) {
        this.userId = userId;
        this.userName = userName;
        this.amount = amount;
    }

    public int getUserId() { return userId; }
    public String getUserName() { return userName; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    @Override
    public int compareTo(BalanceEntry other) {
        return other.amount.compareTo(this.amount);
    }
}
