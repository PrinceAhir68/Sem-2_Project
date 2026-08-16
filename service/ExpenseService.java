package com.expensesplitter.service;

import com.expensesplitter.dao.*;
import com.expensesplitter.database.DBConnection;
import com.expensesplitter.model.Expense;
import com.expensesplitter.model.ExpenseSplit;
import com.expensesplitter.model.PendingCustomSplitView;
import com.expensesplitter.model.User;
import com.expensesplitter.utility.InputValidator;
import com.expensesplitter.utility.SessionManager;
import com.expensesplitter.utility.TransactionHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Expense business logic with professional JDBC transactions.
 *
 * <pre>
 *   setAutoCommit(false)
 *        │
 *        ▼
 *   insert/update expense + splits + balances + history + notifications
 *        │
 *        ├── success → commit()     (all permanent)
 *        └── any error → rollback() (all discarded)
 *        │
 *        finally → restore autoCommit / close connection
 * </pre>
 */
public class ExpenseService {

    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final ExpenseSplitDAO splitDAO = new ExpenseSplitDAO();
    private final GroupMemberDAO memberDAO = new GroupMemberDAO();
    private final GroupDAO groupDAO = new GroupDAO();
    private final BalanceService balanceService = new BalanceService();
    private final SettlementDAO settlementDAO = new SettlementDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAO();

    // =========================================================================
    // ADD EXPENSE — single transaction → COMMIT or ROLLBACK
    // =========================================================================

    /**
     * Adds an expense inside one DB transaction.
     * Call this only after the user confirmed: "Are you sure you want to add this expense?"
     *
     * @return {@code null} on success, or a user-facing validation / auth error message
     */
    public String addExpense(int groupId, int paidBy, BigDecimal amount, String description,
                             String category, String splitType, Map<Integer, BigDecimal> shares)
            throws Exception {

        // --- Authentication & authorization (before opening a transaction) ---
        if (!SessionManager.isLoggedIn()) {
            return "You must be logged in to add an expense.";
        }
        int actorId = SessionManager.getCurrentUserId();
        if (!memberDAO.isMember(groupId, actorId)) {
            return "You are not a member of this group.";
        }
        if (!InputValidator.isValidDescription(description)) {
            return InputValidator.descriptionRuleHint();
        }
        if (shares == null || shares.isEmpty()) {
            return "No members selected for split.";
        }

        BigDecimal totalShares = shares.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalShares.subtract(amount).abs().compareTo(new BigDecimal("0.01")) > 0) {
            return "Split amounts must sum to ₹" + amount + " (got ₹" + totalShares + ").";
        }
        if (!memberDAO.isMember(groupId, paidBy)) {
            return "Payer must be a member of this group.";
        }
        for (Integer memberId : shares.keySet()) {
            if (!memberDAO.isMember(groupId, memberId)) {
                return "Split member is not in this group (user id " + memberId + ").";
            }
        }

        Expense expense = new Expense();
        expense.setGroupId(groupId);
        expense.setPaidBy(paidBy);
        expense.setCreatedBy(actorId);
        expense.setAmount(amount);
        expense.setDescription(description.trim());
        expense.setCategory(category);
        expense.setSplitType(splitType);
        expense.setPending(false);
        expense.setRemainingAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        Connection conn = null;
        try {
            // 1) Open connection and start transaction
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // 2) Insert expense
            int expenseId = expenseDAO.create(conn, expense);

            // 3) Insert expense members (splits)
            List<ExpenseSplit> splits = new ArrayList<>();
            for (Map.Entry<Integer, BigDecimal> entry : shares.entrySet()) {
                ExpenseSplit split = new ExpenseSplit(expenseId, entry.getKey(), entry.getValue());
                split.setContributionStatus("finalized");
                splits.add(split);
            }
            splitDAO.createBatch(conn, splits);

            // 4) Recalculate balances (same connection)
            balanceService.recalculateBalances(conn, groupId);

            // 5) Clear stale pending settlements (debt simplification data)
            settlementDAO.clearPendingForGroup(conn, groupId);

            // 6) Notifications
            List<User> members = memberDAO.getMembers(conn, groupId);
            String payerName = members.stream()
                    .filter(m -> m.getUserId() == paidBy)
                    .map(User::getName).findFirst().orElse("Someone");
            for (User member : members) {
                if (member.getUserId() != paidBy) {
                    notificationDAO.create(conn, member.getUserId(),
                            payerName + " added expense: " + description.trim() + " (₹" + amount + ")");
                }
            }

            // 7) History / activity log
            activityLogDAO.log(conn, actorId, groupId,
                    "EXPENSE_ADDED", description.trim() + " — ₹" + amount);

            // 8) All steps succeeded → permanent save
            conn.commit();
            return null;

        } catch (Exception e) {
            // Any failure → undo everything in this transaction
            safeRollback(conn);
            throw new Exception("Add expense failed and was rolled back: " + e.getMessage(), e);
        } finally {
            safeClose(conn);
        }
    }

    // =========================================================================
    // UPDATE EXPENSE — single transaction → COMMIT or ROLLBACK
    // =========================================================================

    /**
     * Updates an existing expense inside one DB transaction.
     * Call this only after the user confirmed: "Do you want to save the changes?"
     *
     * @return {@code null} on success, or a user-facing error message
     */
    public String updateExpense(int expenseId, int paidBy, BigDecimal amount, String description,
                                String category, String splitType, Map<Integer, BigDecimal> shares)
            throws Exception {

        if (!SessionManager.isLoggedIn()) {
            return "You must be logged in to edit an expense.";
        }
        int actorId = SessionManager.getCurrentUserId();

        Optional<Expense> existingOpt = expenseDAO.findById(expenseId);
        if (existingOpt.isEmpty()) {
            return "Expense not found.";
        }
        Expense existing = existingOpt.get();
        int groupId = existing.getGroupId();

        if (!memberDAO.isMember(groupId, actorId)) {
            return "You are not a member of this group.";
        }
        if (!InputValidator.isValidDescription(description)) {
            return InputValidator.descriptionRuleHint();
        }
        if (shares == null || shares.isEmpty()) {
            return "No members selected for split.";
        }

        BigDecimal totalShares = shares.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalShares.subtract(amount).abs().compareTo(new BigDecimal("0.01")) > 0) {
            return "Split amounts must sum to ₹" + amount + " (got ₹" + totalShares + ").";
        }
        if (!memberDAO.isMember(groupId, paidBy)) {
            return "Payer must be a member of this group.";
        }
        for (Integer memberId : shares.keySet()) {
            if (!memberDAO.isMember(groupId, memberId)) {
                return "Split member is not in this group (user id " + memberId + ").";
            }
        }

        Expense expense = new Expense();
        expense.setExpenseId(expenseId);
        expense.setGroupId(groupId);
        expense.setPaidBy(paidBy);
        expense.setCreatedBy(existing.getCreatedBy() == 0 ? actorId : existing.getCreatedBy());
        expense.setAmount(amount);
        expense.setDescription(description.trim());
        expense.setCategory(category);
        expense.setSplitType(splitType);
        expense.setPending(false);
        expense.setRemainingAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // 1) Update expense row
            boolean updated = expenseDAO.update(conn, expense);
            if (!updated) {
                throw new SQLException("Expense update affected 0 rows.");
            }

            // 2) Replace expense members (delete old splits, insert new)
            splitDAO.deleteByExpenseId(conn, expenseId);
            List<ExpenseSplit> splits = new ArrayList<>();
            for (Map.Entry<Integer, BigDecimal> entry : shares.entrySet()) {
                ExpenseSplit split = new ExpenseSplit(expenseId, entry.getKey(), entry.getValue());
                split.setContributionStatus("finalized");
                splits.add(split);
            }
            splitDAO.createBatch(conn, splits);

            // 3) Recalculate balances
            balanceService.recalculateBalances(conn, groupId);

            // 4) Invalidate pending debt-simplification settlements
            settlementDAO.clearPendingForGroup(conn, groupId);

            // 5) History: "Expense Updated"
            activityLogDAO.log(conn, actorId, groupId,
                    "EXPENSE_UPDATED",
                    "Expense Updated: " + description.trim() + " — ₹" + amount);

            // 6) Notify other members
            List<User> members = memberDAO.getMembers(conn, groupId);
            String actorName = members.stream()
                    .filter(m -> m.getUserId() == actorId)
                    .map(User::getName).findFirst().orElse("A member");
            for (User member : members) {
                if (member.getUserId() != actorId) {
                    notificationDAO.create(conn, member.getUserId(),
                            actorName + " updated expense: " + description.trim() + " (₹" + amount + ")");
                }
            }

            conn.commit();
            return null;

        } catch (Exception e) {
            safeRollback(conn);
            throw new Exception("Update expense failed and was rolled back: " + e.getMessage(), e);
        } finally {
            safeClose(conn);
        }
    }

    // =========================================================================
    // DELETE EXPENSE — single transaction
    // =========================================================================

    public String deleteExpense(int expenseId) throws Exception {
        if (!SessionManager.isLoggedIn()) {
            return "You must be logged in to delete an expense.";
        }
        int actorId = SessionManager.getCurrentUserId();

        Optional<Expense> expenseOpt = expenseDAO.findById(expenseId);
        if (expenseOpt.isEmpty()) {
            return "Expense not found.";
        }

        int groupId = expenseOpt.get().getGroupId();
        if (!memberDAO.isMember(groupId, actorId)) {
            return "You are not a member of this group.";
        }

        String description = expenseOpt.get().getDescription();

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // Splits cascade via FK ON DELETE CASCADE; still explicit for clarity on some DBs
            splitDAO.deleteByExpenseId(conn, expenseId);
            expenseDAO.delete(conn, expenseId);

            balanceService.recalculateBalances(conn, groupId);
            settlementDAO.clearPendingForGroup(conn, groupId);

            activityLogDAO.log(conn, actorId, groupId,
                    "EXPENSE_DELETED",
                    "Expense deleted: " + description + " (ID " + expenseId + ")");

            conn.commit();
            return null;

        } catch (Exception e) {
            safeRollback(conn);
            throw new Exception("Delete expense failed and was rolled back: " + e.getMessage(), e);
        } finally {
            safeClose(conn);
        }
    }

    // =========================================================================
    // READS
    // =========================================================================

    public List<Expense> getExpenses(int groupId) throws Exception {
        if (!SessionManager.isLoggedIn()) {
            throw new IllegalStateException("You must be logged in.");
        }
        if (!memberDAO.isMember(groupId, SessionManager.getCurrentUserId())) {
            throw new IllegalStateException("You are not a member of this group.");
        }
        return expenseDAO.findByGroupId(groupId);
    }

    public Optional<Expense> getExpense(int expenseId) throws Exception {
        if (!SessionManager.isLoggedIn()) {
            throw new IllegalStateException("You must be logged in.");
        }
        Optional<Expense> expense = expenseDAO.findById(expenseId);
        if (expense.isPresent()) {
            int groupId = expense.get().getGroupId();
            if (!memberDAO.isMember(groupId, SessionManager.getCurrentUserId())) {
                throw new IllegalStateException("You are not a member of this group.");
            }
        }
        return expense;
    }

    public List<ExpenseSplit> getSplits(int expenseId) throws Exception {
        if (!SessionManager.isLoggedIn()) {
            throw new IllegalStateException("You must be logged in.");
        }
        Optional<Expense> expense = expenseDAO.findById(expenseId);
        if (expense.isEmpty()) {
            return List.of();
        }
        if (!memberDAO.isMember(expense.get().getGroupId(), SessionManager.getCurrentUserId())) {
            throw new IllegalStateException("You are not a member of this group.");
        }
        return splitDAO.findByExpenseId(expenseId);
    }

    public Map<Integer, BigDecimal> buildEqualSplit(BigDecimal amount, List<Integer> memberIds) {
        int count = memberIds.size();
        BigDecimal share = amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        BigDecimal remainder = amount.subtract(share.multiply(BigDecimal.valueOf(count)));

        Map<Integer, BigDecimal> shares = new java.util.LinkedHashMap<>();
        for (int i = 0; i < memberIds.size(); i++) {
            BigDecimal s = share;
            if (i == 0) {
                s = s.add(remainder);
            }
            shares.put(memberIds.get(i), s);
        }
        return shares;
    }

    public String addDistributedCustomExpense(int groupId, int paidBy, BigDecimal amount, String description,
                                              String category, List<Integer> participantIds, BigDecimal creatorShare)
            throws Exception {
        if (!SessionManager.isLoggedIn()) {
            return "You must be logged in to add an expense.";
        }
        int actorId = SessionManager.getCurrentUserId();
        if (!memberDAO.isMember(groupId, actorId)) {
            return "You are not a member of this group.";
        }
        if (!memberDAO.isMember(groupId, paidBy)) {
            return "Payer must be a member of this group.";
        }
        if (!InputValidator.isValidDescription(description)) {
            return InputValidator.descriptionRuleHint();
        }
        if (participantIds == null || participantIds.isEmpty()) {
            return "No members selected for split.";
        }
        for (Integer memberId : participantIds) {
            if (!memberDAO.isMember(groupId, memberId)) {
                return "Split member is not in this group (user id " + memberId + ").";
            }
        }
        if (!participantIds.contains(actorId)) {
            return "The expense creator must be included in the distributed custom split.";
        }
        creatorShare = creatorShare.setScale(2, RoundingMode.HALF_UP);
        if (creatorShare.compareTo(BigDecimal.ZERO) < 0) {
            return "Amount cannot be negative.";
        }
        if (creatorShare.compareTo(amount) > 0) {
            return "Amount should be less than ₹" + amount.setScale(2, RoundingMode.HALF_UP) + ".";
        }

        BigDecimal remainingAmount = amount.subtract(creatorShare).setScale(2, RoundingMode.HALF_UP);
        final BigDecimal creatorShareFinal = creatorShare;
        final BigDecimal remainingAmountFinal = remainingAmount;

        Expense expense = new Expense();
        expense.setGroupId(groupId);
        expense.setPaidBy(paidBy);
        expense.setCreatedBy(actorId);
        expense.setAmount(amount);
        expense.setDescription(description.trim());
        expense.setCategory(category);
        expense.setSplitType("custom");
        expense.setPending(remainingAmount.compareTo(BigDecimal.ZERO) > 0);
        expense.setRemainingAmount(remainingAmount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));

        try {
            TransactionHelper.run(conn -> {
                int expenseId = expenseDAO.create(conn, expense);

                List<ExpenseSplit> splits = new ArrayList<>();
                int order = 1;
                for (Integer memberId : participantIds) {
                    ExpenseSplit split = new ExpenseSplit();
                    split.setExpenseId(expenseId);
                    split.setUserId(memberId);
                    split.setContributionOrder(order++);
                    if (memberId == actorId) {
                        split.setShareAmount(creatorShareFinal);
                        split.setContributionStatus("finalized");
                    } else {
                        split.setShareAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                        split.setContributionStatus(remainingAmountFinal.compareTo(BigDecimal.ZERO) == 0 ? "removed" : "pending");
                    }
                    splits.add(split);
                }
                splitDAO.createBatch(conn, splits);

                List<User> members = memberDAO.getMembers(conn, groupId);
                String groupName = findGroupName(groupId);
                for (User member : members) {
                    if (participantIds.contains(member.getUserId()) && member.getUserId() != actorId) {
                        notificationDAO.create(conn, member.getUserId(),
                                "Add your custom split share for \"" + description.trim()
                                        + "\" in group " + groupName + ".");
                    }
                }

                activityLogDAO.log(conn, actorId, groupId,
                        "EXPENSE_ADDED", description.trim() + " — ₹" + amount);

                if (remainingAmountFinal.compareTo(BigDecimal.ZERO) == 0) {
                    expenseDAO.updatePendingState(conn, expenseId, false, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    balanceService.recalculateBalances(conn, groupId);
                    settlementDAO.clearPendingForGroup(conn, groupId);
                }
            });
            return null;
        } catch (Exception e) {
            throw new Exception("Add expense failed and was rolled back: " + e.getMessage(), e);
        }
    }

    public List<PendingCustomSplitView> getPendingCustomSplits(int groupId) throws Exception {
        if (!SessionManager.isLoggedIn()) {
            throw new IllegalStateException("You must be logged in.");
        }
        if (!memberDAO.isMember(groupId, SessionManager.getCurrentUserId())) {
            throw new IllegalStateException("You are not a member of this group.");
        }
        try (Connection conn = DBConnection.getConnection()) {
            List<PendingCustomSplitView> views = new ArrayList<>();
            for (Expense expense : expenseDAO.findPendingCustomByGroupId(conn, groupId)) {
                List<ExpenseSplit> splits = splitDAO.findByExpenseId(conn, expense.getExpenseId());
                views.add(toPendingView(expense, splits));
            }
            return views;
        }
    }

    public ShareSubmissionResult submitDistributedShare(int expenseId, BigDecimal shareAmount) throws Exception {
        if (!SessionManager.isLoggedIn()) {
            return new ShareSubmissionResult("You must be logged in to add your share.", null, List.of());
        }
        int actorId = SessionManager.getCurrentUserId();
        shareAmount = shareAmount.setScale(2, RoundingMode.HALF_UP);
        final BigDecimal submittedShare = shareAmount;

        try {
            return TransactionHelper.execute(conn -> {
                Expense expense = expenseDAO.findByIdForUpdate(conn, expenseId).orElse(null);
                if (expense == null) {
                    return new ShareSubmissionResult("Expense not found.", null, List.of());
                }
                if (!expense.isPending()) {
                    return new ShareSubmissionResult("This custom split is already finalized.", null, List.of());
                }
                if (!memberDAO.isMember(expense.getGroupId(), actorId)) {
                    return new ShareSubmissionResult("You are not a member of this group.", null, List.of());
                }

                List<ExpenseSplit> splits = splitDAO.findByExpenseId(conn, expenseId);
                ExpenseSplit actorSplit = splits.stream()
                        .filter(split -> split.getUserId() == actorId)
                        .findFirst()
                        .orElse(null);
                if (actorSplit == null) {
                    return new ShareSubmissionResult("You are not part of this expense.", null, List.of());
                }
                if (!"pending".equals(actorSplit.getContributionStatus())) {
                    return new ShareSubmissionResult("You have already completed your share for this expense.", null, List.of());
                }

                ExpenseSplit nextPending = findNextPendingSplit(splits);
                if (nextPending == null) {
                    finalizePendingExpense(conn, expense.getGroupId(), expenseId, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    return new ShareSubmissionResult("This custom split is already finalized.", null, List.of());
                }
                if (nextPending.getUserId() != actorId) {
                    return new ShareSubmissionResult("It is not your turn to contribute to this expense yet.", null, List.of());
                }

                BigDecimal remaining = expense.getRemainingAmount().setScale(2, RoundingMode.HALF_UP);
                if (remaining.compareTo(BigDecimal.ZERO) == 0) {
                    splitDAO.updateStatus(conn, expenseId, actorId, "removed");
                    finalizePendingExpense(conn, expense.getGroupId(), expenseId, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    return new ShareSubmissionResult(null,
                            actorSplit.getUserName() + " is removed from this expense", List.of());
                }
                boolean lastPendingContributor = splits.stream()
                        .filter(split -> split.getUserId() != actorId)
                        .noneMatch(split -> "pending".equals(split.getContributionStatus()));
                if (lastPendingContributor && submittedShare.compareTo(remaining) < 0) {
                    return new ShareSubmissionResult(
                            "As the last pending contributor, you must enter the exact remaining amount of â‚¹"
                                    + remaining.setScale(2, RoundingMode.HALF_UP) + ".",
                            null, List.of());
                }
                if (submittedShare.compareTo(remaining) > 0) {
                    return new ShareSubmissionResult(
                            "Amount should be less than ₹" + remaining.setScale(2, RoundingMode.HALF_UP) + ".",
                            null, List.of());
                }

                splitDAO.updateContribution(conn, expenseId, actorId, submittedShare, "finalized");
                BigDecimal newRemaining = remaining.subtract(submittedShare).setScale(2, RoundingMode.HALF_UP);
                List<String> autoRemovedMessages = new ArrayList<>();
                if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
                    List<ExpenseSplit> refreshedSplits = splitDAO.findPendingByExpenseId(conn, expenseId);
                    for (ExpenseSplit pendingSplit : refreshedSplits) {
                        splitDAO.updateStatus(conn, expenseId, pendingSplit.getUserId(), "removed");
                        autoRemovedMessages.add(pendingSplit.getUserName() + " is removed from this expense");
                    }
                    finalizePendingExpense(conn, expense.getGroupId(), expenseId, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                } else {
                    expenseDAO.updatePendingState(conn, expenseId, true, newRemaining);
                }

                return new ShareSubmissionResult(null, null, autoRemovedMessages);
            });
        } catch (Exception e) {
            throw new Exception("Share submission failed and was rolled back: " + e.getMessage(), e);
        }
    }

    public String finalizeDistributedSplitNow(int expenseId, boolean splitRemainderEqually) throws Exception {
        if (!SessionManager.isLoggedIn()) {
            return "You must be logged in.";
        }
        int actorId = SessionManager.getCurrentUserId();

        try {
            return TransactionHelper.execute(conn -> {
                Expense expense = expenseDAO.findByIdForUpdate(conn, expenseId).orElse(null);
                if (expense == null) {
                    return "Expense not found.";
                }
                if (!expense.isPending()) {
                    return "This custom split is already finalized.";
                }
                if (!memberDAO.isMember(expense.getGroupId(), actorId)) {
                    return "You are not a member of this group.";
                }
                if (expense.getCreatedBy() != actorId) {
                    return "Only the expense creator can finish this pending split now.";
                }

                if (!splitRemainderEqually) {
                    splitDAO.deleteByExpenseId(conn, expenseId);
                    expenseDAO.delete(conn, expenseId);
                    activityLogDAO.log(conn, actorId, expense.getGroupId(),
                            "EXPENSE_DELETED", "Expense deleted: " + expense.getDescription() + " (ID " + expenseId + ")");
                    return null;
                }

                List<ExpenseSplit> pendingSplits = splitDAO.findPendingByExpenseId(conn, expenseId);
                if (pendingSplits.isEmpty()) {
                    finalizePendingExpense(conn, expense.getGroupId(), expenseId, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    return null;
                }

                Map<Integer, BigDecimal> equalRemainderShares =
                        buildEqualSplit(expense.getRemainingAmount(), pendingSplits.stream().map(ExpenseSplit::getUserId).toList());
                for (ExpenseSplit split : pendingSplits) {
                    splitDAO.updateContribution(conn, expenseId, split.getUserId(),
                            equalRemainderShares.get(split.getUserId()), "finalized");
                }
                finalizePendingExpense(conn, expense.getGroupId(), expenseId, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                return null;
            });
        } catch (Exception e) {
            throw new Exception("Pending split finalization failed and was rolled back: " + e.getMessage(), e);
        }
    }

    private PendingCustomSplitView toPendingView(Expense expense, List<ExpenseSplit> splits) {
        ExpenseSplit nextPending = findNextPendingSplit(splits);
        int pendingCount = (int) splits.stream()
                .filter(split -> "pending".equals(split.getContributionStatus()))
                .count();
        return new PendingCustomSplitView(expense, splits, nextPending, pendingCount);
    }

    private ExpenseSplit findNextPendingSplit(List<ExpenseSplit> splits) {
        return splits.stream()
                .filter(split -> "pending".equals(split.getContributionStatus()))
                .min(Comparator.comparingInt(ExpenseSplit::getContributionOrder))
                .orElse(null);
    }

    private void finalizePendingExpense(Connection conn, int groupId, int expenseId, BigDecimal remainingAmount)
            throws Exception {
        expenseDAO.updatePendingState(conn, expenseId, false, remainingAmount);
        balanceService.recalculateBalances(conn, groupId);
        settlementDAO.clearPendingForGroup(conn, groupId);
    }

    private String findGroupName(int groupId) throws Exception {
        return groupDAO.findById(groupId).map(com.expensesplitter.model.Group::getGroupName).orElse("group");
    }

    public record ShareSubmissionResult(String error, String infoMessage, List<String> autoRemovedMessages) { }

    // =========================================================================
    // Transaction helpers (delegate to shared utility)
    // =========================================================================

    private static void safeRollback(Connection conn) {
        TransactionHelper.safeRollback(conn);
    }

    private static void safeClose(Connection conn) {
        TransactionHelper.safeClose(conn);
    }
}
