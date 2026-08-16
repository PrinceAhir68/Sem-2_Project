package com.expensesplitter.menu;

import com.expensesplitter.model.User;
import com.expensesplitter.service.AdminService;
import com.expensesplitter.utility.ConsoleHelper;
import com.expensesplitter.utility.SessionManager;

import java.util.List;

public class AdminMenu {

    private final AdminService adminService = new AdminService();

    public void show() {
        while (SessionManager.isAdminLoggedIn()) {
            ConsoleHelper.printHeader("ADMIN PANEL");
            System.out.println("  1. View All Users (Email & Name)");
            System.out.println("  2. View User Activities");
            System.out.println("  3. Logout");
            System.out.println("=".repeat(50));

            int choice = ConsoleHelper.readChoice("Choose option: ", 1, 3);

            try {
                switch (choice) {
                    case 1 -> viewAllUsers();
                    case 2 -> viewUserActivities();
                    case 3 -> {
                        adminService.adminLogout();
                        ConsoleHelper.printSuccess("Admin logged out.");
                        return;
                    }
                }
            } catch (Exception e) {
                ConsoleHelper.printError("Error: " + e.getMessage());
            }
            ConsoleHelper.pause();
        }
    }

    private void viewAllUsers() throws Exception {
        ConsoleHelper.printHeader("ALL USERS");

        List<User> users = adminService.getAllUsersBasicInfo();

        if (users.isEmpty()) {
            ConsoleHelper.printInfo("No users found in the system.");
        } else {
            System.out.println("\nTotal Users: " + users.size());
            System.out.println("-".repeat(60));
            System.out.printf("%-30s | %-30s%n", "Email", "Name");
            System.out.println("-".repeat(60));

            for (User user : users) {
                System.out.printf("%-30s | %-30s%n", 
                    truncateString(user.getEmail(), 30),
                    truncateString(user.getName(), 30));
            }
            System.out.println("-".repeat(60));
        }
    }

    private void viewUserActivities() throws Exception {
        ConsoleHelper.printHeader("USER ACTIVITIES");

        List<User> users = adminService.getAllUsersBasicInfo();

        if (users.isEmpty()) {
            ConsoleHelper.printInfo("No users found in the system.");
            ConsoleHelper.pause();
            return;
        }

        System.out.println("Select user to view activities:");
        System.out.println("-".repeat(70));
        for (int i = 0; i < users.size(); i++) {
            System.out.printf("  %d. %-30s | %s%n", i + 1, 
                truncateString(users.get(i).getEmail(), 30),
                truncateString(users.get(i).getName(), 30));
        }
        System.out.println("  0. Back");
        System.out.println("-".repeat(70));

        int choice = ConsoleHelper.readChoice("Choose user: ", 0, users.size());
        if (choice == 0) return;

        User selectedUser = users.get(choice - 1);
        viewActivitiesForUser(selectedUser);
    }

    private void viewActivitiesForUser(User user) throws Exception {
        ConsoleHelper.printHeader("Activities — " + user.getName() + " (" + user.getEmail() + ")");

        List<String> activities = adminService.getUserActivities(user.getUserId());

        if (activities.isEmpty()) {
            ConsoleHelper.printInfo("No activities recorded for this user.");
        } else {
            System.out.println("\nRecent Activities (Latest First):");
            System.out.println("-".repeat(70));
            for (String activity : activities) {
                System.out.println("  " + activity);
            }
            System.out.println("-".repeat(70));
            System.out.println("Total Activities: " + activities.size());
        }
    }

    private String truncateString(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() > maxLength) {
            return str.substring(0, maxLength - 3) + "...";
        }
        return str;
    }
}
