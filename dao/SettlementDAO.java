package com.expensesplitter.dao;

import com.expensesplitter.database.DBConnection;
import com.expensesplitter.model.Settlement;

import java.sql.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SettlementDAO {

    public int create(Settlement settlement) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            return create(conn, settlement);
        }
    }

    public void clearPendingForGroup(int groupId) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            clearPendingForGroup(conn, groupId);
        }
    }

    /** SQL: INSERT INTO settlements (group_id, from_user, to_user, amount, is_settled) VALUES (?, ?, ?, ?, 0) */
    public int create(Connection conn, Settlement settlement) throws SQLException {
        String sql = """
                INSERT INTO settlements (group_id, from_user, to_user, amount, is_settled)
                VALUES (?, ?, ?, ?, 0)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, settlement.getGroupId());
            ps.setInt(2, settlement.getFromUser());
            ps.setInt(3, settlement.getToUser());
            ps.setBigDecimal(4, settlement.getAmount());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create settlement");
    }

    /** SQL: DELETE FROM settlements WHERE group_id = ? AND is_settled = 0 */
    public void clearPendingForGroup(Connection conn, int groupId) throws SQLException {
        String sql = "DELETE FROM settlements WHERE group_id = ? AND is_settled = 0";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.executeUpdate();
        }
    }

    public List<Settlement> findPendingByGroupId(int groupId) throws Exception {
        return findByGroupId(groupId, false);
    }

    public List<Settlement> findHistoryByGroupId(int groupId) throws Exception {
        return findByGroupId(groupId, true);
    }

    private List<Settlement> findByGroupId(int groupId, boolean settledOnly) throws Exception {
        String sql = """
                SELECT s.*, fu.name AS from_user_name, tu.name AS to_user_name
                FROM settlements s
                JOIN users fu ON s.from_user = fu.user_id
                JOIN users tu ON s.to_user = tu.user_id
                WHERE s.group_id = ? AND s.is_settled = ?
                ORDER BY s.created_at DESC
                """;
        List<Settlement> settlements = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setBoolean(2, settledOnly);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    settlements.add(mapRow(rs));
                }
            }
        }
        return settlements;
    }

    public Optional<Settlement> findById(int settlementId) throws Exception {
        String sql = """
                SELECT s.*, fu.name AS from_user_name, tu.name AS to_user_name
                FROM settlements s
                JOIN users fu ON s.from_user = fu.user_id
                JOIN users tu ON s.to_user = tu.user_id
                WHERE s.settlement_id = ?
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, settlementId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public boolean markAsSettled(int settlementId) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            return markAsSettled(conn, settlementId);
        }
    }

    /**
     * Mark settlement paid inside an open transaction.
     * SQL: UPDATE settlements SET is_settled = 1, settled_at = NOW() WHERE settlement_id = ?
     */
    public boolean markAsSettled(Connection conn, int settlementId) throws SQLException {
        String sql = "UPDATE settlements SET is_settled = 1, settled_at = NOW() WHERE settlement_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, settlementId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Returns each member's adjustment from completed payments in a group.
     * A payment made by a member reduces what they owe (+); a payment received
     * reduces what others owe them (-).
     */
    public Map<Integer, BigDecimal> findSettledBalanceAdjustments(Connection conn, int groupId)
            throws SQLException {
        String sql = """
                SELECT user_id, SUM(adjustment) AS adjustment
                FROM (
                    SELECT from_user AS user_id, amount AS adjustment
                    FROM settlements
                    WHERE group_id = ? AND is_settled = 1
                    UNION ALL
                    SELECT to_user AS user_id, -amount AS adjustment
                    FROM settlements
                    WHERE group_id = ? AND is_settled = 1
                ) settled_payments
                GROUP BY user_id
                """;
        Map<Integer, BigDecimal> adjustments = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    adjustments.put(rs.getInt("user_id"), rs.getBigDecimal("adjustment"));
                }
            }
        }
        return adjustments;
    }

    private Settlement mapRow(ResultSet rs) throws Exception {
        Settlement s = new Settlement();
        s.setSettlementId(rs.getInt("settlement_id"));
        s.setGroupId(rs.getInt("group_id"));
        s.setFromUser(rs.getInt("from_user"));
        s.setToUser(rs.getInt("to_user"));
        s.setAmount(rs.getBigDecimal("amount"));
        s.setSettled(rs.getBoolean("is_settled"));
        Timestamp settledAt = rs.getTimestamp("settled_at");
        if (settledAt != null) {
            s.setSettledAt(settledAt.toLocalDateTime());
        }
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            s.setCreatedAt(created.toLocalDateTime());
        }
        s.setFromUserName(rs.getString("from_user_name"));
        s.setToUserName(rs.getString("to_user_name"));
        return s;
    }
}
