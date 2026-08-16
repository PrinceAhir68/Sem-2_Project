package com.expensesplitter.model;

import java.util.List;

public class PendingCustomSplitView {
    private final Expense expense;
    private final List<ExpenseSplit> splits;
    private final ExpenseSplit nextPendingSplit;
    private final int pendingParticipantCount;

    public PendingCustomSplitView(Expense expense, List<ExpenseSplit> splits,
                                  ExpenseSplit nextPendingSplit, int pendingParticipantCount) {
        this.expense = expense;
        this.splits = splits;
        this.nextPendingSplit = nextPendingSplit;
        this.pendingParticipantCount = pendingParticipantCount;
    }

    public Expense getExpense() {
        return expense;
    }

    public List<ExpenseSplit> getSplits() {
        return splits;
    }

    public ExpenseSplit getNextPendingSplit() {
        return nextPendingSplit;
    }

    public int getPendingParticipantCount() {
        return pendingParticipantCount;
    }
}
