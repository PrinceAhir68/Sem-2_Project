package com.expensesplitter.utility;

import com.expensesplitter.model.Admin;
import com.expensesplitter.model.User;

public class SessionManager {

    private static User currentUser;
    private static Admin currentAdmin;

    public static void login(User user) {
        currentUser = user;
        currentAdmin = null;
    }

    public static void logout() {
        currentUser = null;
        currentAdmin = null;
    }

    public static void loginAdmin(Admin admin) {
        currentAdmin = admin;
        currentUser = null;
    }

    public static void logoutAdmin() {
        currentAdmin = null;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static Admin getCurrentAdmin() {
        return currentAdmin;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public static boolean isAdminLoggedIn() {
        return currentAdmin != null;
    }

    public static int getCurrentUserId() {
        return currentUser != null ? currentUser.getUserId() : -1;
    }

    public static int getCurrentAdminId() {
        return currentAdmin != null ? currentAdmin.getAdminId() : -1;
    }
}

