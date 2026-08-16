package com.expensesplitter.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {

    private static volatile boolean initialized;

    public static void initialize() throws SQLException {
        if (initialized) {
            return;
        }

        synchronized (DatabaseInitializer.class) {
            if (initialized) {
                return;
            }

            createDatabaseIfMissing();
            createTablesIfMissing();
            initialized = true;
        }
    }

    private static void createDatabaseIfMissing() throws SQLException {
        try (Connection conn = DBConnection.getServerConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE DATABASE IF NOT EXISTS expense_splitter "
                            + "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
            );
        }
    }

    private static void createTablesIfMissing() throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS users (
                        user_id         INT AUTO_INCREMENT PRIMARY KEY,
                        name            VARCHAR(100) NOT NULL,
                        email           VARCHAR(150) NOT NULL UNIQUE,
                        username        VARCHAR(50)  NOT NULL UNIQUE,
                        password_hash   VARCHAR(255) NOT NULL,
                        failed_attempts INT          DEFAULT 0,
                        locked_until    DATETIME     NULL,
                        created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `groups` (
                        group_id    INT AUTO_INCREMENT PRIMARY KEY,
                        group_name  VARCHAR(100) NOT NULL,
                        created_by  INT NOT NULL,
                        created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_groups_created_by
                            FOREIGN KEY (created_by) REFERENCES users(user_id)
                            ON DELETE RESTRICT
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS group_members (
                        group_id   INT NOT NULL,
                        user_id    INT NOT NULL,
                        joined_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (group_id, user_id),
                        CONSTRAINT fk_gm_group
                            FOREIGN KEY (group_id) REFERENCES `groups`(group_id)
                            ON DELETE CASCADE,
                        CONSTRAINT fk_gm_user
                            FOREIGN KEY (user_id) REFERENCES users(user_id)
                            ON DELETE CASCADE
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS expenses (
                        expense_id   INT AUTO_INCREMENT PRIMARY KEY,
                        group_id     INT NOT NULL,
                        paid_by      INT NOT NULL,
                        amount       DECIMAL(12,2) NOT NULL,
                        description  VARCHAR(255),
                        category     ENUM('food','travel','hotel','shopping','entertainment','other')
                                     DEFAULT 'other',
                        split_type   ENUM('equal','exact','percentage','custom') NOT NULL,
                        expense_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_expenses_group
                            FOREIGN KEY (group_id) REFERENCES `groups`(group_id)
                            ON DELETE CASCADE,
                        CONSTRAINT fk_expenses_paid_by
                            FOREIGN KEY (paid_by) REFERENCES users(user_id)
                            ON DELETE RESTRICT,
                        CONSTRAINT chk_expense_amount CHECK (amount > 0)
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS expense_splits (
                        split_id     INT AUTO_INCREMENT PRIMARY KEY,
                        expense_id   INT NOT NULL,
                        user_id      INT NOT NULL,
                        share_amount DECIMAL(12,2) NOT NULL,
                        UNIQUE KEY uk_expense_user (expense_id, user_id),
                        CONSTRAINT fk_splits_expense
                            FOREIGN KEY (expense_id) REFERENCES expenses(expense_id)
                            ON DELETE CASCADE,
                        CONSTRAINT fk_splits_user
                            FOREIGN KEY (user_id) REFERENCES users(user_id)
                            ON DELETE RESTRICT,
                        CONSTRAINT chk_split_amount CHECK (share_amount >= 0)
                    )
                    """);

            ensureColumnExists(stmt, "expenses", "created_by",
                    "ALTER TABLE expenses ADD COLUMN created_by INT NULL AFTER paid_by");
            ensureColumnExists(stmt, "expenses", "is_pending",
                    "ALTER TABLE expenses ADD COLUMN is_pending TINYINT(1) NOT NULL DEFAULT 0 AFTER split_type");
            ensureColumnExists(stmt, "expenses", "remaining_amount",
                    "ALTER TABLE expenses ADD COLUMN remaining_amount DECIMAL(12,2) NOT NULL DEFAULT 0 AFTER is_pending");
            ensureColumnExists(stmt, "expense_splits", "contribution_order",
                    "ALTER TABLE expense_splits ADD COLUMN contribution_order INT NOT NULL DEFAULT 0 AFTER share_amount");
            ensureColumnExists(stmt, "expense_splits", "contribution_status",
                    "ALTER TABLE expense_splits ADD COLUMN contribution_status "
                            + "ENUM('finalized','pending','removed') NOT NULL DEFAULT 'finalized' AFTER contribution_order");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS balances (
                        balance_id   INT AUTO_INCREMENT PRIMARY KEY,
                        group_id     INT NOT NULL,
                        user_id      INT NOT NULL,
                        total_paid   DECIMAL(12,2) DEFAULT 0,
                        total_share  DECIMAL(12,2) DEFAULT 0,
                        net_balance  DECIMAL(12,2) DEFAULT 0,
                        updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_balance_group_user (group_id, user_id),
                        CONSTRAINT fk_balances_group
                            FOREIGN KEY (group_id) REFERENCES `groups`(group_id)
                            ON DELETE CASCADE,
                        CONSTRAINT fk_balances_user
                            FOREIGN KEY (user_id) REFERENCES users(user_id)
                            ON DELETE CASCADE
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS settlements (
                        settlement_id INT AUTO_INCREMENT PRIMARY KEY,
                        group_id      INT NOT NULL,
                        from_user     INT NOT NULL,
                        to_user       INT NOT NULL,
                        amount        DECIMAL(12,2) NOT NULL,
                        is_settled    TINYINT(1) DEFAULT 0,
                        settled_at    DATETIME NULL,
                        created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_settlements_group
                            FOREIGN KEY (group_id) REFERENCES `groups`(group_id)
                            ON DELETE CASCADE,
                        CONSTRAINT fk_settlements_from
                            FOREIGN KEY (from_user) REFERENCES users(user_id)
                            ON DELETE RESTRICT,
                        CONSTRAINT fk_settlements_to
                            FOREIGN KEY (to_user) REFERENCES users(user_id)
                            ON DELETE RESTRICT,
                        CONSTRAINT chk_settlement_amount CHECK (amount > 0),
                        CONSTRAINT chk_settlement_users CHECK (from_user <> to_user)
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS notifications (
                        notification_id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id         INT NOT NULL,
                        message         TEXT NOT NULL,
                        is_read         TINYINT(1) DEFAULT 0,
                        created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_notifications_user
                            FOREIGN KEY (user_id) REFERENCES users(user_id)
                            ON DELETE CASCADE
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS activity_logs (
                        log_id      INT AUTO_INCREMENT PRIMARY KEY,
                        user_id     INT NULL,
                        group_id    INT NULL,
                        action_type VARCHAR(50) NOT NULL,
                        description TEXT,
                        created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_logs_user
                            FOREIGN KEY (user_id) REFERENCES users(user_id)
                            ON DELETE SET NULL,
                        CONSTRAINT fk_logs_group
                            FOREIGN KEY (group_id) REFERENCES `groups`(group_id)
                            ON DELETE SET NULL
                    )
                    """);
            ensureColumnExists(stmt, "activity_logs", "target_user_id",
                    "ALTER TABLE activity_logs ADD COLUMN target_user_id INT NULL AFTER user_id");
            ensureTrigger(stmt, "trg_activity_log_blocked_notify", """
                    CREATE TRIGGER trg_activity_log_blocked_notify
                    AFTER INSERT ON activity_logs
                    FOR EACH ROW
                    BEGIN
                        INSERT INTO notifications (user_id, message)
                        SELECT NEW.target_user_id, NEW.description
                        WHERE NEW.action_type = 'SETTLEMENT_PAY_BLOCKED'
                          AND NEW.target_user_id IS NOT NULL;
                    END
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS user_data_files (
                        user_id     INT PRIMARY KEY,
                        file_name   VARCHAR(255) NOT NULL,
                        data_clob   LONGTEXT NOT NULL,
                        updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        CONSTRAINT fk_user_data_files_user
                            FOREIGN KEY (user_id) REFERENCES users(user_id)
                            ON DELETE CASCADE
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS admins (
                        admin_id         INT AUTO_INCREMENT PRIMARY KEY,
                        admin_username   VARCHAR(50)  NOT NULL UNIQUE,
                        password_hash    VARCHAR(255) NOT NULL,
                        email            VARCHAR(150) NOT NULL,
                        created_at       DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS user_storage_paths (
                        path_id     INT AUTO_INCREMENT PRIMARY KEY,
                        user_id     INT NOT NULL UNIQUE,
                        storage_path VARCHAR(500) NOT NULL,
                        is_valid    TINYINT(1) DEFAULT 1,
                        created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        CONSTRAINT fk_storage_paths_user
                            FOREIGN KEY (user_id) REFERENCES users(user_id)
                            ON DELETE CASCADE
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS user_reports (
                        id           INT AUTO_INCREMENT PRIMARY KEY,
                        user_id      INT NOT NULL,
                        report_name  VARCHAR(255) NOT NULL,
                        report_data  LONGTEXT NOT NULL,
                        created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_user_reports_user
                            FOREIGN KEY (user_id) REFERENCES users(user_id)
                            ON DELETE CASCADE,
                        INDEX idx_user_reports_user (user_id)
                    )
                    """);

            createDefaultAdminIfNotExists(conn, stmt);
        }
    }

    private static void ensureColumnExists(Statement stmt, String tableName, String columnName, String alterSql)
            throws SQLException {
        try (ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM " + tableName + " LIKE '" + columnName + "'")) {
            if (!rs.next()) {
                stmt.executeUpdate(alterSql);
            }
        }
    }

    private static void ensureTrigger(Statement stmt, String triggerName, String createSql) throws SQLException {
        stmt.execute("DROP TRIGGER IF EXISTS " + triggerName);
        stmt.execute(createSql);
    }

    private static void createDefaultAdminIfNotExists(Connection conn, Statement stmt) throws SQLException {
        String checkSql = "SELECT COUNT(*) as count FROM admins";
        try (ResultSet rs = stmt.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt("count") == 0) {
                String defaultAdminPassword = "admin123";
                String hashedPassword = com.expensesplitter.utility.PasswordHasher.hash(defaultAdminPassword);
                String insertSql = "INSERT INTO admins (admin_username, password_hash, email) VALUES (?, ?, ?)";
                try (java.sql.PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, "admin");
                    ps.setString(2, hashedPassword);
                    ps.setString(3, "admin@expensesplitter.com");
                    ps.executeUpdate();
                }
            }
        }
    }
}
