package com.expensesplitter.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class  DBConnection {

    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String HOST = "localhost";
    private static final int PORT = 3306;
    private static final String DATABASE = "expense_splitter";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "";

    private static final String CONNECTION_PARAMS =
            "?useSSL=false"
            + "&allowPublicKeyRetrieval=true"
            + "&serverTimezone=UTC"
            + "&autoReconnect=true"
            + "&connectTimeout=10000"
            + "&socketTimeout=30000";

    static final String URL =
            "jdbc:mysql://" + HOST + ":" + PORT + "/" + DATABASE + CONNECTION_PARAMS;

    private static final String SERVER_URL =
            "jdbc:mysql://" + HOST + ":" + PORT + "/" + CONNECTION_PARAMS;

    static {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "MySQL JDBC Driver not found. Add mysql-connector-j to project libraries.", e);
        }
    }

    public static String getUrl() {
        return URL;
    }

    public static String getUsername() {
        return USERNAME;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }

    public static Connection getServerConnection() throws SQLException {
        return DriverManager.getConnection(SERVER_URL, USERNAME, PASSWORD);
    }

    public static void testConnection() throws SQLException {
        try (Connection conn = getConnection()) {
            if (!conn.isValid(5)) {
                throw new SQLException("Database connection is not valid.");
            }
        } catch (SQLException e) {
            throw new SQLException(buildConnectionHelp(e), e);
        }
    }

    private static String buildConnectionHelp(SQLException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        StringBuilder help = new StringBuilder("Database connection failed: ");
        help.append(e.getMessage()).append("\n\n");

        if (message.contains("communications link failure")
                || message.contains("did not receive any packets")
                || message.contains("connection refused")
                || message.contains("connect timed out")) {
            help.append("MySQL server is not running or not reachable.\n");
            help.append("  1. Start MySQL (Services -> MySQL80 -> Start, or MySQL Workbench).\n");
            help.append("  2. Confirm it listens on ").append(HOST).append(":").append(PORT).append(".\n");
        } else if (message.contains("unknown database")) {
            help.append("Database '").append(DATABASE).append("' does not exist yet.\n");
            help.append("  Run database/schema.sql in MySQL, or restart the app after MySQL is up.\n");
        } else if (message.contains("access denied")) {
            help.append("Login failed for user '").append(USERNAME).append("'.\n");
            help.append("  Check MySQL root password in DBConnection.java if it is not empty.\n");
        } else {
            help.append("Connection settings:\n");
            help.append("  URL      : ").append(URL).append("\n");
            help.append("  User     : ").append(USERNAME).append("\n");
            help.append("  Driver   : ").append(DRIVER).append("\n");
        }

        return help.toString();
    }
}
