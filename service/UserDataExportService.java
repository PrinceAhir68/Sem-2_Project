package com.expensesplitter.service;

import com.expensesplitter.dao.*;
import com.expensesplitter.model.*;
import com.expensesplitter.utility.SessionManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class UserDataExportService {

    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final UserDAO userDAO = new UserDAO();
    private final GroupDAO groupDAO = new GroupDAO();
    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final BalanceDAO balanceDAO = new BalanceDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final ActivityLogQueryDAO activityLogQueryDAO = new ActivityLogQueryDAO();
    private final UserSettlementQueryDAO settlementQueryDAO = new UserSettlementQueryDAO();
    private final UserDataStorageDAO userDataStorageDAO = new UserDataStorageDAO();
    private final UserStoragePathDAO userStoragePathDAO = new UserStoragePathDAO();

    public String exportCurrentUserData() throws Exception {
        int userId = SessionManager.getCurrentUserId();
        User user = userDAO.findById(userId).orElseThrow(() -> new IllegalStateException("User not found."));

        String fileName = "user_" + user.getUsername() + "_" + LocalDateTime.now().format(FILE_TIME) + ".txt";
        String data = buildExportText(user);

        userDataStorageDAO.saveUserData(userId, fileName, data);

        Path exportDir = getExportDirectory(userId);
        Files.createDirectories(exportDir);
        Path filePath = exportDir.resolve(fileName);
        Files.writeString(filePath, data, StandardCharsets.UTF_8);

        return filePath.toAbsolutePath().toString();
    }

    public void saveUserStoragePath(String storagePath) throws Exception {
        int userId = SessionManager.getCurrentUserId();
        userStoragePathDAO.saveUserStoragePath(userId, storagePath, true);
    }

    public String getStoredDataPreview() throws Exception {
        return userDataStorageDAO.readUserData(SessionManager.getCurrentUserId())
                .orElse("No stored data found.");
    }

    private Path getExportDirectory(int userId) throws Exception {
        java.util.Optional<String> customPath = userStoragePathDAO.getUserStoragePath(userId);
        
        if (customPath.isPresent()) {
            return Paths.get(customPath.get());
        }
        
        return Paths.get("exports");
    }

    private String buildExportText(User user) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("=".repeat(60)).append('\n');
        sb.append("  EXPENSE SPLITTER — PERSONAL DATA EXPORT\n");
        sb.append("=".repeat(60)).append('\n');
        sb.append("Exported At : ").append(LocalDateTime.now()).append('\n');
        sb.append("Name        : ").append(user.getName()).append('\n');
        sb.append("Email       : ").append(user.getEmail()).append('\n');
        sb.append("Username    : ").append(user.getUsername()).append('\n');
        sb.append("Member Since: ").append(user.getCreatedAt()).append('\n');
        sb.append('\n');

        appendSection(sb, "MY GROUPS");
        List<Group> groups = groupDAO.findByUserId(user.getUserId());
        if (groups.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (Group group : groups) {
                sb.append("  • ").append(group.getGroupName())
                        .append(" (created by ").append(group.getCreatorName()).append(")\n");
            }
        }
        sb.append('\n');

        appendSection(sb, "MY EXPENSES (ALL GROUPS)");
        boolean hasExpenses = false;
        for (Group group : groups) {
            List<Expense> expenses = expenseDAO.findByGroupId(group.getGroupId());
            for (Expense expense : expenses) {
                hasExpenses = true;
                sb.append(String.format(
                        "  • [%s] %s — Rs.%s (%s) paid by %s%n",
                        group.getGroupName(),
                        expense.getDescription(),
                        expense.getAmount(),
                        expense.getCategory(),
                        expense.getPayerName()
                ));
            }
        }
        if (!hasExpenses) {
            sb.append("  (none)\n");
        }
        sb.append('\n');

        appendSection(sb, "MY BALANCES");
        boolean hasBalances = false;
        for (Group group : groups) {
            for (Balance balance : balanceDAO.findByGroupId(group.getGroupId())) {
                if (balance.getUserId() == user.getUserId()) {
                    hasBalances = true;
                    sb.append(String.format(
                            "  • [%s] Paid: Rs.%s | Share: Rs.%s | Net: Rs.%s%n",
                            group.getGroupName(),
                            balance.getTotalPaid(),
                            balance.getTotalShare(),
                            balance.getNetBalance()
                    ));
                }
            }
        }
        if (!hasBalances) {
            sb.append("  (none)\n");
        }
        sb.append('\n');

        appendSection(sb, "MY SETTLEMENTS");
        List<String> settlements = settlementQueryDAO.findByUserId(user.getUserId());
        appendLines(sb, settlements);

        appendSection(sb, "MY NOTIFICATIONS");
        List<Notification> notifications = notificationDAO.findAllByUserId(user.getUserId());
        if (notifications.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (Notification notification : notifications) {
                sb.append("  • [").append(notification.getCreatedAt()).append("] ")
                        .append(notification.getMessage()).append('\n');
            }
        }
        sb.append('\n');

        appendSection(sb, "MY ACTIVITY LOG");
        appendLines(sb, activityLogQueryDAO.findDescriptionsByUserId(user.getUserId()));

        sb.append('\n').append("=".repeat(60)).append('\n');
        sb.append("End of export\n");
        return sb.toString();
    }

    private void appendSection(StringBuilder sb, String title) {
        sb.append("-".repeat(60)).append('\n');
        sb.append(title).append('\n');
        sb.append("-".repeat(60)).append('\n');
    }

    private void appendLines(StringBuilder sb, List<String> lines) {
        if (lines.isEmpty()) {
            sb.append("  (none)\n");
            return;
        }
        for (String line : lines) {
            sb.append("  • ").append(line).append('\n');
        }
    }
}
