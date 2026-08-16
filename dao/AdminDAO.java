package com.expensesplitter.dao;

import com.expensesplitter.database.DBConnection;
import com.expensesplitter.model.Admin;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AdminDAO {

    public boolean create(Admin admin) throws Exception {
        String sql = "INSERT INTO admins (admin_username, password_hash, email) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, admin.getAdminUsername());
            ps.setString(2, admin.getPasswordHash());
            ps.setString(3, admin.getEmail());
            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        admin.setAdminId(rs.getInt(1));
                    }
                }
                return true;
            }
            return false;
        }
    }

    public Optional<Admin> findByAdminUsername(String adminUsername) throws Exception {
        String sql = "SELECT * FROM admins WHERE LOWER(admin_username) = LOWER(?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, adminUsername);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Admin> findFirstAdmin() throws Exception {
        String sql = "SELECT * FROM admins LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    public Optional<Admin> findById(int adminId) throws Exception {
        String sql = "SELECT * FROM admins WHERE admin_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, adminId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Admin> findAll() throws Exception {
        String sql = "SELECT * FROM admins";
        List<Admin> admins = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                admins.add(mapRow(rs));
            }
        }
        return admins;
    }

    private Admin mapRow(ResultSet rs) throws Exception {
        Admin admin = new Admin();
        admin.setAdminId(rs.getInt("admin_id"));
        admin.setAdminUsername(rs.getString("admin_username"));
        admin.setPasswordHash(rs.getString("password_hash"));
        admin.setEmail(rs.getString("email"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            admin.setCreatedAt(created.toLocalDateTime());
        }
        return admin;
    }
}
