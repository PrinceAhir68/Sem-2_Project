package com.expensesplitter.dao;

import com.expensesplitter.database.DBConnection;
import com.expensesplitter.model.ExpenseSplit;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ExpenseSplitDAO {

    public void create(ExpenseSplit split) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            create(conn, split);
        }
    }

    public void createBatch(List<ExpenseSplit> splits) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            createBatch(conn, splits);
        }
    }

    public List<ExpenseSplit> findByExpenseId(int expenseId) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            return findByExpenseId(conn, expenseId);
        }
    }

    /** SQL: INSERT INTO expense_splits (expense_id, user_id, share_amount) VALUES (?, ?, ?) */
    public void create(Connection conn, ExpenseSplit split) throws SQLException {
        String sql = """
                INSERT INTO expense_splits
                (expense_id, user_id, share_amount, contribution_order, contribution_status)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, split.getExpenseId());
            ps.setInt(2, split.getUserId());
            ps.setBigDecimal(3, split.getShareAmount());
            ps.setInt(4, split.getContributionOrder());
            ps.setString(5, normalizeStatus(split.getContributionStatus()));
            ps.executeUpdate();
        }
    }

    /**
     * Batch-insert expense members (splits) on the same Connection (one transaction).
     * SQL: INSERT INTO expense_splits (expense_id, user_id, share_amount) VALUES (?, ?, ?)
     */
    public void createBatch(Connection conn, List<ExpenseSplit> splits) throws SQLException {
        String sql = """
                INSERT INTO expense_splits
                (expense_id, user_id, share_amount, contribution_order, contribution_status)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ExpenseSplit split : splits) {
                ps.setInt(1, split.getExpenseId());
                ps.setInt(2, split.getUserId());
                ps.setBigDecimal(3, split.getShareAmount());
                ps.setInt(4, split.getContributionOrder());
                ps.setString(5, normalizeStatus(split.getContributionStatus()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Remove all members for an expense before re-inserting on edit.
     * SQL: DELETE FROM expense_splits WHERE expense_id = ?
     */
    public void deleteByExpenseId(Connection conn, int expenseId) throws SQLException {
        String sql = "DELETE FROM expense_splits WHERE expense_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, expenseId);
            ps.executeUpdate();
        }
    }

    public List<ExpenseSplit> findByExpenseId(Connection conn, int expenseId) throws SQLException {
        String sql = """
                SELECT es.*, u.name AS user_name
                FROM expense_splits es
                JOIN users u ON es.user_id = u.user_id
                WHERE es.expense_id = ?
                ORDER BY es.contribution_order, es.split_id
                """;
        List<ExpenseSplit> splits = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, expenseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ExpenseSplit split = new ExpenseSplit();
                    split.setSplitId(rs.getInt("split_id"));
                    split.setExpenseId(rs.getInt("expense_id"));
                    split.setUserId(rs.getInt("user_id"));
                    split.setShareAmount(rs.getBigDecimal("share_amount"));
                    split.setContributionOrder(rs.getInt("contribution_order"));
                    split.setContributionStatus(rs.getString("contribution_status"));
                    split.setUserName(rs.getString("user_name"));
                    splits.add(split);
                }
            }
        }
        return splits;
    }

    public List<ExpenseSplit> findPendingByExpenseId(Connection conn, int expenseId) throws SQLException {
        String sql = """
                SELECT es.*, u.name AS user_name
                FROM expense_splits es
                JOIN users u ON es.user_id = u.user_id
                WHERE es.expense_id = ? AND es.contribution_status = 'pending'
                ORDER BY es.contribution_order, es.split_id
                """;
        List<ExpenseSplit> splits = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, expenseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ExpenseSplit split = new ExpenseSplit();
                    split.setSplitId(rs.getInt("split_id"));
                    split.setExpenseId(rs.getInt("expense_id"));
                    split.setUserId(rs.getInt("user_id"));
                    split.setShareAmount(rs.getBigDecimal("share_amount"));
                    split.setContributionOrder(rs.getInt("contribution_order"));
                    split.setContributionStatus(rs.getString("contribution_status"));
                    split.setUserName(rs.getString("user_name"));
                    splits.add(split);
                }
            }
        }
        return splits;
    }

    public boolean updateContribution(Connection conn, int expenseId, int userId, BigDecimal shareAmount, String status)
            throws SQLException {
        String sql = """
                UPDATE expense_splits
                SET share_amount = ?, contribution_status = ?
                WHERE expense_id = ? AND user_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, shareAmount);
            ps.setString(2, normalizeStatus(status));
            ps.setInt(3, expenseId);
            ps.setInt(4, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateStatus(Connection conn, int expenseId, int userId, String status) throws SQLException {
        String sql = "UPDATE expense_splits SET contribution_status = ? WHERE expense_id = ? AND user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizeStatus(status));
            ps.setInt(2, expenseId);
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;
        }
    }

    private String normalizeStatus(String status) {
        return (status == null || status.isBlank()) ? "finalized" : status;
    }
}
