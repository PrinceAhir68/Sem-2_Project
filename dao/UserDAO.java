package com.expensesplitter.dao;

import com.expensesplitter.database.DBConnection;
import com.expensesplitter.model.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDAO {

    public boolean create(User user) throws Exception {
        String sql = "INSERT INTO users (name, email, username, password_hash) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getUsername());
            ps.setString(4, user.getPasswordHash());
            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        user.setUserId(rs.getInt(1));
                    }
                }
                return true;
            }
            return false;
        }
    }

    public Optional<User> findByUsername(String username) throws Exception {
        String sql = "SELECT * FROM users WHERE LOWER(username) = LOWER(?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<User> findByEmail(String email) throws Exception {
        String sql = "SELECT * FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<User> findById(int userId) throws Exception {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<User> searchByUsernameOrEmail(String query) throws Exception {
        String sql = "SELECT * FROM users WHERE username LIKE ? OR email LIKE ? OR name LIKE ? LIMIT 20";
        String pattern = "%" + query + "%";
        List<User> users = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
            }
        }
        return users;
    }

    public List<User> findAll() throws Exception {
        String sql = "SELECT * FROM users ORDER BY user_id";
        List<User> users = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        }
        return users;
    }

    public void updateFailedAttempts(int userId, int attempts, LocalDateTime lockedUntil) throws Exception {
        String sql = "UPDATE users SET failed_attempts = ?, locked_until = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attempts);
            if (lockedUntil != null) {
                ps.setTimestamp(2, Timestamp.valueOf(lockedUntil));
            } else {
                ps.setNull(2, Types.TIMESTAMP);
            }
            ps.setInt(3, userId);
            ps.executeUpdate();
        }
    }

    public void resetFailedAttempts(int userId) throws Exception {
        updateFailedAttempts(userId, 0, null);
    }

    public boolean updateProfile(int userId, String name, String email) throws Exception {
        String sql = "UPDATE users SET name = ?, email = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updatePassword(int userId, String passwordHash) throws Exception {
        String sql = "UPDATE users SET password_hash = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    private User mapRow(ResultSet rs) throws Exception {
        User user = new User();
        user.setUserId(rs.getInt("user_id"));
        user.setName(rs.getString("name"));
        user.setEmail(rs.getString("email"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setFailedAttempts(rs.getInt("failed_attempts"));
        Timestamp locked = rs.getTimestamp("locked_until");
        if (locked != null) {
            user.setLockedUntil(locked.toLocalDateTime());
        }
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            user.setCreatedAt(created.toLocalDateTime());
        }
        return user;
    }
}
