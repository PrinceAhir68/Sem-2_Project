package com.expensesplitter.dao;

import com.expensesplitter.database.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserSettlementQueryDAO {

    public List<String> findByUserId(int userId) throws Exception {
        String sql = """
                SELECT s.*, g.group_name, fu.name AS from_user_name, tu.name AS to_user_name
                FROM settlements s
                JOIN `groups` g ON s.group_id = g.group_id
                JOIN users fu ON s.from_user = fu.user_id
                JOIN users tu ON s.to_user = tu.user_id
                WHERE s.from_user = ? OR s.to_user = ?
                ORDER BY s.created_at DESC
                """;
        List<String> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(String.format(
                            "Group: %s | %s pays %s Rs.%s | Status: %s",
                            rs.getString("group_name"),
                            rs.getString("from_user_name"),
                            rs.getString("to_user_name"),
                            rs.getBigDecimal("amount"),
                            rs.getBoolean("is_settled") ? "Paid" : "Pending"
                    ));
                }
            }
        }
        return rows;
    }
}
