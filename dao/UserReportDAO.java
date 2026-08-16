package com.expensesplitter.dao;

import com.expensesplitter.database.DBConnection;

import java.io.IOException;
import java.io.Reader;
import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Access to user CLOB reports stored in {@code user_reports}. */
public class UserReportDAO {

    public long insertReport(int userId, String reportName, String reportData) throws SQLException {
        String sql = "INSERT INTO user_reports (user_id, report_name, report_data) VALUES (?, ?, ?)";
        try (var conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, reportName);
            ps.setString(3, reportData);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    public List<ReportSummary> listSummariesByUserId(int userId) throws SQLException {
        String sql = """
                SELECT id, report_name, created_at, CHAR_LENGTH(report_data) AS char_len
                FROM user_reports WHERE user_id = ? ORDER BY created_at DESC, id DESC
                """;
        List<ReportSummary> reports = new ArrayList<>();
        try (var conn = DBConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    reports.add(new ReportSummary(rs.getLong("id"), rs.getString("report_name"),
                            rs.getString("created_at"), rs.getInt("char_len")));
                }
            }
        }
        return reports;
    }

    public Optional<String> readClobTextById(long reportId, int userId) throws SQLException {
        String sql = "SELECT report_data FROM user_reports WHERE id = ? AND user_id = ?";
        try (var conn = DBConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, reportId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Clob clob = rs.getClob("report_data");
                if (clob == null) {
                    return Optional.ofNullable(rs.getString("report_data"));
                }
                try {
                    return Optional.of(readClob(clob));
                } finally {
                    clob.free();
                }
            }
        }
    }

    private String readClob(Clob clob) throws SQLException {
        try (Reader reader = clob.getCharacterStream()) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                text.append(buffer, 0, count);
            }
            return text.toString();
        } catch (IOException e) {
            throw new SQLException("Failed to read report data", e);
        }
    }

    public record ReportSummary(long id, String reportName, String createdAt, int characterCount) { }
}
