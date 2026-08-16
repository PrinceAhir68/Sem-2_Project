package com.expensesplitter.dao;

import com.expensesplitter.database.DBConnection;
import com.expensesplitter.model.Expense;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ExpenseDAO {

    // -------------------------------------------------------------------------
    // Public API (each opens its own connection — fine for simple reads)
    // -------------------------------------------------------------------------

    public int create(Expense expense) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            return create(conn, expense);
        }
    }

    public Optional<Expense> findById(int expenseId) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, expenseId);
        }
    }

    public List<Expense> findByGroupId(int groupId) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            return findByGroupId(conn, groupId);
        }
    }

    public boolean delete(int expenseId) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            return delete(conn, expenseId);
        }
    }

    public boolean update(Expense expense) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, expense);
        }
    }

    // -------------------------------------------------------------------------
    // Transaction-aware overloads (same Connection → same COMMIT / ROLLBACK)
    // -------------------------------------------------------------------------

    /**
     * INSERT expense within an existing transaction.
     * SQL: INSERT INTO expenses (group_id, paid_by, amount, description, category, split_type)
     *      VALUES (?, ?, ?, ?, ?, ?)
     */
    public int create(Connection conn, Expense expense) throws SQLException {
        String sql = """
                INSERT INTO expenses
                (group_id, paid_by, created_by, amount, description, category, split_type, is_pending, remaining_amount)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, expense.getGroupId());
            ps.setInt(2, expense.getPaidBy());
            ps.setInt(3, expense.getCreatedBy());
            ps.setBigDecimal(4, expense.getAmount());
            ps.setString(5, expense.getDescription());
            ps.setString(6, expense.getCategory());
            ps.setString(7, expense.getSplitType());
            ps.setBoolean(8, expense.isPending());
            ps.setBigDecimal(9, expense.getRemainingAmount());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create expense — no generated key returned.");
    }

    /**
     * UPDATE expense within an existing transaction.
     * SQL: UPDATE expenses SET paid_by=?, amount=?, description=?, category=?, split_type=?
     *      WHERE expense_id=?
     */
    public boolean update(Connection conn, Expense expense) throws SQLException {
        String sql = """
                UPDATE expenses
                SET paid_by = ?, created_by = ?, amount = ?, description = ?, category = ?,
                    split_type = ?, is_pending = ?, remaining_amount = ?
                WHERE expense_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, expense.getPaidBy());
            ps.setInt(2, expense.getCreatedBy());
            ps.setBigDecimal(3, expense.getAmount());
            ps.setString(4, expense.getDescription());
            ps.setString(5, expense.getCategory());
            ps.setString(6, expense.getSplitType());
            ps.setBoolean(7, expense.isPending());
            ps.setBigDecimal(8, expense.getRemainingAmount());
            ps.setInt(9, expense.getExpenseId());
            return ps.executeUpdate() > 0;
        }
    }

    public Optional<Expense> findById(Connection conn, int expenseId) throws SQLException {
        String sql = """
                SELECT e.*, u.name AS payer_name
                FROM expenses e
                JOIN users u ON e.paid_by = u.user_id
                WHERE e.expense_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, expenseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Expense> findByGroupId(Connection conn, int groupId) throws SQLException {
        return findByGroupId(conn, groupId, true);
    }

    public List<Expense> findByGroupId(Connection conn, int groupId, boolean includePending) throws SQLException {
        String sql = """
                SELECT e.*, u.name AS payer_name
                FROM expenses e
                JOIN users u ON e.paid_by = u.user_id
                WHERE e.group_id = ? AND (? = 1 OR e.is_pending = 0)
                ORDER BY e.expense_date DESC
                """;
        List<Expense> expenses = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setBoolean(2, includePending);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    expenses.add(mapRow(rs));
                }
            }
        }
        return expenses;
    }

    public List<Expense> findPendingCustomByGroupId(Connection conn, int groupId) throws SQLException {
        String sql = """
                SELECT e.*, u.name AS payer_name
                FROM expenses e
                JOIN users u ON e.paid_by = u.user_id
                WHERE e.group_id = ? AND e.split_type = 'custom' AND e.is_pending = 1
                ORDER BY e.expense_date DESC
                """;
        List<Expense> expenses = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    expenses.add(mapRow(rs));
                }
            }
        }
        return expenses;
    }

    public Optional<Expense> findByIdForUpdate(Connection conn, int expenseId) throws SQLException {
        String sql = """
                SELECT e.*, u.name AS payer_name
                FROM expenses e
                JOIN users u ON e.paid_by = u.user_id
                WHERE e.expense_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, expenseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public boolean updatePendingState(Connection conn, int expenseId, boolean pending, BigDecimal remainingAmount)
            throws SQLException {
        String sql = "UPDATE expenses SET is_pending = ?, remaining_amount = ? WHERE expense_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, pending);
            ps.setBigDecimal(2, remainingAmount);
            ps.setInt(3, expenseId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(Connection conn, int expenseId) throws SQLException {
        String sql = "DELETE FROM expenses WHERE expense_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, expenseId);
            return ps.executeUpdate() > 0;
        }
    }

    private Expense mapRow(ResultSet rs) throws SQLException {
        Expense expense = new Expense();
        expense.setExpenseId(rs.getInt("expense_id"));
        expense.setGroupId(rs.getInt("group_id"));
        expense.setPaidBy(rs.getInt("paid_by"));
        expense.setCreatedBy(rs.getInt("created_by"));
        expense.setAmount(rs.getBigDecimal("amount"));
        expense.setDescription(rs.getString("description"));
        expense.setCategory(rs.getString("category"));
        expense.setSplitType(rs.getString("split_type"));
        expense.setPending(rs.getBoolean("is_pending"));
        expense.setRemainingAmount(rs.getBigDecimal("remaining_amount"));
        Timestamp date = rs.getTimestamp("expense_date");
        if (date != null) {
            expense.setExpenseDate(date.toLocalDateTime());
        }
        expense.setPayerName(rs.getString("payer_name"));
        return expense;
    }
}
