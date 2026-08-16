package com.expensesplitter.model;

import java.math.BigDecimal;

public class ExpenseSplit {
    private int splitId;
    private int expenseId;
    private int userId;
    private BigDecimal shareAmount;
    private int contributionOrder;
    private String contributionStatus;
    private String userName;

    public ExpenseSplit() {}

    public ExpenseSplit(int expenseId, int userId, BigDecimal shareAmount) {
        this.expenseId = expenseId;
        this.userId = userId;
        this.shareAmount = shareAmount;
    }

    public int getSplitId() { return splitId; }
    public void setSplitId(int splitId) { this.splitId = splitId; }

    public int getExpenseId() { return expenseId; }
    public void setExpenseId(int expenseId) { this.expenseId = expenseId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public BigDecimal getShareAmount() { return shareAmount; }
    public void setShareAmount(BigDecimal shareAmount) { this.shareAmount = shareAmount; }

    public int getContributionOrder() { return contributionOrder; }
    public void setContributionOrder(int contributionOrder) { this.contributionOrder = contributionOrder; }

    public String getContributionStatus() { return contributionStatus; }
    public void setContributionStatus(String contributionStatus) { this.contributionStatus = contributionStatus; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
}
