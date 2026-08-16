package com.expensesplitter.service;

import com.expensesplitter.dao.*;
import com.expensesplitter.model.*;
import com.expensesplitter.utility.SessionManager;
import com.expensesplitter.model.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.List;

public class ExpenseReportService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserDAO userDAO = new UserDAO();
    private final GroupDAO groupDAO = new GroupDAO();
    private final GroupMemberDAO groupMemberDAO = new GroupMemberDAO();
    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final BalanceDAO balanceDAO = new BalanceDAO();
    private final UserSettlementQueryDAO settlementQueryDAO = new UserSettlementQueryDAO();
    private final ActivityLogQueryDAO activityLogQueryDAO = new ActivityLogQueryDAO();

    /**
     * Generate comprehensive expense report with all user information
     * and save it to the specified path
     */
    public String generateExpenseReport(String userProvidedPath) throws Exception {
        int userId = SessionManager.getCurrentUserId();
        User user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found."));

        // Validate and normalize the path
        String validatedPath = validateAndNormalizePath(userProvidedPath);
        if (validatedPath == null) {
            throw new IllegalArgumentException("Invalid path provided.");
        }

        // Create the directory if it doesn't exist
        Path dirPath = Paths.get(validatedPath);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            throw new Exception("Failed to create directory: " + e.getMessage());
        }

        // Generate the report content
        String reportContent = buildComprehensiveReport(user);

        // Create filename
        String fileName = "ExpenseReport_" + user.getUsername() + "_" + LocalDateTime.now().format(FILE_TIME) + ".txt";

        // Write file to the specified path
        Path filePath = dirPath.resolve(fileName);
        try {
            Files.writeString(filePath, reportContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new Exception("Failed to write report file: " + e.getMessage());
        }

        return filePath.toAbsolutePath().toString();
    }

    /**
     * Validate and normalize the provided path
     * Accepts both Windows (C:\...) and Unix (/home/...) style paths
     * Also accepts paths with :// notation
     */
    public String validateAndNormalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        path = path.trim();

        // Check if path contains :// (web-like path format) - convert to regular path
        if (path.contains("://")) {
            // Remove :// for file system paths
            path = path.replace("://", ":");
        }

        try {
            // Try to create a Path object
            Path pathObj = Paths.get(path);

            // Get the absolute path
            Path absolutePath = pathObj.toAbsolutePath();

            // Check if parent directory exists or can be created
            Path parentPath = absolutePath.getParent();
            if (parentPath != null) {
                // Try to verify the path is writable
                if (!Files.exists(parentPath)) {
                    // Parent doesn't exist, try to create it
                    try {
                        Files.createDirectories(parentPath);
                    } catch (IOException e) {
                        // If we can't create it now, it might be creatable later
                        // (e.g., due to permissions), so we'll allow it
                    }
                }
            }

            return absolutePath.toString();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * Build comprehensive expense report with all information
     */
    private String buildComprehensiveReport(User user) throws Exception {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("╔").append("═".repeat(68)).append("╗\n");
        sb.append("║").append(centerText("COMPREHENSIVE EXPENSE REPORT", 68)).append("║\n");
        sb.append("╚").append("═".repeat(68)).append("╝\n\n");

        // Report Information
        sb.append("Report Generated: ").append(LocalDateTime.now().format(DATE_FORMAT)).append("\n");
        sb.append("\n");

        // User Information Section
        appendUserInformation(sb, user);

        // Groups Section
        appendGroupsSection(sb, user);

        // Expenses Summary Section
        appendExpensesSummary(sb, user);

        // Balances Section
        appendBalancesSection(sb, user);

        // Settlements Section
        appendSettlementsSection(sb, user);

        // Activity Log Section
        appendActivityLogSection(sb, user);

        // Footer
        sb.append("\n");
        sb.append("╔").append("═".repeat(68)).append("╗\n");
        sb.append("║").append(centerText("END OF REPORT", 68)).append("║\n");
        sb.append("╚").append("═".repeat(68)).append("╝\n");

        return sb.toString();
    }

    /**
     * Append user information to report
     */
    private void appendUserInformation(StringBuilder sb, User user) {
        appendSectionHeader(sb, "USER INFORMATION");
        sb.append("  Name       : ").append(user.getName()).append("\n");
        sb.append("  Email      : ").append(user.getEmail()).append("\n");
        sb.append("  Username   : ").append(user.getUsername()).append("\n");
        sb.append("  Member Since: ").append(user.getCreatedAt()).append("\n");
        sb.append("\n");
    }

    /**
     * Append groups information to report
     */
    private void appendGroupsSection(StringBuilder sb, User user) throws Exception {
        appendSectionHeader(sb, "MY GROUPS");
        List<Group> groups = groupDAO.findByUserId(user.getUserId());

        if (groups.isEmpty()) {
            sb.append("  (No groups found)\n\n");
            return;
        }

        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            sb.append("  ").append(i + 1).append(". ").append(group.getGroupName())
                    .append(" (Created by: ").append(group.getCreatorName()).append(")\n");

            // Get member count
            int memberCount = groupMemberDAO.memberCount(group.getGroupId());
            sb.append("     Members: ").append(memberCount).append("\n");

            // Get expense count
            List<Expense> expenses = expenseDAO.findByGroupId(group.getGroupId());
            sb.append("     Expenses: ").append(expenses.size()).append("\n\n");
        }
        sb.append("\n");
    }

    /**
     * Append expenses summary to report
     */
    private void appendExpensesSummary(StringBuilder sb, User user) throws Exception {
        appendSectionHeader(sb, "EXPENSES SUMMARY");
        List<Group> groups = groupDAO.findByUserId(user.getUserId());

        if (groups.isEmpty()) {
            sb.append("  (No expenses found)\n\n");
            return;
        }

        int totalExpenses = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Group group : groups) {
            List<Expense> expenses = expenseDAO.findByGroupId(group.getGroupId());
            if (expenses.isEmpty()) continue;

            sb.append("  Group: ").append(group.getGroupName()).append("\n");
            sb.append("  ").append("-".repeat(64)).append("\n");

            for (Expense expense : expenses) {
                sb.append(String.format("    • %s%n", expense.getDescription()));
                sb.append(String.format("      Amount: Rs.%.2f | Category: %s%n", expense.getAmount(), expense.getCategory()));
                sb.append(String.format("      Paid by: %s | Date: %s%n", expense.getPayerName(), expense.getCreatedAt()));
                totalExpenses++;
                totalAmount = totalAmount.add(expense.getAmount());
            }
            sb.append("\n");
        }

        sb.append("  TOTAL EXPENSES: ").append(totalExpenses).append(" | Total Amount: Rs.").append(String.format("%.2f", totalAmount)).append("\n");
        sb.append("\n");
    }

    /**
     * Append balances section to report
     */
    private void appendBalancesSection(StringBuilder sb, User user) throws Exception {
        appendSectionHeader(sb, "FINANCIAL BALANCES");
        List<Group> groups = groupDAO.findByUserId(user.getUserId());

        if (groups.isEmpty()) {
            sb.append("  (No balances found)\n\n");
            return;
        }

        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalShare = BigDecimal.ZERO;
        BigDecimal netBalance = BigDecimal.ZERO;

        for (Group group : groups) {
            List<Balance> balances = balanceDAO.findByGroupId(group.getGroupId());

            for (Balance balance : balances) {
                if (balance.getUserId() == user.getUserId()) {
                    sb.append("  Group: ").append(group.getGroupName()).append("\n");
                    sb.append(String.format("    Total Paid  : Rs.%.2f%n", balance.getTotalPaid()));
                    sb.append(String.format("    Total Share : Rs.%.2f%n", balance.getTotalShare()));
                    sb.append(String.format("    Net Balance : Rs.%.2f%n", balance.getNetBalance()));

                    if (balance.getNetBalance().signum() > 0) {
                        sb.append("    Status      : YOU ARE OWED MONEY ✓\n");
                    } else if (balance.getNetBalance().signum() < 0) {
                        sb.append("    Status      : YOU OWE MONEY ✗\n");
                    } else {
                        sb.append("    Status      : SETTLED\n");
                    }
                    sb.append("\n");

                    totalPaid = totalPaid.add(balance.getTotalPaid());
                    totalShare = totalShare.add(balance.getTotalShare());
                    netBalance = netBalance.add(balance.getNetBalance());
                }
            }
        }

        sb.append("  ").append("-".repeat(64)).append("\n");
        sb.append(String.format("  OVERALL - Total Paid: Rs.%.2f | Total Share: Rs.%.2f | Net: Rs.%.2f%n",
                totalPaid, totalShare, netBalance)).append("\n");
        sb.append("\n");
    }

    /**
     * Append settlements section to report
     */
    private void appendSettlementsSection(StringBuilder sb, User user) throws Exception {
        appendSectionHeader(sb, "SETTLEMENTS");
        List<String> settlements = settlementQueryDAO.findByUserId(user.getUserId());

        if (settlements.isEmpty()) {
            sb.append("  (No settlements found)\n\n");
            return;
        }

        for (String settlement : settlements) {
            sb.append("  • ").append(settlement).append("\n");
        }
        sb.append("\n");
    }

    /**
     * Append activity log section to report
     */
    private void appendActivityLogSection(StringBuilder sb, User user) throws Exception {
        appendSectionHeader(sb, "ACTIVITY LOG");
        List<String> activities = activityLogQueryDAO.findDescriptionsByUserId(user.getUserId());

        if (activities.isEmpty()) {
            sb.append("  (No activities found)\n\n");
            return;
        }

        for (String activity : activities) {
            sb.append("  • ").append(activity).append("\n");
        }
        sb.append("\n");
    }

    /**
     * Helper method to append section header with formatting
     */
    private void appendSectionHeader(StringBuilder sb, String title) {
        sb.append("\n");
        sb.append("┌").append("─".repeat(66)).append("┐\n");
        sb.append("│ ").append(title).append(" ".repeat(Math.max(0, 64 - title.length()))).append("│\n");
        sb.append("└").append("─".repeat(66)).append("┘\n");
    }

    /**
     * Helper method to center text
     */
    private String centerText(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        int leftPad = (width - text.length()) / 2;
        int rightPad = width - text.length() - leftPad;
        return " ".repeat(leftPad) + text + " ".repeat(rightPad);
    }
}
