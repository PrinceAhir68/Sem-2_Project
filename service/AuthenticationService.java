package com.expensesplitter.service;

import com.expensesplitter.dao.ActivityLogDAO;
import com.expensesplitter.dao.UserDAO;
import com.expensesplitter.model.User;
import com.expensesplitter.utility.InputValidator;
import com.expensesplitter.utility.PasswordHasher;
import com.expensesplitter.utility.SessionManager;

import java.time.LocalDateTime;
import java.util.Optional;

public class AuthenticationService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    private final UserDAO userDAO = new UserDAO();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAO();

    public String register(String name, String email, String username, String password, String confirmPassword)
            throws Exception {
        if (!InputValidator.isValidName(name)) {
            return "Invalid name. Enter your full name (max 100 characters).";
        }
        if (!InputValidator.isValidEmail(email)) {
            return "Invalid email format (example: name@email.com).";
        }

        String normalizedUser = InputValidator.normalizeUsername(username);
        if (!InputValidator.isValidUsername(normalizedUser)) {
            return "Username must be 3-30 characters (letters, numbers, underscore only).";
        }
        if (!InputValidator.isValidPassword(password)) {
            return InputValidator.passwordRuleHint();
        }
        if (confirmPassword == null || !password.equals(confirmPassword)) {
            return "Passwords do not match. Please re-enter.";
        }

        if (userDAO.findByEmail(email.trim()).isPresent()) {
            return "Email already registered.";
        }
        if (userDAO.findByUsername(normalizedUser).isPresent()) {
            return "Username already taken.";
        }

        User user = new User(name.trim(), email.trim().toLowerCase(), normalizedUser, PasswordHasher.hash(password));
        userDAO.create(user);
        activityLogDAO.log(user.getUserId(), null, "USER_REGISTERED", name.trim() + " registered");
        return null;
    }

    public String login(String username, String password) throws Exception {
        if (username == null || username.trim().isEmpty()) {
            return "Username cannot be empty.";
        }
        if (password == null || password.isEmpty()) {
            return "Password cannot be empty.";
        }

        String normalizedUser = InputValidator.normalizeUsername(username);

        Optional<User> opt = userDAO.findByUsername(normalizedUser);
        if (opt.isEmpty()) {
            return "Invalid username or password.";
        }

        User user = opt.get();

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            return "Account locked. Try again after " + user.getLockedUntil();
        }

        if (!PasswordHasher.verify(password, user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            if (attempts >= MAX_ATTEMPTS) {
                userDAO.updateFailedAttempts(user.getUserId(), attempts,
                        LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                return "Too many failed attempts. Account locked for " + LOCK_MINUTES + " minutes.";
            }
            userDAO.updateFailedAttempts(user.getUserId(), attempts, null);
            return "Invalid username or password. Attempts left: " + (MAX_ATTEMPTS - attempts);
        }

        userDAO.resetFailedAttempts(user.getUserId());
        SessionManager.login(user);
        activityLogDAO.log(user.getUserId(), null, "USER_LOGIN", user.getName() + " logged in");
        return null;
    }

    public void logout() {
        SessionManager.logout();
    }

    /** True if another account already uses this email. */
    public boolean isEmailTaken(String email) throws Exception {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return userDAO.findByEmail(email.trim().toLowerCase()).isPresent();
    }

    /**
     * True if another account (not {@code excludeUserId}) already uses this email.
     * Used by Update Profile so the current user can keep their own email.
     */
    public boolean isEmailTaken(String email, int excludeUserId) throws Exception {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        Optional<User> existing = userDAO.findByEmail(email.trim().toLowerCase());
        return existing.isPresent() && existing.get().getUserId() != excludeUserId;
    }

    /** True if this username is already taken. */
    public boolean isUsernameTaken(String username) throws Exception {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return userDAO.findByUsername(InputValidator.normalizeUsername(username)).isPresent();
    }

    public String updateProfile(String name, String email) throws Exception {
        if (!InputValidator.isValidName(name)) {
            return "Invalid name.";
        }
        if (!InputValidator.isValidEmail(email)) {
            return "Invalid email format (example: name@email.com).";
        }
        int userId = SessionManager.getCurrentUserId();
        String trimmedEmail = email.trim().toLowerCase();
        Optional<User> existing = userDAO.findByEmail(trimmedEmail);
        if (existing.isPresent() && existing.get().getUserId() != userId) {
            return "Email already used by another account.";
        }
        userDAO.updateProfile(userId, name.trim(), trimmedEmail);
        User user = SessionManager.getCurrentUser();
        user.setName(name.trim());
        user.setEmail(trimmedEmail);
        return null;
    }

    public String changePassword(String oldPassword, String newPassword, String confirmPassword)
            throws Exception {
        if (oldPassword == null || oldPassword.isEmpty()) {
            return "Current password cannot be empty.";
        }
        if (!InputValidator.isValidPassword(newPassword)) {
            return InputValidator.passwordRuleHint();
        }
        if (confirmPassword == null || !newPassword.equals(confirmPassword)) {
            return "New passwords do not match.";
        }
        if (oldPassword.equals(newPassword)) {
            return "New password must be different from the current password.";
        }

        User user = SessionManager.getCurrentUser();
        if (!PasswordHasher.verify(oldPassword, user.getPasswordHash())) {
            return "Current password is incorrect.";
        }
        String hashed = PasswordHasher.hash(newPassword);
        userDAO.updatePassword(user.getUserId(), hashed);
        user.setPasswordHash(hashed);
        return null;
    }
}
