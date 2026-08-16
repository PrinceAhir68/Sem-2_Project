package com.expensesplitter;

import com.expensesplitter.database.DBConnection;
import com.expensesplitter.database.DatabaseInitializer;
import com.expensesplitter.menu.MainMenu;
import com.expensesplitter.utility.ConsoleHelper;

public class Main {

    public static void main(String[] args) {
        ConsoleHelper.printHeader("Expense Splitter with Debt Simplification");
        System.out.println("  Java + MySQL + JDBC + Priority Queue Algorithm");
        System.out.println("=".repeat(50));

        try {
            DatabaseInitializer.initialize();
            DBConnection.testConnection();
            ConsoleHelper.printSuccess("Database connected successfully!");
            ConsoleHelper.printInfo("Using: " + DBConnection.getUrl());
            ConsoleHelper.printInfo("User : " + DBConnection.getUsername());

            new MainMenu().show();
        } catch (Exception e) {
            ConsoleHelper.printError(e.getMessage());
            System.out.println();
            System.out.println("Fix checklist:");
            System.out.println("  - Start MySQL service on localhost:3306");
            System.out.println("  - User: root, Password: (empty)");
            System.out.println("  - Database: expense_splitter (auto-created on first run)");
            System.out.println("  - Driver: com.mysql.cj.jdbc.Driver");
        }
    }
}
