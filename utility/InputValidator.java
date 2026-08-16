package com.expensesplitter.utility;

import java.util.regex.Pattern;

public class InputValidator {

    private static final Pattern LOCAL_PART_PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z0-9+_.-]*$");
    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_]{3,30}$");
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{6,50}$");

    public static boolean isValidEmail(String email) {
        return emailValidationError(email) == null;
    }

    /**
     * Returns null if the email is valid; otherwise a specific error message.
     * Local part must be non-empty and must not start with a digit.
     * Domain must be exactly gmail.com or yahoo.com (case-insensitive).
     */
    public static String emailValidationError(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "Invalid email. Enter a valid email (e.g. name@gmail.com).";
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at < 0) {
            return "Invalid email. Enter a valid email (e.g. name@gmail.com).";
        }
        if (at == 0) {
            return "Invalid email. Enter the part before @ (e.g. name@gmail.com).";
        }
        if (trimmed.indexOf('@', at + 1) >= 0) {
            return "Invalid email. Enter a valid email (e.g. name@gmail.com).";
        }

        String local = trimmed.substring(0, at);
        String domain = trimmed.substring(at + 1);

        if (local.isEmpty()) {
            return "Invalid email. Enter the part before @ (e.g. name@gmail.com).";
        }
        if (Character.isDigit(local.charAt(0))) {
            return "Invalid email. Local part must not start with a digit.";
        }
        if (!LOCAL_PART_PATTERN.matcher(local).matches()) {
            return "Invalid email. Enter a valid email (e.g. name@gmail.com).";
        }

        String domainLower = domain.toLowerCase();
        if (!domainLower.equals("gmail.com") && !domainLower.equals("yahoo.com")) {
            return "Invalid email. Only gmail.com or yahoo.com allowed.";
        }
        return null;
    }

    public static boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username.trim()).matches();
    }

    /** 6–50 chars, letter + digit + special char, no spaces. */
    public static boolean isValidPassword(String password) {
        return password != null
                && !password.contains(" ")
                && PASSWORD_PATTERN.matcher(password).matches();
    }

    public static String passwordRuleHint() {
        return "Password must be 6-50 chars, include a letter, a number, and a special character, and have no spaces.";
    }

    public static boolean isValidName(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            return false;
        }

        boolean previousWasSpace = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch == ' ') {
                if (previousWasSpace) {
                    return false;
                }
                previousWasSpace = true;
                continue;
            }
            if (!((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z'))) {
                return false;
            }
            previousWasSpace = false;
        }
        return true;
    }

    public static String nameRuleHint() {
        return "Full Name must contain only letters and spaces (no numbers or special characters).";
    }

    public static boolean isValidGroupName(String name) {
        return name != null && !name.trim().isEmpty() && name.trim().length() <= 100;
    }

    /**
     * Expense description: 3–200 chars after trim, must contain a letter,
     * and must not be a single character repeated (e.g. "aaaa", "....").
     */
    public static boolean isValidDescription(String description) {
        if (description == null) {
            return false;
        }
        String trimmed = description.trim();
        if (trimmed.length() < 3 || trimmed.length() > 200) {
            return false;
        }
        boolean hasLetter = false;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isLetter(trimmed.charAt(i))) {
                hasLetter = true;
                break;
            }
        }
        if (!hasLetter) {
            return false;
        }
        char first = trimmed.charAt(0);
        boolean allSame = true;
        for (int i = 1; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) != first) {
                allSame = false;
                break;
            }
        }
        return !allSame;
    }

    public static String descriptionRuleHint() {
        return "Description must be 3-200 characters, include at least one letter, "
                + "and not be the same character repeated (e.g. avoid \"123\", \"!!!\", \"aaaa\").";
    }

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
