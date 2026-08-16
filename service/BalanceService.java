package com.expensesplitter.service;

import com.expensesplitter.dao.BalanceDAO;
import com.expensesplitter.dao.ExpenseDAO;
import com.expensesplitter.dao.ExpenseSplitDAO;
import com.expensesplitter.dao.GroupMemberDAO;
import com.expensesplitter.dao.SettlementDAO;
import com.expensesplitter.database.DBConnection;
import com.expensesplitter.model.Balance;
import com.expensesplitter.model.Expense;
import com.expensesplitter.model.ExpenseSplit;
import com.expensesplitter.model.User;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BalanceService {

    private final BalanceDAO balanceDAO = new BalanceDAO();
    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final ExpenseSplitDAO splitDAO = new ExpenseSplitDAO();
    private final GroupMemberDAO memberDAO = new GroupMemberDAO();
    private final SettlementDAO settlementDAO = new SettlementDAO();

    /**
     * Rebuilds balances from scratch for every group member so old totals
     * never accumulate/mix with new expense data.
     */
    public void recalculateBalances(int groupId) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            recalculateBalances(conn, groupId);
        }
    }

    /**
     * Same rebuild, but on a shared Connection so it participates in COMMIT/ROLLBACK.
     */
    public void recalculateBalances(Connection conn, int groupId) throws SQLException {
        List<User> members = memberDAO.getMembers(conn, groupId);

        for (User member : members) {
            balanceDAO.updateBalance(conn, groupId, member.getUserId(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        List<Expense> expenses = expenseDAO.findByGroupId(conn, groupId, false);
        Map<Integer, BigDecimal> settledAdjustments =
                settlementDAO.findSettledBalanceAdjustments(conn, groupId);

        Map<Integer, BigDecimal> totalPaid = new HashMap<>();
        Map<Integer, BigDecimal> totalShare = new HashMap<>();

        for (User member : members) {
            totalPaid.put(member.getUserId(), BigDecimal.ZERO);
            totalShare.put(member.getUserId(), BigDecimal.ZERO);
        }

        for (Expense expense : expenses) {
            totalPaid.merge(expense.getPaidBy(), expense.getAmount(), BigDecimal::add);

            List<ExpenseSplit> splits = splitDAO.findByExpenseId(conn, expense.getExpenseId());
            for (ExpenseSplit split : splits) {
                totalShare.merge(split.getUserId(), split.getShareAmount(), BigDecimal::add);
            }
        }

        for (User member : members) {
            int userId = member.getUserId();
            BigDecimal paid = totalPaid.getOrDefault(userId, BigDecimal.ZERO);
            BigDecimal share = totalShare.getOrDefault(userId, BigDecimal.ZERO);
            // Net = paid - share + settlements I paid - settlements paid to me.
            // Only completed settlements count; pending suggestions are not payments.
            BigDecimal net = paid.subtract(share)
                    .add(settledAdjustments.getOrDefault(userId, BigDecimal.ZERO));
            balanceDAO.updateBalance(conn, groupId, userId, paid, share, net);
        }
    }

    public List<Balance> getBalances(int groupId) throws Exception {
        return balanceDAO.findByGroupId(groupId);
    }

    public List<Balance> getBalances(Connection conn, int groupId) throws SQLException {
        return balanceDAO.findByGroupId(conn, groupId);
    }
}
