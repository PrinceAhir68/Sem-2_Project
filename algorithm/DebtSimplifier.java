package com.expensesplitter.algorithm;

import com.expensesplitter.model.Balance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Greedy debt simplification using two Max Heaps (Priority Queues).
 * Repeatedly matches the largest creditor with the largest debtor.
 */
public class
DebtSimplifier {

    private static final BigDecimal THRESHOLD = new BigDecimal("0.01");

    public List<SettlementSuggestion> simplify(List<Balance> balances) {
        PriorityQueue<BalanceEntry> creditors = new PriorityQueue<>();
        PriorityQueue<BalanceEntry> debtors = new PriorityQueue<>();

        for (Balance b : balances) {
            BigDecimal net = b.getNetBalance().setScale(2, RoundingMode.HALF_UP);
            if (net.compareTo(THRESHOLD) > 0) {
                creditors.offer(new BalanceEntry(b.getUserId(), b.getUserName(), net));
            } else if (net.compareTo(THRESHOLD.negate()) < 0) {
                debtors.offer(new BalanceEntry(b.getUserId(), b.getUserName(), net.abs()));
            }
        }

        List<SettlementSuggestion> suggestions = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            BalanceEntry creditor = creditors.poll();
            BalanceEntry debtor = debtors.poll();

            BigDecimal settleAmount = creditor.getAmount().min(debtor.getAmount())
                    .setScale(2, RoundingMode.HALF_UP);

            suggestions.add(new SettlementSuggestion(
                    debtor.getUserId(), debtor.getUserName(),
                    creditor.getUserId(), creditor.getUserName(),
                    settleAmount
            ));

            BigDecimal creditorRemaining = creditor.getAmount().subtract(settleAmount);
            BigDecimal debtorRemaining = debtor.getAmount().subtract(settleAmount);

            if (creditorRemaining.compareTo(THRESHOLD) > 0) {
                creditor.setAmount(creditorRemaining);
                creditors.offer(creditor);
            }
            if (debtorRemaining.compareTo(THRESHOLD) > 0) {
                debtor.setAmount(debtorRemaining);
                debtors.offer(debtor);
            }
        }

        return suggestions;
    }
}
