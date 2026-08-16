package com.expensesplitter.service;

import com.expensesplitter.dao.AdminDAO;
import com.expensesplitter.dao.ActivityLogQueryDAO;
import com.expensesplitter.dao.UserDAO;
import com.expensesplitter.model.Admin;
import com.expensesplitter.model.User;
import com.expensesplitter.utility.PasswordHasher;
import com.expensesplitter.utility.SessionManager;

import java.util.List;
import java.util.Optional;

public class AdminService {

    private final AdminDAO adminDAO = new AdminDAO();
    private final UserDAO userDAO = new UserDAO();
    private final ActivityLogQueryDAO activityLogQueryDAO = new ActivityLogQueryDAO();

    public String adminLogin(String adminUsername, String password) throws Exception {
        if (adminUsername == null || adminUsername.trim().isEmpty()) {
            return "Admin username cannot be empty.";
        }
        if (password == null || password.isEmpty()) {
            return "Password cannot be empty.";
        }

        Optional<Admin> opt = adminDAO.findByAdminUsername(adminUsername);
        if (opt.isEmpty()) {
            return "Invalid admin credentials.";
        }

        Admin admin = opt.get();

        if (!PasswordHasher.verify(password, admin.getPasswordHash())) {
            return "Invalid admin credentials.";
        }

        SessionManager.loginAdmin(admin);
        return null;
    }

    public String adminLoginByPassword(String password) throws Exception {
        if (password == null || password.isEmpty()) {
            return "Password cannot be empty.";
        }

        Optional<Admin> opt = adminDAO.findFirstAdmin();
        if (opt.isEmpty()) {
            return "Admin account not configured.";
        }

        Admin admin = opt.get();

        if (!PasswordHasher.verify(password, admin.getPasswordHash())) {
            return "Invalid admin password.";
        }

        SessionManager.loginAdmin(admin);
        return null;
    }

    public void adminLogout() {
        SessionManager.logoutAdmin();
    }

    public List<User> getAllUsersBasicInfo() throws Exception {
        return userDAO.findAll();
    }

    public List<String> getUserActivities(int userId) throws Exception {
        return activityLogQueryDAO.findDescriptionsByUserId(userId);
    }

    public static class UserBasicInfo {
        public String email;
        public String name;

        public UserBasicInfo(String email, String name) {
            this.email = email;
            this.name = name;
        }

        @Override
        public String toString() {
            return "Email: " + email + " | Name: " + name;
        }
    }

    public List<UserBasicInfo> getUsersBasicInfoFiltered() throws Exception {
        List<User> users = userDAO.findAll();
        return users.stream()
                .map(u -> new UserBasicInfo(u.getEmail(), u.getName()))
                .toList();
    }
}
