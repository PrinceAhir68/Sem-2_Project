package com.expensesplitter.menu;


import com.expensesplitter.algorithm.SettlementSuggestion;
import com.expensesplitter.database.DBConnection;
import com.expensesplitter.model.*;
import com.expensesplitter.service.*;
import com.expensesplitter.utility.ConsoleHelper;
import com.expensesplitter.utility.InputValidator;
import com.expensesplitter.utility.SessionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class GroupMenu {

    private static final String[] CATEGORIES = {
            "food", "travel", "hotel", "shopping", "entertainment", "other"
    };

    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private final GroupService groupService = new GroupService();
    private final AuthenticationService authService = new AuthenticationService();
    private final ExpenseService expenseService = new ExpenseService();
    private final BalanceService balanceService = new BalanceService();
    private final DebtSimplificationService debtService = new DebtSimplificationService();
    private final SettlementService settlementService = new SettlementService();
    private final ReportService reportService = new ReportService();
    private final UserReportService userReportService = new UserReportService();

    public void show(Group group) throws Exception {
        while (true) {
            ConsoleHelper.printHeader("Group: " + group.getGroupName());
            System.out.println("  1. Add Expense");
            System.out.println("  2. View Expenses");
            System.out.println("  3. View Members");
            System.out.println("  4. Simplify Debts  CORE ALGORITHM");
            System.out.println("  5. Pending Settlements");
            System.out.println("  6. Settlement History");
            System.out.println("  7. Reports");
            System.out.println("  8. Group Settings");
            System.out.println("  9. Pending Custom Splits");
            System.out.println("  10. search by Expense amount");
            System.out.println("  0. Back");
            System.out.println("=".repeat(50));

            int choice = ConsoleHelper.readChoice("Choose option: ", 0, 10);

            switch (choice) {
                case 1 -> addExpense(group.getGroupId());
                case 2 -> viewExpenses(group.getGroupId());
                case 3 -> viewMembers(group.getGroupId());
                case 4 -> simplifyDebts(group.getGroupId());
                case 5 -> pendingSettlements(group.getGroupId());
                case 6 -> settlementHistory(group.getGroupId());
                case 7 -> reports(group.getGroupId());
                case 8 -> groupSettings(group);
                case 9 -> pendingCustomSplits(group.getGroupId());
            case  10 -> serchByExpenses();
                case 0 -> { return; }
            }
        }
    }

    private void addExpense(int groupId) throws Exception {
        List<User> members = groupService.getMembers(groupId);
        ConsoleHelper.printHeader("Add Expense");

        if (members.size() < 2) {
            ConsoleHelper.printError("Add at least one more member before creating an expense.");
            ConsoleHelper.pause();
            return;
        }

        int payerChoice = selectPayer(members);
        BigDecimal amount = ConsoleHelper.readAmount("Amount  : ");
        String category = selectCategory();
        String description = readValidDescription("Description : ");
        String splitType = selectSplitType();
        List<Integer> selectedIds = selectMembersForSplit(members);

        SplitPlan splitPlan = prepareSplitPlan(amount, splitType, selectedIds, members);
        if (splitPlan == null) {
            ConsoleHelper.pause();
            return;
        }

        Map<Integer, BigDecimal> shares = splitPlan.shares();
        boolean distributedCustom = splitPlan.distributedCustom();
        BigDecimal creatorShare = splitPlan.creatorShare();
        int paidBy = members.get(payerChoice - 1).getUserId();

        for (Map.Entry<Integer, BigDecimal> entry : shares.entrySet()) {
            String name = members.stream().filter(m -> m.getUserId() == entry.getKey())
                    .map(User::getName).findFirst().orElse("?");
            System.out.printf("      %s share: ‚%s%n", name, entry.getValue());
        }
        if (distributedCustom) {
            System.out.printf("      Remaining pending: ‚%s%n",
                    amount.subtract(creatorShare).setScale(2, RoundingMode.HALF_UP));
        }

        int changeAttempts = 0;
        final int MAX_CHANGES = 3;

        while (true) {
            printExpenseSummary(amount, category, description, splitType, payerChoice, shares, members);
            if (distributedCustom) {
                System.out.println("    Custom mode : Let each member add their own share");
                System.out.println("    Remaining   : " + amount.subtract(creatorShare).setScale(2, RoundingMode.HALF_UP));
            }
            boolean confirmed = ConsoleHelper.confirmDialog(
                    "Confirm Add Expense", "Are you sure you want to add this expense?");
            if (confirmed) {
                break;
            }

            if (changeAttempts >= MAX_CHANGES) {
                ConsoleHelper.printInfo("You have reached the maximum number of allowed changes (" + MAX_CHANGES + ").");
                System.out.println("  1. Save the expense with the current details");
                System.out.println("  2. Discard this expense");
                int finalChoice = ConsoleHelper.readChoice("Choose: ", 1, 2);
                if (finalChoice == 1) {
                    break;
                } else {
                    ConsoleHelper.popupInfo("Discarded", "Expense was not saved. No database changes were made.");
                    ConsoleHelper.pause();
                    return;
                }
            }

            System.out.println("\nExpense was not saved. What would you like to change?");
            ConsoleHelper.printInfo("You can modify this expense up to " + MAX_CHANGES + " times. Attempt "
                    + (changeAttempts + 1) + " of " + MAX_CHANGES + ".");
            System.out.println("  1. Modify expense amount");
            System.out.println("  2. Modify split type");
            System.out.println("  3. Change payer");
            System.out.println("  4. Change category and description");
            System.out.println("  5. Change split among members");
            System.out.println("  6. Change custom split entry mode");
            System.out.println("  7. Discard this expense");
            int change = ConsoleHelper.readChoice("Choose: ", 1, 7);
            switch (change) {
                case 1 -> amount = ConsoleHelper.readAmount("New amount: ");
                case 2 -> splitType = selectSplitType();
                case 3 -> payerChoice = selectPayer(members);
                case 4 -> {
                    category = selectCategory();
                    description = readValidDescription("Description: ");
                }
                case 5 -> selectedIds = selectMembersForSplit(members);
                case 6 -> {
                    if (!"custom".equals(splitType)) {
                        ConsoleHelper.printInfo("Custom split mode applies only when split type is Exact / Custom.");
                    }
                }
                case 7 -> {
                    ConsoleHelper.popupInfo("Discarded", "Expense was not saved. No database changes were made.");
                    ConsoleHelper.pause();
                    return;
                }
            }
            if (change != 7) {
                changeAttempts++;
            }
            if (change == 1 || change == 2 || change == 5 || change == 6) {
                splitPlan = prepareSplitPlan(amount, splitType, selectedIds, members);
                if (splitPlan == null) {
                    ConsoleHelper.pause();
                    return;
                }
                shares = splitPlan.shares();
                distributedCustom = splitPlan.distributedCustom();
                creatorShare = splitPlan.creatorShare();
            }
        }

        paidBy = members.get(payerChoice - 1).getUserId();
        String error = distributedCustom
                ? expenseService.addDistributedCustomExpense(groupId, paidBy, amount, description, category,
                selectedIds, creatorShare)
                : expenseService.addExpense(groupId, paidBy, amount, description, category, splitType, shares);

        if (error != null) {
            ConsoleHelper.printError(error);
            ConsoleHelper.pause();
            return;
        }

        if (distributedCustom && amount.subtract(creatorShare).compareTo(BigDecimal.ZERO) > 0) {
            ConsoleHelper.popupSuccess(
                    "Pending Custom Split Saved (COMMIT)",
                    "Expense saved. Shares stay pending until fully allocated or finished by the creator."
            );
        } else {
            ConsoleHelper.popupSuccess(
                    "Expense Saved (COMMIT)",
                    "Expense added successfully. Balances updated and history logged in one transaction."
            );
        }
        for (Map.Entry<Integer, BigDecimal> entry : shares.entrySet()) {
            String name = members.stream().filter(m -> m.getUserId() == entry.getKey())
                    .map(User::getName).findFirst().orElse("?");
            System.out.printf("   %s share: ‚%s%n", name, entry.getValue());
        }
    }

    private int selectPayer(List<User> members) {
        System.out.println("Who paid?");
        for (int i = 0; i < members.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, members.get(i).getName());
        }
        return ConsoleHelper.readChoice("Payer: ", 1, members.size());
    }

    private List<Integer> selectMembersForSplit(List<User> members) {
        System.out.println("Split among (enter member numbers separated by comma, e.g. 1,2,3):");
        for (int i = 0; i < members.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, members.get(i).getName());
        }
        while (true) {
            List<Integer> selectedIds = parseMemberSelection(ConsoleHelper.readRequired("Members: "), members);
            if (selectedIds != null && !selectedIds.isEmpty()) {
                return selectedIds;
            }
            ConsoleHelper.printError("Please re-enter member numbers like 1,2,3.");
        }
    }

    private String readValidDescription(String prompt) {
        while (true) {
            String description = ConsoleHelper.readLine(prompt);
            if (InputValidator.isValidDescription(description)) {
                return description.trim();
            }
            ConsoleHelper.printError(InputValidator.descriptionRuleHint());
        }
    }

    private void printExpenseSummary(BigDecimal amount, String category, String description,
                                     String splitType, int payerChoice, Map<Integer, BigDecimal> shares,
                                     List<User> members) {
        System.out.println("\n  Summary:");
        System.out.println("    Amount      : " + amount);
        System.out.println("    Category    : " + category);
        System.out.println("    Description : " + description);
        System.out.println("    Split type  : " + splitType);
        System.out.println("    Paid by     : " + members.get(payerChoice - 1).getName());
        for (Map.Entry<Integer, BigDecimal> entry : shares.entrySet()) {
            String name = members.stream().filter(m -> m.getUserId() == entry.getKey())
                    .map(User::getName).findFirst().orElse("?");
            System.out.printf("      %s share: %s%n", name, entry.getValue());
        }
    }

    private Map<Integer, BigDecimal> buildShares(BigDecimal amount, String splitType,
                                                 List<Integer> memberIds, List<User> members) {
        return switch (splitType) {
            case "equal" -> expenseService.buildEqualSplit(amount, memberIds);
            case "exact", "custom" -> buildCustomShares(amount, memberIds, members);
            case "percentage" -> buildPercentageShares(amount, memberIds, members);
            default -> null;
        };
    }

    private SplitPlan prepareSplitPlan(BigDecimal amount, String splitType,
                                       List<Integer> memberIds, List<User> members) {
        if ("custom".equals(splitType) && members.size() > 10) {
            ConsoleHelper.printInfo("Large groups are usually easier with Equal or Percentage split.");
            boolean proceed = ConsoleHelper.readYesNo("Do you still want to continue with Custom Split? (yes/no): ");
            if (!proceed) {
                return null;
            }
        }
        if (!"custom".equals(splitType)) {
            return new SplitPlan(false, buildShares(amount, splitType, memberIds, members), BigDecimal.ZERO);
        }

        System.out.println("Custom Split:");
        System.out.println("  1. I'll enter everyone's shares myself right now");
        System.out.println("  2. Let each member add their own share");
        int mode = ConsoleHelper.readChoice("Choose: ", 1, 2);
        if (mode == 1) {
            return new SplitPlan(false, buildCustomShares(amount, memberIds, members), BigDecimal.ZERO);
        }

        int actorId = SessionManager.getCurrentUserId();
        if (!memberIds.contains(actorId)) {
            ConsoleHelper.printError("The expense creator must be included in this distributed custom split.");
            return null;
        }

        String creatorName = members.stream()
                .filter(member -> member.getUserId() == actorId)
                .map(User::getName)
                .findFirst()
                .orElse("Your");
        BigDecimal suggested = amount.divide(BigDecimal.valueOf(memberIds.size()), 2, RoundingMode.HALF_UP);
        BigDecimal creatorShare = readShareAmountWithCap(
                creatorName + " share [max " + amount.setScale(2, RoundingMode.HALF_UP) + "]: ",
                suggested,
                amount,
                true
        );

        Map<Integer, BigDecimal> shares = new LinkedHashMap<>();
        shares.put(actorId, creatorShare);
        return new SplitPlan(true, shares, creatorShare);
    }

    private Map<Integer, BigDecimal> buildCustomShares(BigDecimal amount, List<Integer> memberIds, List<User> members) {
        while (true) {
            Map<Integer, BigDecimal> shares = new LinkedHashMap<>();
            BigDecimal sum = BigDecimal.ZERO;
            for (int id : memberIds) {
                String name = members.stream().filter(m -> m.getUserId() == id).map(User::getName).findFirst().orElse("?");
                BigDecimal share = ConsoleHelper.readAmount(name + " share : ");
                shares.put(id, share);
                sum = sum.add(share);
            }
            if (sum.subtract(amount).abs().compareTo(new BigDecimal("0.01")) <= 0) {
                return shares;
            }
            ConsoleHelper.printError("Shares sum to " + sum + " but expense is " + amount + " Re-enter shares.");
        }
    }

    private Map<Integer, BigDecimal> buildPercentageShares(BigDecimal amount, List<Integer> memberIds, List<User> members) {
        while (true) {
            Map<Integer, Integer> percentages = new LinkedHashMap<>();
            int totalPct = 0;
            for (int id : memberIds) {
                String name = members.stream().filter(m -> m.getUserId() == id).map(User::getName).findFirst().orElse("?");
                int pct = ConsoleHelper.readChoice(name + " percentage (0-100): ", 0, 100);
                percentages.put(id, pct);
                totalPct += pct;
            }
            if (totalPct != 100) {
                ConsoleHelper.printError("Percentages must sum to 100 (got " + totalPct + "). Re-enter.");
                continue;
            }

            Map<Integer, BigDecimal> shares = new LinkedHashMap<>();
            BigDecimal allocated = BigDecimal.ZERO;
            int i = 0;
            for (Map.Entry<Integer, Integer> entry : percentages.entrySet()) {
                i++;
                if (i == percentages.size()) {
                    shares.put(entry.getKey(), amount.subtract(allocated));
                } else {
                    BigDecimal share = amount.multiply(BigDecimal.valueOf(entry.getValue()))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    shares.put(entry.getKey(), share);
                    allocated = allocated.add(share);
                }
            }
            return shares;
        }
    }

    private BigDecimal readShareAmountWithCap(String prompt, BigDecimal suggested, BigDecimal maxAmount, boolean allowZero) {
        while (true) {
            String input = ConsoleHelper.readLine(prompt + "Suggested: " + suggested.setScale(2, RoundingMode.HALF_UP) + " ");
            if (input.isEmpty()) {
                return suggested.setScale(2, RoundingMode.HALF_UP);
            }
            try {
                BigDecimal amount = new BigDecimal(input).setScale(2, RoundingMode.HALF_UP);
                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    ConsoleHelper.printError("Amount cannot be negative.");
                    continue;
                }
                if (!allowZero && amount.compareTo(BigDecimal.ZERO) == 0) {
                    ConsoleHelper.printError("Amount must be greater than zero.");
                    continue;
                }
                if (amount.compareTo(maxAmount) > 0) {
                    ConsoleHelper.printError("Amount should be less than " + maxAmount.setScale(2, RoundingMode.HALF_UP) + ".");
                    continue;
                }
                return amount;
            } catch (NumberFormatException e) {
                ConsoleHelper.printError("Please enter a valid amount (e.g. 500 or 500.50).");
            }
        }
    }

    /**
     * Parses a comma-separated member selection. Any invalid token rejects the entire input
     * (returns {@code null}); partial lists are never returned.
     */
    private List<Integer> parseMemberSelection(String input, List<User> members) {
        Set<Integer> ids = new LinkedHashSet<>();
        String[] parts = input.split(",", -1);
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) {
                ConsoleHelper.printError("Invalid selection: empty value between commas. Enter numbers like 1,2,3.");
                return null;
            }
            int num;
            try {
                num = Integer.parseInt(token);
            } catch (NumberFormatException e) {
                ConsoleHelper.printError("Invalid token \"" + token + "\": must be a member number.");
                return null;
            }
            int idx = num - 1;
            if (idx < 0 || idx >= members.size()) {
                ConsoleHelper.printError("Invalid member number " + num
                        + ": must be between 1 and " + members.size() + ".");
                return null;
            }
            ids.add(members.get(idx).getUserId());
        }
        if (ids.isEmpty()) {
            return null;
        }
        return new ArrayList<>(ids);
    }

    private String selectCategory() {
        System.out.println("Category:");
        System.out.println("  1. Food");
        System.out.println("  2. Travel");
        System.out.println("  3. Hotel");
        System.out.println("  4. Shopping");
        System.out.println("  5. Entertainment");
        System.out.println("  6. Other");
        int c = ConsoleHelper.readChoice("Category (1-6): ", 1, 6);
        return CATEGORIES[c - 1];
    }

    private String selectSplitType() {
        System.out.println("Split type:");
        System.out.println("  1. Equal");
        System.out.println("  2. Exact / Custom");
        System.out.println("  3. Percentage");
        int s = ConsoleHelper.readChoice("Split type (1-3): ", 1, 3);
        return switch (s) {
            case 2 -> "custom";
            case 3 -> "percentage";
            default -> "equal";
        };
    }

    private void viewExpenses(int groupId) throws Exception {
        List<Expense> expenses = expenseService.getExpenses(groupId);
        ConsoleHelper.printHeader("Expense History");

        if (expenses.isEmpty()) {
            ConsoleHelper.printInfo("No expenses yet.");
            ConsoleHelper.pause();
            return;
        }

        for (Expense e : expenses) {
            System.out.printf("  [%d] %s ” %s | Paid by: %s | %s | %s%n",
                    e.getExpenseId(), e.getDescription(), e.getAmount(),
                    e.getPayerName(), e.getCategory(), e.getSplitType());
        }
        ConsoleHelper.pause();
    }

    /**
     * Edit expense flow: collect new values â†’ confirmation dialog â†’
     * transactional update (COMMIT) or cancel (no save / ROLLBACK path unused if never started).
     * currently unused from the UI â€” kept for potential future use
     */
    private void editExpense(int groupId, int expenseId) throws Exception {
        List<User> members = groupService.getMembers(groupId);
        Expense current = expenseService.getExpense(expenseId)
                .orElseThrow(() -> new IllegalStateException("Expense not found."));

        ConsoleHelper.printHeader("Edit Expense #" + expenseId);
        System.out.println("  Current: " + current.getDescription()
                + " | " + current.getAmount()
                + " | " + current.getCategory()
                + " | paid by " + current.getPayerName());
        System.out.println();

        System.out.println("Who paid?");
        for (int i = 0; i < members.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, members.get(i).getName());
        }
        int payerChoice = ConsoleHelper.readChoice("Payer: ", 1, members.size());
        BigDecimal amount = ConsoleHelper.readAmount("New amount : ");
        String category = selectCategory();
        String description = readValidDescription("New description: ");
        String splitType = selectSplitType();

        System.out.println("Split among (enter member numbers separated by comma, e.g. 1,2,3):");
        for (int i = 0; i < members.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, members.get(i).getName());
        }

        List<Integer> selectedIds;
        while (true) {
            String memberInput = ConsoleHelper.readRequired("Members: ");
            selectedIds = parseMemberSelection(memberInput, members);
            if (selectedIds != null && !selectedIds.isEmpty()) {
                break;
            }
            ConsoleHelper.printError("Please re-enter member numbers like 1,2,3.");
        }

        Map<Integer, BigDecimal> shares = buildShares(amount, splitType, selectedIds, members);
        if (shares == null) {
            ConsoleHelper.pause();
            return;
        }

        int paidBy = members.get(payerChoice - 1).getUserId();

        boolean save = ConsoleHelper.confirmDialog(
                "Save Changes?",
                "Do you want to save the changes?"
        );
        if (!save) {
            ConsoleHelper.popupInfo(
                    "Changes Discarded",
                    "Update cancelled. Database was not modified (equivalent to ROLLBACK / no transaction)."
            );
            ConsoleHelper.pause();
            return;
        }

        String error = expenseService.updateExpense(
                expenseId, paidBy, amount, description, category, splitType, shares);

        if (error != null) {
            ConsoleHelper.printError(error);
            ConsoleHelper.pause();
            return;
        }

        ConsoleHelper.popupSuccess(
                "Saved (COMMIT)",
                "Expense updated. Balances recalculated and \"Expense Updated\" written to history."
        );
    }

    private void viewMembers(int groupId) throws Exception {
        List<User> members = groupService.getMembers(groupId);
        ConsoleHelper.printHeader("Members");
        for (User m : members) {
            System.out.printf("  %s (@%s)%n", m.getName(), m.getUsername());
        }
        ConsoleHelper.pause();
    }

    private void simplifyDebts(int groupId) throws Exception {
        ConsoleHelper.printHeader("Debt Simplification (Greedy + Max Heap)");

        balanceService.recalculateBalances(groupId);

        List<SettlementSuggestion> preview = debtService.preview(groupId);
        if (preview.isEmpty()) {
            ConsoleHelper.printSuccess("Everyone is already settled! No payments needed.");
            maybeShowBalances(groupId);
            ConsoleHelper.pause();
            return;
        }

        System.out.println("\n  Preview Suggested Settlements:");
        System.out.println("  " + "-".repeat(40));
        for (SettlementSuggestion s : preview) {
            System.out.println("  " + s);
        }
        System.out.println("  " + "-".repeat(40));

        List<SettlementSuggestion> suggestions = debtService.simplifyAndSave(groupId);
        ConsoleHelper.popupSuccess(
                "Debts Simplified (COMMIT)",
                suggestions.size() + " settlement(s) saved. Pending list updated in one transaction."
        );
        for (SettlementSuggestion s : suggestions) {
            System.out.println( s);
        }

        maybeShowBalances(groupId);
    }

    private void maybeShowBalances(int groupId) throws Exception {
        boolean wantsBalances = ConsoleHelper.readYesNo("Do you want to view current balances? (y/n): ");
        if (!wantsBalances) {
            return;
        }
        List<Balance> balances = balanceService.getBalances(groupId);
        ConsoleHelper.printHeader("Current Balances");
        for (Balance b : balances) {
            String sign = b.getNetBalance().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
            String status = b.getNetBalance().compareTo(BigDecimal.ZERO) > 0 ? "(owed money)"
                    : b.getNetBalance().compareTo(BigDecimal.ZERO) < 0 ? "(owes money)" : "(settled)";
            System.out.printf("  %-15s : %s %-8s  Paid: %-8s  Share: %-8s  %s%n",
                    b.getUserName(), sign, b.getNetBalance(),
                    b.getTotalPaid(), b.getTotalShare(), status);
        }
    }

    private void pendingSettlements(int groupId) throws Exception {
        List<Settlement> pending = settlementService.getPending(groupId);
        ConsoleHelper.printHeader("Pending Settlements");

        if (pending.isEmpty()) {
            ConsoleHelper.printInfo("No pending settlements. Run 'Simplify Debts' first.");
        } else {
            for (Settlement s : pending) {
                System.out.printf("  [%d] %s pays %s %s%n",
                        s.getSettlementId(), s.getFromUserName(), s.getToUserName(), s.getAmount());
            }
            System.out.println("\n  1. For Skip this settlement.");
            System.out.println("  2. For Make Payment. ");
            int action = ConsoleHelper.readChoice("Choose: ", 1, 2);
            if (action == 1) {
                ConsoleHelper.printInfo("This Settlement is Pending");
            } else {
                int id = ConsoleHelper.readInt("Mark settlement ID as paid (0 to skip): ");
                if (id > 0) {
                    boolean ok = ConsoleHelper.confirmDialog(
                            "Confirm Payment",
                            "Is settlement " + id + " correct, and are you sure you want to make this payment?"
                    );
                    if (!ok) {
                        ConsoleHelper.popupInfo("Cancelled", "Settlement not updated. No database changes.");
                    } else {
                        String err = settlementService.markAsPaid(id);
                        if (err != null) {
                            ConsoleHelper.printError(err);
                        } else {
                            ConsoleHelper.popupSuccess("Paid (COMMIT)", "Settlement marked as paid in one transaction.");
                            ConsoleHelper.pause();
                            return;
                        }
                    }
                }
            }
        }
        ConsoleHelper.pause();
    }

    private void pendingCustomSplits(int groupId) throws Exception {
        List<PendingCustomSplitView> pendingSplits = expenseService.getPendingCustomSplits(groupId);
        ConsoleHelper.printHeader("Pending Custom Splits");

        if (pendingSplits.isEmpty()) {
            ConsoleHelper.printInfo("No pending custom splits.");
            ConsoleHelper.pause();
            return;
        }

        for (PendingCustomSplitView view : pendingSplits) {
            Expense expense = view.getExpense();
            ExpenseSplit nextPending = view.getNextPendingSplit();
            System.out.printf("  [%d] %s | %s | Remaining: %s%n",
                    expense.getExpenseId(), expense.getDescription(), expense.getAmount(), expense.getRemainingAmount());
            if (nextPending != null) {
                System.out.println("      Next turn: " + nextPending.getUserName());
            }
            for (ExpenseSplit split : view.getSplits()) {
                System.out.printf("      %s -> %s [%s]%n",
                        split.getUserName(), split.getShareAmount(), split.getContributionStatus());
            }
        }

        System.out.println();
        System.out.println("  1. Add My Share");
        System.out.println("  2. Finish this pending split now");
        System.out.println("  0. Back");
        int action = ConsoleHelper.readChoice("Choose: ", 0, 2);
        if (action == 0) {
            return;
        }

        int expenseId = ConsoleHelper.readInt("Expense ID: ");
        PendingCustomSplitView selected = pendingSplits.stream()
                .filter(view -> view.getExpense().getExpenseId() == expenseId)
                .findFirst()
                .orElse(null);
        if (selected == null) {
            ConsoleHelper.printError("Expense not found.");
            ConsoleHelper.pause();
            return;
        }

        if (action == 1) {
            int actorId = SessionManager.getCurrentUserId();
            ExpenseSplit mySplit = selected.getSplits().stream()
                    .filter(split -> split.getUserId() == actorId)
                    .findFirst()
                    .orElse(null);
            if (mySplit == null || !"pending".equals(mySplit.getContributionStatus())) {
                ConsoleHelper.printError("You do not have a pending share for this expense.");
                ConsoleHelper.pause();
                return;
            }
            if (selected.getNextPendingSplit() != null && selected.getNextPendingSplit().getUserId() != actorId) {
                ConsoleHelper.printError("It is not your turn to contribute to this expense yet.");
                ConsoleHelper.pause();
                return;
            }

            int pendingCount = Math.max(1, selected.getPendingParticipantCount());
            BigDecimal suggestion = selected.getExpense().getRemainingAmount()
                    .divide(BigDecimal.valueOf(pendingCount), 2, RoundingMode.HALF_UP);
            String sharePrompt = pendingCount == 1
                    ? "Your share  [enter exact "
                    + selected.getExpense().getRemainingAmount().setScale(2, RoundingMode.HALF_UP) + "]: "
                    : "Your share  [max "
                    + selected.getExpense().getRemainingAmount().setScale(2, RoundingMode.HALF_UP) + "]: ";
            BigDecimal share = readShareAmountWithCap(
                    sharePrompt,
                    suggestion,
                    selected.getExpense().getRemainingAmount(),
                    true
            );

            ExpenseService.ShareSubmissionResult result = expenseService.submitDistributedShare(expenseId, share);
            if (result.error() != null) {
                ConsoleHelper.printError(result.error());
            } else {
                if (result.infoMessage() != null) {
                    System.out.println(result.infoMessage());
                } else {
                    ConsoleHelper.printSuccess("Share saved.");
                }
                for (String message : result.autoRemovedMessages()) {
                    System.out.println(message);
                }
            }
            ConsoleHelper.pause();
            return;
        }

        int actorId = SessionManager.getCurrentUserId();
        if (selected.getExpense().getCreatedBy() != actorId) {
            ConsoleHelper.printError("Only the expense creator can finish this pending split now.");
            ConsoleHelper.pause();
            return;
        }

        System.out.println("  1. Split the remaining amount equally among members who haven't contributed yet");
        System.out.println("  2. Cancel the expense entirely");
        System.out.println("  0. Back");
        int finishChoice = ConsoleHelper.readChoice("Choose: ", 0, 2);
        if (finishChoice == 0) {
            return;
        }

        String error = expenseService.finalizeDistributedSplitNow(expenseId, finishChoice == 1);
        if (error != null) {
            ConsoleHelper.printError(error);
        } else if (finishChoice == 1) {
            ConsoleHelper.popupSuccess("Pending Split Finalized (COMMIT)",
                    "The remaining amount was split equally and balances updated atomically.");
        } else {
            ConsoleHelper.popupSuccess("Pending Split Cancelled (COMMIT)",
                    "The pending custom split was cancelled with no partial rows left behind.");
        }
        ConsoleHelper.pause();
    }

    private void settlementHistory(int groupId) throws Exception {
        List<Settlement> history = settlementService.getHistory(groupId);
        List<Settlement> pending = settlementService.getPending(groupId);
        ConsoleHelper.printHeader("Settlement History");

        if (history.isEmpty() && pending.isEmpty()) {
            ConsoleHelper.printInfo("No settlements yet.");
        } else {
            for (Settlement s : history) {
                String when = s.getSettledAt() != null
                        ? s.getSettledAt().format(DATE_TIME_FMT)
                        : "";
                System.out.printf("  %s paid %s %s  [%s]%n",
                        s.getFromUserName(), s.getToUserName(), s.getAmount(), when);
            }
        }
        for (Settlement s : pending) {
            System.out.printf("  %s pays %s : %s [PENDING]%n",
                    s.getFromUserName(), s.getToUserName(), s.getAmount());
        }
        ConsoleHelper.pause();
    }

    private void reports(int groupId) throws Exception {
        String groupName = groupService.getGroup(groupId).map(Group::getGroupName).orElse("");
        List<Settlement> paidSettlements = settlementService.getHistory(groupId);
        List<Settlement> pendingSettlements = settlementService.getPending(groupId);
        ConsoleHelper.printHeader("Reports  " + groupName);

        var totalSpending = reportService.getTotalSpending(groupId);
        String topSpender = reportService.getTopSpender(groupId);
        var categoryReport = reportService.getCategoryReport(groupId);

        System.out.println("  Total Spending : ," + totalSpending);
        System.out.println("  Top Spender    : " + topSpender);
        System.out.println("\n  Category Breakdown:");
        categoryReport.forEach((cat, amt) ->
                System.out.printf("    %-15s : %s%n", cat, amt));

        System.out.println("\n  Settlement Status:");
        if (paidSettlements.isEmpty() && pendingSettlements.isEmpty()) {
            System.out.println("    No settlements yet.");
        } else {
            for (Settlement s : paidSettlements) {
                System.out.printf("    %s -> %s : %s [PAID]%n",
                        s.getFromUserName(), s.getToUserName(), s.getAmount());
            }
            for (Settlement s : pendingSettlements) {
                System.out.printf("    %s -> %s : %s [PENDING]%n",
                        s.getFromUserName(), s.getToUserName(), s.getAmount());
            }
        }

        int userId = SessionManager.getCurrentUserId();
        userReportService.generateAndStoreReport(userId, groupId, groupName, null);

        System.out.println();
        System.out.println("  1. Export CLOB Report to .txt File  (FileWriter ’ your folder)");
        System.out.println("  0. Back");

        int choice;
        do {
            choice = ConsoleHelper.readInt("Choose report option: ");
            if (choice != 0 && choice != 1) {
                ConsoleHelper.printError("Please enter 1 to export or 0 to go back.");
            }
        } while (choice != 0 && choice != 1);

        if (choice == 1) {
            exportClobReportToFileFlow();
        }
    }

    private void exportClobReportToFileFlow() {
        try {
            int userId = SessionManager.getCurrentUserId();
            var reports = userReportService.listReports(userId);
            if (reports.isEmpty()) {
                ConsoleHelper.printInfo("No stored reports are available to export.");
                ConsoleHelper.pause();
                return;
            }

            ConsoleHelper.printHeader("Export CLOB Report  .txt File");
            for (var report : reports) {
                System.out.printf("  [%d] %s | %s | %d chars%n",
                        report.id(), report.reportName(), report.createdAt(), report.characterCount());
            }

            long reportId = reports.get(0).id();
            ConsoleHelper.printInfo("Exporting the most recently stored report: "
                    + reports.get(0).reportName());

            System.out.println("  1. Default folder (exports)");
            System.out.println("  2. Custom folder path");
            int location = ConsoleHelper.readChoice("Choose destination: ", 1, 2);
            String directory = location == 1 ? "exports"
                    : ConsoleHelper.readRequired("Enter folder path: ");

            String filePath = userReportService.exportClobToSelectedFolder(reportId, userId, directory);
            ConsoleHelper.popupSuccess("CLOB Exported to .txt", "Report written to the selected folder.");
            System.out.println("  File: " + filePath);
        } catch (Exception e) {
            ConsoleHelper.printError("Export CLOB to file failed: " + e.getMessage());
        }
        ConsoleHelper.pause();
    }

    private void groupSettings(Group group) throws Exception {
        ConsoleHelper.printHeader("Group Settings");
        System.out.println("  1. Add Members");
        System.out.println("  2. Rename Group");
        System.out.println("  0. Back");

        int choice = ConsoleHelper.readChoice("Choose: ", 0, 2);
        switch (choice) {
            case 1 -> addMembersFlow(group.getGroupId());
            case 2 -> {
                String newName = ConsoleHelper.readRequired("New group name: ");
                String err = groupService.renameGroup(group.getGroupId(), newName);
                if (err != null) {
                    ConsoleHelper.printError(err);
                } else {
                    group.setGroupName(newName.trim());
                    ConsoleHelper.printSuccess("Group renamed!");
                }
                ConsoleHelper.pause();
            }
            default -> { }
        }
    }

    void addMembersFlow(int groupId) throws Exception {
        int count = ConsoleHelper.readChoice("How many members do you want to add? ", 1, 50);
        int added = 0;

        for (int i = 1; i <= count; i++) {
            String query = ConsoleHelper.readRequired("Member " + i + " of " + count + " (username/email): ");
            String err = groupService.addMember(groupId, query);
            if (err != null) {
                if (err.startsWith("No user found matching: ")) {
                    String q = err.substring("No user found matching: ".length()).trim();
                    boolean registerNow = ConsoleHelper.readYesNo(
                            "" + q + " is not registered yet. Do you want to register this person now? (yes/no) ");

                    if (registerNow) {
                        String regErr = registerUserFromQuery(q);
                        if (regErr == null) {
                            err = groupService.addMember(groupId, query);
                        } else {
                            err = regErr;
                        }
                    }

                    if (!registerNow || err != null) {
                        ConsoleHelper.printError(err);
                        System.out.println("  1. Retry this member");
                        System.out.println("  2. Skip this member");
                        int retry = ConsoleHelper.readChoice("Choose: ", 1, 2);
                        if (retry == 1) {
                            i--;
                        }
                        continue;
                    }
                } else {
                    ConsoleHelper.printError(err);
                    System.out.println("  1. Retry this member");
                    System.out.println("  2. Skip this member");
                    int retry = ConsoleHelper.readChoice("Choose: ", 1, 2);
                    if (retry == 1) {
                        i--;
                    }
                    continue;
                }
            }

            ConsoleHelper.printSuccess("Added.");
            added++;
        }
        ConsoleHelper.printInfo(added + " of " + count + " member(s) added successfully.");
        ConsoleHelper.pause();
    }

    private String registerUserFromQuery(String query) throws Exception {
        String trimmed = query == null ? "" : query.trim();
        boolean queryIsEmail = trimmed.contains("@");

        String name;
        while (true) {
            name = ConsoleHelper.readRequired("Full Name: ");
            if (InputValidator.isValidName(name)) {
                break;
            }
            ConsoleHelper.printError("Enter a valid name with letters (max 100 characters).");
        }

        String email;
        String username;

        if (queryIsEmail) {
            email = trimmed.toLowerCase();
            String emailErr = InputValidator.emailValidationError(email);
            if (emailErr != null) {
                return emailErr;
            }
            if (authService.isEmailTaken(email)) {
                return "This email is already registered. Enter a different email.";
            }

            while (true) {
                username = ConsoleHelper.readRequired("Username: ");
                String normalized = InputValidator.normalizeUsername(username);
                if (!InputValidator.isValidUsername(normalized)) {
                    ConsoleHelper.printError("Username must be 3-30 characters (letters, numbers, _).");
                    continue;
                }
                if (authService.isUsernameTaken(username)) {
                    ConsoleHelper.printError("This username is already taken. Enter a different username.");
                    continue;
                }
                username = normalized;
                break;
            }
        } else {
            username = InputValidator.normalizeUsername(trimmed);
            if (!InputValidator.isValidUsername(username)) {
                return "Username must be 3-30 characters (letters, numbers, underscore only).";
            }
            if (authService.isUsernameTaken(username)) {
                return "This username is already taken. Enter a different username.";
            }

            while (true) {
                email = ConsoleHelper.readRequired("Email: ");
                String emailErr = InputValidator.emailValidationError(email);
                if (emailErr != null) {
                    ConsoleHelper.printError(emailErr);
                    continue;
                }
                if (authService.isEmailTaken(email)) {
                    ConsoleHelper.printError("This email is already registered. Enter a different email.");
                    continue;
                }
                email = email.trim().toLowerCase();
                break;
            }
        }

        System.out.println("  Tip: " + InputValidator.passwordRuleHint());

        String password;
        while (true) {
            password = ConsoleHelper.readRequired("Password: ");
            if (InputValidator.isValidPassword(password)) {
                break;
            }
            ConsoleHelper.printError(InputValidator.passwordRuleHint());
        }

        String confirm;
        while (true) {
            confirm = ConsoleHelper.readRequired("Confirm Password: ");
            if (password.equals(confirm)) {
                break;
            }
            ConsoleHelper.printError("Passwords do not match. Try again.");
        }

        String error = authService.register(name, email, username, password, confirm);
        if (error != null) {
            return error;
        }

        ConsoleHelper.printSuccess("Account created! Please login.");
        return null;
    }
public  static  void serchByExpenses()
{
    int searchE = ConsoleHelper.readInt("Enter amount for searching = ");
    String url = DBConnection.getUrl();
    String username = DBConnection.getUsername();
    String pass = "";
    String sql = "select * from expenses where amount >= "+searchE+" ";
    try {
        Connection con = DriverManager.getConnection(url,username,pass);
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery(sql);
        while (rs.next()) {
            System.out.println("amt = "+rs.getBigDecimal("amount")+"dse = "+rs.getString("description"));

        }

    } catch (SQLException e) {
        System.out.println("Conn");
    }


}
    private record SplitPlan(boolean distributedCustom, Map<Integer, BigDecimal> shares, BigDecimal creatorShare) { }
}

