package com.expensesplitter.dao;

import com.expensesplitter.database.DBConnection;
import com.expensesplitter.model.Balance;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BalanceDAO {

    public void initializeBalance(int groupId, int userId) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            initializeBalance(conn, groupId, userId);
        }
    }

    public void updateBalance(int groupId, int userId, BigDecimal totalPaid,
                              BigDecimal totalShare, BigDecimal netBalance) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            updateBalance(conn, groupId, userId, totalPaid, totalShare, netBalance);
        }
    }

    public List<Balance> findByGroupId(int groupId) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            return findByGroupId(conn, groupId);
        }
    }

    public void initializeBalance(Connection conn, int groupId, int userId) throws SQLException {
        String sql = """
                INSERT IGNORE INTO balances (group_id, user_id, total_paid, total_share, net_balance)
                VALUES (?, ?, 0, 0, 0)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    /**
     * Upsert balance row inside a transaction.
     * SQL: INSERT ... ON DUPLICATE KEY UPDATE total_paid, total_share, net_balance
     */
    public void updateBalance(Connection conn, int groupId, int userId, BigDecimal totalPaid,
                              BigDecimal totalShare, BigDecimal netBalance) throws SQLException {
        String sql = """
                INSERT INTO balances (group_id, user_id, total_paid, total_share, net_balance)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    total_paid = VALUES(total_paid),
                    total_share = VALUES(total_share),
                    net_balance = VALUES(net_balance)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.setBigDecimal(3, totalPaid);
            ps.setBigDecimal(4, totalShare);
            ps.setBigDecimal(5, netBalance);
            ps.executeUpdate();
        }
    }

    public List<Balance> findByGroupId(Connection conn, int groupId) throws SQLException {
        String sql = """
                SELECT b.*, u.name AS user_name
                FROM balances b
                JOIN users u ON b.user_id = u.user_id
                WHERE b.group_id = ?
                ORDER BY b.net_balance DESC
                """;
        List<Balance> balances = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    balances.add(mapRow(rs));
                }
            }
        }
        return balances;
    }

    private Balance mapRow(ResultSet rs) throws SQLException {
        Balance balance = new Balance();
        balance.setBalanceId(rs.getInt("balance_id"));
        balance.setGroupId(rs.getInt("group_id"));
        balance.setUserId(rs.getInt("user_id"));
        balance.setTotalPaid(rs.getBigDecimal("total_paid"));
        balance.setTotalShare(rs.getBigDecimal("total_share"));
        balance.setNetBalance(rs.getBigDecimal("net_balance"));
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            balance.setUpdatedAt(updated.toLocalDateTime());
        }
        balance.setUserName(rs.getString("user_name"));
        return balance;
    }
}
