package com.expensesplitter.model;

import java.time.LocalDateTime;

public class Admin {
    private int adminId;
    private String adminUsername;
    private String passwordHash;
    private String email;
    private LocalDateTime createdAt;

    public Admin() {}

    public Admin(String adminUsername, String passwordHash, String email) {
        this.adminUsername = adminUsername;
        this.passwordHash = passwordHash;
        this.email = email;
    }

    public int getAdminId() { return adminId; }
    public void setAdminId(int adminId) { this.adminId = adminId; }

    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
