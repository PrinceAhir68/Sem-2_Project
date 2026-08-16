package com.expensesplitter.utility;

import java.math.BigDecimal;
import java.util.Scanner;

public class ConsoleHelper {

    private static final Scanner SCANNER = new Scanner(System.in);

    public static Scanner getScanner() {
        return SCANNER;
    }

    public static void printHeader(String title) {
        System.out.println();
        System.out.println("=".repeat(50));
        System.out.println("  " + title);
        System.out.println("=".repeat(50));
    }

    public static void printSuccess(String message) {
        System.out.println("✅ " + message);
    }

    public static void printError(String message) {
        System.out.println("❌ " + message);
    }

    public static void printInfo(String message) {
        System.out.println("ℹ️  " + message);
    }

    public static String readLine(String prompt) {
        // Ensure menus, validation messages, and the prompt are visible before waiting for input.
        System.out.flush();
        System.out.print(prompt == null ? "" : prompt);
        System.out.flush();
        return SCANNER.nextLine().trim();
    }

    public static int readInt(String prompt) {
        while (true) {
            String input = readLine(prompt);
            if (input.isEmpty()) {
                printError("Input cannot be empty. Please enter a number.");
                continue;
            }
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                printError("Please enter a valid number.");
            }
        }
    }

    /** Keeps asking until the user enters an int in [min, max] inclusive. */
    public static int readChoice(String prompt, int min, int max) {
        while (true) {
            int value = readInt(prompt);
            if (value >= min && value <= max) {
                return value;
            }
            printError("Please enter a number between " + min + " and " + max + ".");
        }
    }

    /** Keeps asking until a non-empty trimmed string is entered. */
    public static String readRequired(String prompt) {
        while (true) {
            String input = readLine(prompt);
            if (!input.isEmpty()) {
                return input;
            }
            printError("This field cannot be empty.");
        }
    }

    public static BigDecimal readAmount(String prompt) {
        while (true) {
            String input = readLine(prompt);
            if (input.isEmpty()) {
                printError("Amount cannot be empty.");
                continue;
            }
            try {
                BigDecimal amount = new BigDecimal(input);
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    printError("Amount must be greater than zero.");
                    continue;
                }
                return amount.setScale(2, java.math.RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                printError("Please enter a valid amount (e.g. 500 or 500.50).");
            }
        }
    }

    public static void pause() {
        readLine("\nPress Enter to continue...");
    }

    public static boolean readYesNo(String prompt) {
        while (true) {
            String input = readLine(prompt).trim().toLowerCase();
            if (input.equals("y") || input.equals("yes")) {
                return true;
            }
            if (input.equals("n") || input.equals("no")) {
                return false;
            }
            printError("Please enter y/yes or n/no.");
        }
    }

    /**
     * Console "popup" confirmation dialog.
     * Shows a bordered question box, then asks Yes/No.
     *
     * @return true if user chose Yes, false if No
     */
    public static boolean confirmDialog(String title, String message) {
        System.out.println();
        System.out.println("+" + "-".repeat(48) + "+");
        System.out.println("|  " + padRight(title, 46) + "|");
        System.out.println("+" + "-".repeat(48) + "+");
        wrapMessage(message, 46).forEach(line ->
                System.out.println("|  " + padRight(line, 46) + "|"));
        System.out.println("+" + "-".repeat(48) + "+");
        return readYesNo("|  Confirm (y/n): ");
    }

    /** Success-style popup message after a completed action. */
    public static void popupSuccess(String title, String message) {
        System.out.println();
        System.out.println("+" + "-".repeat(48) + "+");
        System.out.println("|  ✅ " + padRight(title, 44) + "|");
        System.out.println("+" + "-".repeat(48) + "+");
        wrapMessage(message, 46).forEach(line ->
                System.out.println("|  " + padRight(line, 46) + "|"));
        System.out.println("+" + "-".repeat(48) + "+");
    }

    /** Info / cancel-style popup. */
    public static void popupInfo(String title, String message) {
        System.out.println();
        System.out.println("+" + "-".repeat(48) + "+");
        System.out.println("|  ℹ️  " + padRight(title, 43) + "|");
        System.out.println("+" + "-".repeat(48) + "+");
        wrapMessage(message, 46).forEach(line ->
                System.out.println("|  " + padRight(line, 46) + "|"));
        System.out.println("+" + "-".repeat(48) + "+");
    }

    private static String padRight(String text, int width) {
        if (text == null) {
            text = "";
        }
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        return text + " ".repeat(width - text.length());
    }

    private static java.util.List<String> wrapMessage(String message, int width) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (message == null || message.isBlank()) {
            lines.add("");
            return lines;
        }
        String[] words = message.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= width) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }
}
