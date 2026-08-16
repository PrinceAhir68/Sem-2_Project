package com.expensesplitter.dao;

import com.expensesplitter.database.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ActivityLogQueryDAO {

    public List<String> findDescriptionsByUserId(int userId) throws Exception {
        String sql = """
                SELECT action_type, description, created_at
                FROM activity_logs
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT 100
                """;
        List<String> logs = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(String.format("[%s] %s — %s",
                            rs.getTimestamp("created_at"),
                            rs.getString("action_type"),
                            rs.getString("description")));
                }
            }
        }
        return logs;
    }
}
