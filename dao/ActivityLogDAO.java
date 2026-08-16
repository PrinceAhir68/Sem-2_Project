package com.expensesplitter.dao;

import com.expensesplitter.database.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * History / activity log writes. Transaction overload keeps history in the same
 * COMMIT as expense + balances (or ROLLBACKs together if anything fails).
 */
public class ActivityLogDAO {

    public void log(Integer userId, Integer groupId, String actionType, String description)
            throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            log(conn, userId, groupId, actionType, description);
        }
    }

    public void log(Integer userId, Integer groupId, String actionType, String description,
                    Integer targetUserId) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            log(conn, userId, groupId, actionType, description, targetUserId);
        }
    }

    /**
     * History insert inside an open transaction.
     * SQL: INSERT INTO activity_logs (user_id, group_id, action_type, description)
     *      VALUES (?, ?, ?, ?)
     */
    public void log(Connection conn, Integer userId, Integer groupId,
                    String actionType, String description) throws SQLException {
        String sql = """
                INSERT INTO activity_logs (user_id, group_id, action_type, description)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (userId != null) {
                ps.setInt(1, userId);
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            if (groupId != null) {
                ps.setInt(2, groupId);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, actionType);
            ps.setString(4, description);
            ps.executeUpdate();
        }
    }

    public void log(Connection conn, Integer userId, Integer groupId,
                    String actionType, String description, Integer targetUserId) throws SQLException {
        String sql = """
                INSERT INTO activity_logs (user_id, target_user_id, group_id, action_type, description)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (userId != null) {
                ps.setInt(1, userId);
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            if (targetUserId != null) {
                ps.setInt(2, targetUserId);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            if (groupId != null) {
                ps.setInt(3, groupId);
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            ps.setString(4, actionType);
            ps.setString(5, description);
            ps.executeUpdate();
        }
    }
}
