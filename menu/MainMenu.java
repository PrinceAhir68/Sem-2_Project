package com.expensesplitter.menu;

import com.expensesplitter.service.AuthenticationService;
import com.expensesplitter.service.AdminService;
import com.expensesplitter.utility.ConsoleHelper;
import com.expensesplitter.utility.InputValidator;

public class MainMenu {

    private final AuthenticationService authService = new AuthenticationService();
    private final AdminService adminService = new AdminService();
    private final UserMenu userMenu = new UserMenu();
    private final AdminMenu adminMenu = new AdminMenu();

    public void show() throws Exception {
        while (true) {
            ConsoleHelper.printHeader("EXPENSE SPLITTER — Welcome");
            System.out.println("  1. Login");
            System.out.println("  2. Register");
            System.out.println("  3. Exit");
            System.out.println("=".repeat(50));

            int choice = ConsoleHelper.readChoice("Choose option: ", 1, 3);

            switch (choice) {
                case 1 -> handleLoginMenu();
                case 2 -> handleRegister();
                case 3 -> {
                    ConsoleHelper.printInfo("Goodbye!");
                    return;
                }
            }
        }
    }

    private void handleLoginMenu() throws Exception {
        ConsoleHelper.printHeader("LOGIN TYPE");
        System.out.println("  1. Login as User");
        System.out.println("  2. Login as Admin");
        System.out.println("  3. Back to Main Menu");
        System.out.println("=".repeat(50));

        int choice = ConsoleHelper.readChoice("Choose login type: ", 1, 3);

        switch (choice) {
            case 1 -> handleUserLogin();
            case 2 -> handleAdminLogin();
            case 3 -> {} // Go back to main menu
        }
    }

    private void handleUserLogin() throws Exception {
        ConsoleHelper.printHeader("User Login");

        String username;
        while (true) {
            username = ConsoleHelper.readRequired("Username: ");
            if (InputValidator.isValidUsername(InputValidator.normalizeUsername(username))) {
                break;
            }
            ConsoleHelper.printError("Username must be 3-30 characters (letters, numbers, _).");
        }

        String password = ConsoleHelper.readRequired("Password: ");

        String error = authService.login(username, password);
        if (error != null) {
            ConsoleHelper.printError(error);
            ConsoleHelper.pause();
            return;
        }

        ConsoleHelper.printSuccess("Login successful!");
        userMenu.show();
    }

    private void handleAdminLogin() throws Exception {
        ConsoleHelper.printHeader("Admin Login");

        String password = ConsoleHelper.readRequired("Admin Password: ");

        String error = adminService.adminLoginByPassword(password);
        if (error != null) {
            ConsoleHelper.printError(error);
            ConsoleHelper.pause();
            return;
        }

        ConsoleHelper.printSuccess("Admin login successful!");
        adminMenu.show();
    }

    private void handleRegister() throws Exception {
        ConsoleHelper.printHeader("Register");

        String name;
        while (true) {
            name = ConsoleHelper.readRequired("Full Name: ");
            if (InputValidator.isValidName(name)) break;
            ConsoleHelper.printError(InputValidator.nameRuleHint());
        }

        String email;
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
            break;
        }

        String username;
        while (true) {
            username = ConsoleHelper.readRequired("Username: ");
            if (!InputValidator.isValidUsername(InputValidator.normalizeUsername(username))) {
                ConsoleHelper.printError("Username must be 3-30 characters (letters, numbers, _).");
                continue;
            }
            if (authService.isUsernameTaken(username)) {
                ConsoleHelper.printError("This username is already taken. Enter a different username.");
                continue;
            }
            break;
        }

        System.out.println("  Tip: " + InputValidator.passwordRuleHint());
        String password;
        while (true) {
            password = ConsoleHelper.readRequired("Password: ");
            if (InputValidator.isValidPassword(password)) break;
            ConsoleHelper.printError(InputValidator.passwordRuleHint());
        }

        String confirm;
        while (true) {
            confirm = ConsoleHelper.readRequired("Confirm Password: ");
            if (password.equals(confirm)) break;
            ConsoleHelper.printError("Passwords do not match. Try again.");
        }

        String error = authService.register(name, email, username, password, confirm);
        if (error != null) {
            ConsoleHelper.printError(error);
        } else {
            ConsoleHelper.printSuccess("Account created! Please login.");
        }
        ConsoleHelper.pause();
    }
}
