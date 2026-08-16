package com.expensesplitter.dao;

import com.expensesplitter.database.DBConnection;
import com.expensesplitter.model.Group;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GroupDAO {

    public int create(Group group) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            return create(conn, group);
        }
    }

    /** SQL: INSERT INTO groups (group_name, created_by) VALUES (?, ?) */
    public int create(Connection conn, Group group) throws SQLException {
        String sql = "INSERT INTO groups (group_name, created_by) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, group.getGroupName());
            ps.setInt(2, group.getCreatedBy());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    group.setGroupId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Failed to create group");
    }

    public Optional<Group> findById(int groupId) throws Exception {
        String sql = """
                SELECT g.*, u.name AS creator_name
                FROM groups g
                JOIN users u ON g.created_by = u.user_id
                WHERE g.group_id = ?
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Group> findByUserId(int userId) throws Exception {
        String sql = """
                SELECT g.*, u.name AS creator_name
                FROM groups g
                JOIN group_members gm ON g.group_id = gm.group_id
                JOIN users u ON g.created_by = u.user_id
                WHERE gm.user_id = ?
                ORDER BY g.created_at DESC
                """;
        List<Group> groups = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    groups.add(mapRow(rs));
                }
            }
        }
        return groups;
    }

    public boolean updateName(int groupId, String newName) throws Exception {
        String sql = "UPDATE groups SET group_name = ? WHERE group_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setInt(2, groupId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int groupId) throws Exception {
        String sql = "DELETE FROM groups WHERE group_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            return ps.executeUpdate() > 0;
        }
    }

    private Group mapRow(ResultSet rs) throws Exception {
        Group group = new Group();
        group.setGroupId(rs.getInt("group_id"));
        group.setGroupName(rs.getString("group_name"));
        group.setCreatedBy(rs.getInt("created_by"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            group.setCreatedAt(created.toLocalDateTime());
        }
        group.setCreatorName(rs.getString("creator_name"));
        return group;
    }
}
