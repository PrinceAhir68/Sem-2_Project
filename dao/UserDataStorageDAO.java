package com.expensesplitter.dao;

import com.expensesplitter.database.DBConnection;

import java.io.Reader;
import java.io.StringReader;
import java.sql.*;
import java.util.Optional;

public class UserDataStorageDAO {

    public void saveUserData(int userId, String fileName, String data) throws Exception {
        String sql = """
                INSERT INTO user_data_files (user_id, file_name, data_clob)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    file_name = VALUES(file_name),
                    data_clob = VALUES(data_clob),
                    updated_at = CURRENT_TIMESTAMP
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, fileName);
            ps.setCharacterStream(3, new StringReader(data), data.length());
            ps.executeUpdate();
        }
    }

    public Optional<String> readUserData(int userId) throws Exception {
        String sql = "SELECT data_clob FROM user_data_files WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                Clob clob = rs.getClob("data_clob");
                if (clob == null) {
                    return Optional.empty();
                }

                try (Reader reader = clob.getCharacterStream()) {
                    StringBuilder builder = new StringBuilder();
                    char[] buffer = new char[4096];
                    int read;
                    while ((read = reader.read(buffer)) != -1) {
                        builder.append(buffer, 0, read);
                    }
                    return Optional.of(builder.toString());
                } finally {
                    clob.free();
                }
            }
        }
    }

    public Optional<String> getStoredFileName(int userId) throws Exception {
        String sql = "SELECT file_name FROM user_data_files WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("file_name"));
                }
            }
        }
        return Optional.empty();
    }
}
