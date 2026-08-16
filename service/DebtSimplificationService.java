package com.expensesplitter.service;

import com.expensesplitter.algorithm.DebtSimplifier;
import com.expensesplitter.algorithm.SettlementSuggestion;
import com.expensesplitter.dao.ActivityLogDAO;
import com.expensesplitter.dao.GroupMemberDAO;
import com.expensesplitter.dao.SettlementDAO;
import com.expensesplitter.model.Balance;
import com.expensesplitter.model.Settlement;
import com.expensesplitter.utility.SessionManager;
import com.expensesplitter.utility.TransactionHelper;

import java.util.List;

/**
 * Debt simplification with a single JDBC transaction:
 * clear pending settlements → insert new suggestions → history log → COMMIT / ROLLBACK.
 */
public class DebtSimplificationService {

    private final BalanceService balanceService = new BalanceService();
    private final SettlementDAO settlementDAO = new SettlementDAO();
    private final DebtSimplifier simplifier = new DebtSimplifier();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAO();
    private final GroupMemberDAO memberDAO = new GroupMemberDAO();

    /**
     * Runs the greedy debt algorithm and persists results in one transaction.
     * Call only after the user confirmed the action in the menu.
     */
    public List<SettlementSuggestion> simplifyAndSave(int groupId) throws Exception {
        if (!SessionManager.isLoggedIn()) {
            throw new IllegalStateException("You must be logged in to simplify debts.");
        }
        int actorId = SessionManager.getCurrentUserId();
        if (!memberDAO.isMember(groupId, actorId)) {
            throw new IllegalStateException("You are not a member of this group.");
        }

        // Read balances (outside write txn is fine; recalculate is done by caller when needed)
        List<Balance> balances = balanceService.getBalances(groupId);
        List<SettlementSuggestion> suggestions = simplifier.simplify(balances);

        try {
            TransactionHelper.run(conn -> {
                // 1) Remove old pending settlements for this group
                settlementDAO.clearPendingForGroup(conn, groupId);

                // 2) Insert new simplified settlements
                for (SettlementSuggestion s : suggestions) {
                    Settlement settlement = new Settlement(
                            groupId, s.getFromUserId(), s.getToUserId(), s.getAmount());
                    settlementDAO.create(conn, settlement);
                }

                // 3) History log (same transaction)
                activityLogDAO.log(conn, actorId, groupId,
                        "DEBT_SIMPLIFIED",
                        suggestions.size() + " settlement(s) generated");
            });
        } catch (Exception e) {
            throw new Exception("Debt simplification failed and was rolled back: " + e.getMessage(), e);
        }

        return suggestions;
    }

    public List<SettlementSuggestion> preview(int groupId) throws Exception {
        if (!SessionManager.isLoggedIn()) {
            throw new IllegalStateException("You must be logged in.");
        }
        if (!memberDAO.isMember(groupId, SessionManager.getCurrentUserId())) {
            throw new IllegalStateException("You are not a member of this group.");
        }
        List<Balance> balances = balanceService.getBalances(groupId);
        return simplifier.simplify(balances);
    }
}
