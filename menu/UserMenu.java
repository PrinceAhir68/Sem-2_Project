package com.expensesplitter.menu;

import com.expensesplitter.model.Group;
import com.expensesplitter.model.Notification;
import com.expensesplitter.service.AuthenticationService;
import com.expensesplitter.service.GroupService;
import com.expensesplitter.service.NotificationService;
import com.expensesplitter.utility.ConsoleHelper;
import com.expensesplitter.utility.InputValidator;
import com.expensesplitter.utility.SessionManager;

import java.util.List;

/**
 * User dashboard after login.
 */
public class UserMenu {

    private final AuthenticationService authService = new AuthenticationService();
    private final GroupService groupService = new GroupService();
    private final NotificationService notificationService = new NotificationService();
    private final GroupMenu groupMenu = new GroupMenu();

    public void show() {
        while (SessionManager.isLoggedIn()) {
            ConsoleHelper.printHeader("Dashboard — " + SessionManager.getCurrentUser().getName());
            System.out.println("  1. My Groups");
            System.out.println("  2. Create Group");
            System.out.println("  3. Notifications");
            System.out.println("  4. Profile");
            System.out.println("  5. Logout");
            System.out.println("=".repeat(50));

            int choice = ConsoleHelper.readChoice("Choose option: ", 1, 5);

            try {
                switch (choice) {
                    case 1 -> showMyGroups();
                    case 2 -> createGroup();
                    case 3 -> showNotifications();
                    case 4 -> showProfile();
                    case 5 -> {
                        authService.logout();
                        ConsoleHelper.printSuccess("Logged out.");
                        return;
                    }
                }
            } catch (Exception e) {
                ConsoleHelper.printError("Error: " + e.getMessage());
                ConsoleHelper.pause();
            }
        }
    }

    private void showMyGroups() throws Exception {
        List<Group> groups = groupService.getMyGroups();
        ConsoleHelper.printHeader("My Groups");

        if (groups.isEmpty()) {
            ConsoleHelper.printInfo("You are not in any group yet. Create one!");
            ConsoleHelper.pause();
            return;
        }

        for (int i = 0; i < groups.size(); i++) {
            Group g = groups.get(i);
            System.out.printf("  %d. %s (created by %s)%n", i + 1, g.getGroupName(), g.getCreatorName());
        }
        System.out.println("  0. Back");

        int choice = ConsoleHelper.readChoice("Open group (number): ", 0, groups.size());
        if (choice == 0) {
            return;
        }

        groupMenu.show(groups.get(choice - 1));
    }

    private void createGroup() throws Exception {
        ConsoleHelper.printHeader("Create Group");
        String name = ConsoleHelper.readRequired("Group name (e.g. Goa Trip): ");
        if (!InputValidator.isValidGroupName(name)) {
            ConsoleHelper.printError("Invalid group name.");
            ConsoleHelper.pause();
            return;
        }

        int groupId;
        try {
            groupId = groupService.createGroup(name);
            ConsoleHelper.printSuccess("Group '" + name.trim() + "' created!");
        } catch (IllegalArgumentException e) {
            ConsoleHelper.printError(e.getMessage());
            ConsoleHelper.pause();
            return;
        }

        // First prompt after create: add members
        System.out.println();
        System.out.println("  Add members to this group?");
        System.out.println("  1. Yes — add members now");
        System.out.println("  2. No — skip for now");
        int addChoice = ConsoleHelper.readChoice("Choose: ", 1, 2);

        if (addChoice == 1) {
            // Unify the duplicated "add members" loop with the Group Settings flow.
            groupMenu.addMembersFlow(groupId);
        }

        Group group = groupService.getGroup(groupId).orElse(null);
        if (group != null) {
            ConsoleHelper.printInfo("Opening group...");
            groupMenu.show(group);
        }
    }

    private void showNotifications() throws Exception {
        List<Notification> notifications = notificationService.getUnread();
        ConsoleHelper.printHeader("Notifications");

        if (notifications.isEmpty()) {
            ConsoleHelper.printInfo("No new notifications.");
        } else {
            for (Notification n : notifications) {
                System.out.println("  • " + n.getMessage());
            }
            notificationService.markAllRead();
        }
        ConsoleHelper.pause();
    }

    private void showProfile() throws Exception {
        ConsoleHelper.printHeader("Profile");
        var user = SessionManager.getCurrentUser();
        System.out.println("  Name    : " + user.getName());
        System.out.println("  Email   : " + user.getEmail());
        System.out.println("  Username: " + user.getUsername());
        System.out.println();
        System.out.println("  1. Update Profile");
        System.out.println("  2. Change Password");
        System.out.println("  0. Back");

        int choice = ConsoleHelper.readChoice("Choose: ", 0, 2);
        switch (choice) {
            case 1 -> {
                String name;
                while (true) {
                    name = ConsoleHelper.readRequired("New name: ");
                    if (InputValidator.isValidName(name)) {
                        break;
                    }
                    ConsoleHelper.printError(InputValidator.nameRuleHint());
                }
                String email;
                while (true) {
                    email = ConsoleHelper.readRequired("New email: ");
                    String emailErr = InputValidator.emailValidationError(email);
                    if (emailErr != null) {
                        ConsoleHelper.printError(emailErr);
                        continue;
                    }
                    if (authService.isEmailTaken(email, user.getUserId())) {
                        ConsoleHelper.printError("This email is already registered. Enter a different email.");
                        continue;
                    }
                    break;
                }
                String err = authService.updateProfile(name, email);
                if (err != null) {
                    ConsoleHelper.printError(err);
                } else {
                    ConsoleHelper.printSuccess("Profile updated.");
                }
            }
            case 2 -> {
                String old = ConsoleHelper.readRequired("Current password: ");
                System.out.println("  Tip: " + InputValidator.passwordRuleHint());
                String newPass;
                while (true) {
                    newPass = ConsoleHelper.readRequired("New password: ");
                    if (InputValidator.isValidPassword(newPass)) {
                        break;
                    }
                    ConsoleHelper.printError(InputValidator.passwordRuleHint());
                }
                String confirm;
                while (true) {
                    confirm = ConsoleHelper.readRequired("Confirm new password: ");
                    if (newPass.equals(confirm)) {
                        break;
                    }
                    ConsoleHelper.printError("Passwords do not match.");
                }
                String err = authService.changePassword(old, newPass, confirm);
                if (err != null) {
                    ConsoleHelper.printError(err);
                } else {
                    ConsoleHelper.printSuccess("Password changed.");
                }
            }
        }
        if (choice != 0) {
            ConsoleHelper.pause();
        }
    }
}
