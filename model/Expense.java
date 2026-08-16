package com.expensesplitter.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Expense {
    private int expenseId;
    private int groupId;
    private int paidBy;
    private int createdBy;
    private BigDecimal amount;
    private String description;
    private String category;
    private String splitType;
    private boolean pending;
    private BigDecimal remainingAmount;
    private LocalDateTime expenseDate;
    private String payerName;

    public Expense() {}

    public int getExpenseId() { return expenseId; }
    public void setExpenseId(int expenseId) { this.expenseId = expenseId; }

    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }

    public int getPaidBy() { return paidBy; }
    public void setPaidBy(int paidBy) { this.paidBy = paidBy; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSplitType() { return splitType; }
    public void setSplitType(String splitType) { this.splitType = splitType; }

    public boolean isPending() { return pending; }
    public void setPending(boolean pending) { this.pending = pending; }

    public BigDecimal getRemainingAmount() { return remainingAmount; }
    public void setRemainingAmount(BigDecimal remainingAmount) { this.remainingAmount = remainingAmount; }

    public LocalDateTime getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDateTime expenseDate) { this.expenseDate = expenseDate; }

    public String getPayerName() { return payerName; }
    public void setPayerName(String payerName) { this.payerName = payerName; }

    /**
     * The schema stores the date of an expense in {@code expense_date}.
     * Export/report views use this accessor as their creation timestamp.
     */
    public LocalDateTime getCreatedAt() {
        return expenseDate;
    }
}

