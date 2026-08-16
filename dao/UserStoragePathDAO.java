package com.expensesplitter.dao;

import com.expensesplitter.database.DBConnection;

import java.sql.*;
import java.nio.file.Path;
import java.util.Optional;

public class UserStoragePathDAO {

    public boolean saveUserStoragePath(int userId, String storagePath, boolean isValid) throws Exception {
        String checkSql = "SELECT COUNT(*) as count FROM user_storage_paths WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt("count") > 0) {
                    return updateUserStoragePath(userId, storagePath, isValid);
                }
            }
        }

        String insertSql = "INSERT INTO user_storage_paths (user_id, storage_path, is_valid) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setInt(1, userId);
            ps.setString(2, storagePath);
            ps.setBoolean(3, isValid);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateUserStoragePath(int userId, String storagePath, boolean isValid) throws Exception {
        String sql = "UPDATE user_storage_paths SET storage_path = ?, is_valid = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, storagePath);
            ps.setBoolean(2, isValid);
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public Optional<String> getUserStoragePath(int userId) throws Exception {
        String sql = "SELECT storage_path FROM user_storage_paths WHERE user_id = ? AND is_valid = 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("storage_path"));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<String> getUserStoragePathAny(int userId) throws Exception {
        String sql = "SELECT storage_path FROM user_storage_paths WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("storage_path"));
                }
            }
        }
        return Optional.empty();
    }
}
