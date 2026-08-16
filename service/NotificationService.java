package com.expensesplitter.service;

import com.expensesplitter.dao.NotificationDAO;
import com.expensesplitter.model.Notification;
import com.expensesplitter.utility.SessionManager;

import java.sql.SQLException;
import java.util.List;

public class NotificationService {

    private final NotificationDAO notificationDAO = new NotificationDAO();

    public List<Notification> getUnread() throws Exception {
        return notificationDAO.findUnreadByUserId(SessionManager.getCurrentUserId());
    }

    public List<Notification> getAll() throws Exception {
        return notificationDAO.findAllByUserId(SessionManager.getCurrentUserId());
    }

    public void markAllRead() throws Exception {
        notificationDAO.markAllRead(SessionManager.getCurrentUserId());
    }
}
