package com.expensesplitter.service;

import com.expensesplitter.dao.ActivityLogDAO;
import com.expensesplitter.dao.GroupMemberDAO;
import com.expensesplitter.dao.NotificationDAO;
import com.expensesplitter.dao.SettlementDAO;
import com.expensesplitter.model.Settlement;
import com.expensesplitter.utility.SessionManager;
import com.expensesplitter.utility.TransactionHelper;

import java.util.List;
import java.util.Optional;

/**
 * Settlement operations with authentication and single-transaction writes.
 */
public class SettlementService {

    private final SettlementDAO settlementDAO = new SettlementDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAO();
    private final GroupMemberDAO memberDAO = new GroupMemberDAO();
    private final BalanceService balanceService = new BalanceService();

    public List<Settlement> getPending(int groupId) throws Exception {
        requireGroupMember(groupId);
        return settlementDAO.findPendingByGroupId(groupId);
    }

    public List<Settlement> getHistory(int groupId) throws Exception {
        requireGroupMember(groupId);
        return settlementDAO.findHistoryByGroupId(groupId);
    }

    /**
     * Marks a settlement as paid inside one transaction:
     * UPDATE settlement + notification + history → COMMIT, or ROLLBACK on failure.
     */
    public String markAsPaid(int settlementId) throws Exception {
        if (!SessionManager.isLoggedIn()) {
            return "You must be logged in to mark a settlement as paid.";
        }
        int actorId = SessionManager.getCurrentUserId();

        Optional<Settlement> opt = settlementDAO.findById(settlementId);
        if (opt.isEmpty()) {
            return "Settlement not found.";
        }

        Settlement s = opt.get();
        if (s.isSettled()) {
            return "Already marked as paid.";
        }
        if (!memberDAO.isMember(s.getGroupId(), actorId)) {
            return "You are not a member of this group.";
        }
        if (s.getFromUser() != actorId) {
            activityLogDAO.log(
                    actorId,
                    s.getGroupId(),
                    "SETTLEMENT_PAY_BLOCKED",
                    s.getFromUserName() + " blocked unauthorized settlement payment attempt by "
                            + actorId + " on settlement #" + settlementId + ".",
                    s.getFromUser()
            );
            return "You can only settle payments that you owe.";
        }

        try {
            TransactionHelper.run(conn -> {
                boolean updated = settlementDAO.markAsSettled(conn, settlementId);
                if (!updated) {
                    throw new Exception("Settlement update affected 0 rows.");
                }

                // Keep stored balances in sync with the payment before commit.
                balanceService.recalculateBalances(conn, s.getGroupId());

                notificationDAO.create(conn, s.getToUser(),
                        s.getFromUserName() + " paid you ₹" + s.getAmount());

                activityLogDAO.log(conn, actorId, s.getGroupId(),
                        "SETTLEMENT_PAID",
                        s.getFromUserName() + " → " + s.getToUserName() + " ₹" + s.getAmount());
            });
            return null;
        } catch (Exception e) {
            throw new Exception("Mark settlement paid failed and was rolled back: " + e.getMessage(), e);
        }
    }

    private void requireGroupMember(int groupId) throws Exception {
        if (!SessionManager.isLoggedIn()) {
            throw new IllegalStateException("You must be logged in.");
        }
        if (!memberDAO.isMember(groupId, SessionManager.getCurrentUserId())) {
            throw new IllegalStateException("You are not a member of this group.");
        }
    }
}
