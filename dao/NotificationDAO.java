package com.expensesplitter.dao;

import com.expensesplitter.database.DBConnection;
import com.expensesplitter.model.Notification;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NotificationDAO {

    public void create(int userId, String message) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            create(conn, userId, message);
        }
    }

    /** SQL: INSERT INTO notifications (user_id, message) VALUES (?, ?) */
    public void create(Connection conn, int userId, String message) throws SQLException {
        String sql = "INSERT INTO notifications (user_id, message) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, message);
            ps.executeUpdate();
        }
    }

    public List<Notification> findUnreadByUserId(int userId) throws Exception {
        String sql = """
                SELECT * FROM notifications
                WHERE user_id = ? AND is_read = 0
                ORDER BY created_at DESC
                """;
        return findByUser(userId, sql);
    }

    public List<Notification> findAllByUserId(int userId) throws Exception {
        String sql = """
                SELECT * FROM notifications
                WHERE user_id = ?
                ORDER BY created_at DESC LIMIT 50
                """;
        return findByUser(userId, sql);
    }

    private List<Notification> findByUser(int userId, String sql) throws Exception {
        List<Notification> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Notification n = new Notification();
                    n.setNotificationId(rs.getInt("notification_id"));
                    n.setUserId(rs.getInt("user_id"));
                    n.setMessage(rs.getString("message"));
                    n.setRead(rs.getBoolean("is_read"));
                    Timestamp created = rs.getTimestamp("created_at");
                    if (created != null) {
                        n.setCreatedAt(created.toLocalDateTime());
                    }
                    list.add(n);
                }
            }
        }
        return list;
    }

    public void markAllRead(int userId) throws Exception {
        String sql = "UPDATE notifications SET is_read = 1 WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }
}
