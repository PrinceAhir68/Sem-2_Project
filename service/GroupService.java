package com.expensesplitter.service;

import com.expensesplitter.dao.*;
import com.expensesplitter.model.Group;
import com.expensesplitter.model.User;
import com.expensesplitter.utility.InputValidator;
import com.expensesplitter.utility.SessionManager;
import com.expensesplitter.utility.TransactionHelper;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class GroupService {

    private final GroupDAO groupDAO = new GroupDAO();
    private final GroupMemberDAO memberDAO = new GroupMemberDAO();
    private final BalanceDAO balanceDAO = new BalanceDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAO();
    private final UserDAO userDAO = new UserDAO();

    /**
     * Creates a group + creator membership + zero balance + history in one transaction.
     *
     * @return groupId on success
     */
    public int createGroup(String groupName) throws Exception {
        if (!SessionManager.isLoggedIn()) {
            throw new IllegalStateException("You must be logged in to create a group.");
        }
        if (!InputValidator.isValidGroupName(groupName)) {
            throw new IllegalArgumentException("Invalid group name.");
        }

        int creatorId = SessionManager.getCurrentUserId();
        Group group = new Group(groupName.trim(), creatorId);
        AtomicInteger groupIdHolder = new AtomicInteger();

        try {
            TransactionHelper.run(conn -> {
                int groupId = groupDAO.create(conn, group);
                groupIdHolder.set(groupId);
                memberDAO.addMember(conn, groupId, creatorId);
                balanceDAO.initializeBalance(conn, groupId, creatorId);
                activityLogDAO.log(conn, creatorId, groupId,
                        "GROUP_CREATED", "Group '" + groupName.trim() + "' created");
            });
        } catch (Exception e) {
            throw new Exception("Create group failed and was rolled back: " + e.getMessage(), e);
        }

        return groupIdHolder.get();
    }

    public List<Group> getMyGroups() throws Exception {
        if (!SessionManager.isLoggedIn()) {
            throw new IllegalStateException("You must be logged in.");
        }
        return groupDAO.findByUserId(SessionManager.getCurrentUserId());
    }

    public Optional<Group> getGroup(int groupId) throws Exception {
        return groupDAO.findById(groupId);
    }

    public List<User> getMembers(int groupId) throws Exception {
        if (!SessionManager.isLoggedIn()) {
            throw new IllegalStateException("You must be logged in.");
        }
        if (!memberDAO.isMember(groupId, SessionManager.getCurrentUserId())) {
            throw new IllegalStateException("You are not a member of this group.");
        }
        return memberDAO.getMembers(groupId);
    }

    /**
     * Adds a member + balance row + notification + history in one transaction.
     */
    public String addMember(int groupId, String searchQuery) throws Exception {
        if (!SessionManager.isLoggedIn()) {
            return "You must be logged in.";
        }
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return "Username/email cannot be empty.";
        }
        int actorId = SessionManager.getCurrentUserId();
        if (!memberDAO.isMember(groupId, actorId)) {
            return "You are not a member of this group.";
        }

        List<User> results = userDAO.searchByUsernameOrEmail(searchQuery.trim());
        if (results.isEmpty()) {
            return "No user found matching: " + searchQuery.trim();
        }

        User toAdd = results.stream()
                .filter(u -> u.getUsername().equalsIgnoreCase(searchQuery.trim())
                        || u.getEmail().equalsIgnoreCase(searchQuery.trim()))
                .findFirst()
                .orElse(results.get(0));

        if (memberDAO.isMember(groupId, toAdd.getUserId())) {
            return toAdd.getName() + " is already in this group.";
        }

        String groupName = groupDAO.findById(groupId).map(Group::getGroupName).orElse("group");

        try {
            TransactionHelper.run(conn -> {
                memberDAO.addMember(conn, groupId, toAdd.getUserId());
                balanceDAO.initializeBalance(conn, groupId, toAdd.getUserId());
                notificationDAO.create(conn, toAdd.getUserId(),
                        "You were added to group: " + groupName);
                activityLogDAO.log(conn, actorId, groupId,
                        "MEMBER_ADDED", toAdd.getName() + " added to group");
            });
            return null;
        } catch (Exception e) {
            throw new Exception("Add member failed and was rolled back: " + e.getMessage(), e);
        }
    }

    public String removeMember(int groupId, int userId) throws Exception {
        if (!SessionManager.isLoggedIn()) {
            return "You must be logged in.";
        }
        Group group = groupDAO.findById(groupId).orElse(null);
        if (group == null) {
            return "Group not found.";
        }
        if (group.getCreatedBy() != SessionManager.getCurrentUserId()) {
            return "Only the group creator can remove members.";
        }
        if (userId == group.getCreatedBy()) {
            return "Cannot remove the group creator.";
        }

        try {
            TransactionHelper.run(conn -> {
                memberDAO.removeMember(conn, groupId, userId);
                activityLogDAO.log(conn, SessionManager.getCurrentUserId(), groupId,
                        "MEMBER_REMOVED", "User ID " + userId + " removed");
            });
            return null;
        } catch (Exception e) {
            throw new Exception("Remove member failed and was rolled back: " + e.getMessage(), e);
        }
    }

    public String renameGroup(int groupId, String newName) throws Exception {
        if (!SessionManager.isLoggedIn()) {
            return "You must be logged in.";
        }
        if (!InputValidator.isValidGroupName(newName)) {
            return "Invalid group name.";
        }
        Group group = groupDAO.findById(groupId).orElse(null);
        if (group == null) {
            return "Group not found.";
        }
        if (group.getCreatedBy() != SessionManager.getCurrentUserId()) {
            return "Only the group creator can rename the group.";
        }
        groupDAO.updateName(groupId, newName.trim());
        return null;
    }
}
