package com.expensesplitter.dao;

import com.expensesplitter.database.DBConnection;
import com.expensesplitter.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupMemberDAO {

    public void addMember(int groupId, int userId) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            addMember(conn, groupId, userId);
        }
    }

    /** SQL: INSERT IGNORE INTO group_members (group_id, user_id) VALUES (?, ?) */
    public void addMember(Connection conn, int groupId, int userId) throws SQLException {
        String sql = "INSERT IGNORE INTO group_members (group_id, user_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public void removeMember(int groupId, int userId) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            removeMember(conn, groupId, userId);
        }
    }

    /** SQL: DELETE FROM group_members WHERE group_id = ? AND user_id = ? */
    public void removeMember(Connection conn, int groupId, int userId) throws SQLException {
        String sql = "DELETE FROM group_members WHERE group_id = ? AND user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public List<User> getMembers(int groupId) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            return getMembers(conn, groupId);
        }
    }

    public boolean isMember(int groupId, int userId) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            return isMember(conn, groupId, userId);
        }
    }

    public List<User> getMembers(Connection conn, int groupId) throws SQLException {
        String sql = """
                SELECT u.*
                FROM users u
                JOIN group_members gm ON u.user_id = gm.user_id
                WHERE gm.group_id = ?
                ORDER BY u.name
                """;
        List<User> members = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    User user = new User();
                    user.setUserId(rs.getInt("user_id"));
                    user.setName(rs.getString("name"));
                    user.setEmail(rs.getString("email"));
                    user.setUsername(rs.getString("username"));
                    members.add(user);
                }
            }
        }
        return members;
    }

    public boolean isMember(Connection conn, int groupId, int userId) throws SQLException {
        String sql = "SELECT 1 FROM group_members WHERE group_id = ? AND user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public int memberCount(int groupId) throws Exception {
        String sql = "SELECT COUNT(*) FROM group_members WHERE group_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
}
