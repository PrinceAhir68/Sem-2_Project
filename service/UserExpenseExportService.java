package com.expensesplitter.service;

import com.expensesplitter.dao.*;
import com.expensesplitter.model.*;
import com.expensesplitter.utility.SessionManager;

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

/**
 * Service for exporting user expense data to files with custom paths.
 * Provides validation, normalization, and export functionality.
 */
public class UserExpenseExportService {

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
     * Export all user expense information to a file at the specified path
     * @param userProvidedPath The directory path where to save the file
     * @return The absolute path of the created file
     * @throws Exception If validation, directory creation, or file writing fails
     */
    public String exportUserExpensesWithPath(String userProvidedPath) throws Exception {
        int userId = SessionManager.getCurrentUserId();
        User user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found."));

        // Validate and normalize the path
        String validatedPath = validateAndNormalizePath(userProvidedPath);
        if (validatedPath == null) {
            throw new IllegalArgumentException("Invalid path format.");
        }

        // Create the directory if it doesn't exist
        Path dirPath = Paths.get(validatedPath);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            throw new Exception("Failed to create directory: " + e.getMessage());
        }

        // Build the comprehensive export content
        String exportContent = buildCompleteExpenseExport(user);

        // Create filename with timestamp
        String fileName = "ExpenseExport_" + user.getUsername() + "_" + LocalDateTime.now().format(FILE_TIME) + ".txt";

        // Write file to the specified path
        Path filePath = dirPath.resolve(fileName);
        try {
            Files.writeString(filePath, exportContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new Exception("Failed to write export file: " + e.getMessage());
        }

        return filePath.toAbsolutePath().toString();
    }

    /**
     * Validate and normalize the provided file path
     * Accepts multiple path formats:
     * - Windows: C:\Path\To\Directory or C://Path/To/Directory
     * - Unix: /home/user/path or /home/user/path://
     * @param path The path string to validate
     * @return Normalized absolute path, or null if invalid
     */
    public String validateAndNormalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        path = path.trim();

        // Handle :// notation by converting to standard path format
        if (path.contains("://")) {
            path = path.replace("://", ":");
        }

        try {
            // Create a Path object to validate syntax
            Path pathObj = Paths.get(path);
            Path absolutePath = pathObj.toAbsolutePath();

            // Verify parent directory
            Path parentPath = absolutePath.getParent();
            if (parentPath != null) {
                // Try to create parent directory if it doesn't exist
                if (!Files.exists(parentPath)) {
                    try {
                        Files.createDirectories(parentPath);
                    } catch (IOException e) {
                        // Continue anyway - directory might be creatable later
                    }
                }
            }

            return absolutePath.toString();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * Build a complete expense export with all user information
     * @param user The user to generate export for
     * @return Complete export text
     * @throws Exception If data retrieval fails
     */
    public String buildCompleteExpenseExport(User user) throws Exception {
        StringBuilder sb = new StringBuilder();

        // Header with timestamp
        sb.append("╔").append("═".repeat(70)).append("╗\n");
        sb.append("║").append(centerText("EXPENSE REPORT EXPORT", 70)).append("║\n");
        sb.append("║").append(centerText("All Information with Expenses", 70)).append("║\n");
        sb.append("╚").append("═".repeat(70)).append("╝\n\n");

        sb.append("Export Generated: ").append(LocalDateTime.now().format(DATE_FORMAT)).append("\n\n");

        // User Information
        appendUserInfo(sb, user);

        // Groups and Members
        appendGroupsInfo(sb, user);

        // All Expenses
        appendAllExpenses(sb, user);

        // Financial Balances
        appendFinancialBalances(sb, user);

        // Settlement Information
        appendSettlementInfo(sb, user);

        // Activity Logs
        appendActivityLogs(sb, user);

        // Footer
        sb.append("\n");
        sb.append("╔").append("═".repeat(70)).append("╗\n");
        sb.append("║").append(centerText("END OF EXPENSE REPORT", 70)).append("║\n");
        sb.append("╚").append("═".repeat(70)).append("╝\n");

        return sb.toString();
    }

    private void appendUserInfo(StringBuilder sb, User user) {
        appendHeader(sb, "USER INFORMATION");
        sb.append("  Name            : ").append(user.getName()).append("\n");
        sb.append("  Email           : ").append(user.getEmail()).append("\n");
        sb.append("  Username        : ").append(user.getUsername()).append("\n");
        sb.append("  Member Since    : ").append(user.getCreatedAt()).append("\n");
        sb.append("\n");
    }

    private void appendGroupsInfo(StringBuilder sb, User user) throws Exception {
        appendHeader(sb, "GROUPS AND MEMBERS");
        List<Group> groups = groupDAO.findByUserId(user.getUserId());

        if (groups.isEmpty()) {
            sb.append("  (No groups found)\n\n");
            return;
        }

        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            sb.append(String.format("  %d. %s%n", i + 1, group.getGroupName()));
            sb.append("     Creator: ").append(group.getCreatorName()).append("\n");
            
            int memberCount = groupMemberDAO.memberCount(group.getGroupId());
            sb.append("     Members: ").append(memberCount).append("\n");
            
            List<User> members = groupMemberDAO.getMembers(group.getGroupId());
            for (User member : members) {
                sb.append("       • ").append(member.getName()).append(" (").append(member.getEmail()).append(")\n");
            }
            sb.append("\n");
        }
    }

    private void appendAllExpenses(StringBuilder sb, User user) throws Exception {
        appendHeader(sb, "ALL EXPENSES");
        List<Group> groups = groupDAO.findByUserId(user.getUserId());

        if (groups.isEmpty()) {
            sb.append("  (No expenses found)\n\n");
            return;
        }

        int totalCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Group group : groups) {
            List<Expense> expenses = expenseDAO.findByGroupId(group.getGroupId());
            if (expenses.isEmpty()) continue;

            sb.append("  Group: ").append(group.getGroupName()).append("\n");
            sb.append("  ").append("─".repeat(68)).append("\n");

            for (Expense expense : expenses) {
                sb.append(String.format("    • %s%n", expense.getDescription()));
                sb.append(String.format("      Amount: Rs.%.2f | Category: %s%n", expense.getAmount(), expense.getCategory()));
                sb.append(String.format("      Paid by: %s | Date: %s%n", expense.getPayerName(), expense.getCreatedAt()));
                totalCount++;
                totalAmount = totalAmount.add(expense.getAmount());
            }
            sb.append("\n");
        }

        sb.append("  ").append("─".repeat(68)).append("\n");
        sb.append(String.format("  TOTAL: %d expenses | Total Amount: Rs.%.2f%n%n", totalCount, totalAmount));
    }

    private void appendFinancialBalances(StringBuilder sb, User user) throws Exception {
        appendHeader(sb, "FINANCIAL BALANCES");
        List<Group> groups = groupDAO.findByUserId(user.getUserId());

        if (groups.isEmpty()) {
            sb.append("  (No balances found)\n\n");
            return;
        }

        BigDecimal overallPaid = BigDecimal.ZERO;
        BigDecimal overallShare = BigDecimal.ZERO;
        BigDecimal overallBalance = BigDecimal.ZERO;

        for (Group group : groups) {
            List<Balance> balances = balanceDAO.findByGroupId(group.getGroupId());

            for (Balance balance : balances) {
                if (balance.getUserId() == user.getUserId()) {
                    sb.append("  Group: ").append(group.getGroupName()).append("\n");
                    sb.append(String.format("    Total Paid  : Rs.%.2f%n", balance.getTotalPaid()));
                    sb.append(String.format("    Total Share : Rs.%.2f%n", balance.getTotalShare()));
                    sb.append(String.format("    Net Balance : Rs.%.2f%n", balance.getNetBalance()));

                    String status;
                    if (balance.getNetBalance().signum() > 0) {
                        status = "YOU ARE OWED MONEY ✓";
                    } else if (balance.getNetBalance().signum() < 0) {
                        status = "YOU OWE MONEY ✗";
                    } else {
                        status = "SETTLED ✓";
                    }
                    sb.append("    Status      : ").append(status).append("\n\n");

                    overallPaid = overallPaid.add(balance.getTotalPaid());
                    overallShare = overallShare.add(balance.getTotalShare());
                    overallBalance = overallBalance.add(balance.getNetBalance());
                }
            }
        }

        sb.append("  ").append("─".repeat(68)).append("\n");
        sb.append(String.format("  OVERALL BALANCE: Paid Rs.%.2f | Share Rs.%.2f | Net Rs.%.2f%n%n",
                overallPaid, overallShare, overallBalance));
    }

    private void appendSettlementInfo(StringBuilder sb, User user) throws Exception {
        appendHeader(sb, "SETTLEMENT INFORMATION");
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

    private void appendActivityLogs(StringBuilder sb, User user) throws Exception {
        appendHeader(sb, "ACTIVITY LOG");
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

    private void appendHeader(StringBuilder sb, String title) {
        sb.append("┌").append("─".repeat(68)).append("┐\n");
        sb.append("│ ").append(title).append(" ".repeat(Math.max(0, 66 - title.length()))).append("│\n");
        sb.append("└").append("─".repeat(68)).append("┘\n");
    }

    private String centerText(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        int leftPad = (width - text.length()) / 2;
        int rightPad = width - text.length() - leftPad;
        return " ".repeat(leftPad) + text + " ".repeat(rightPad);
    }
}
