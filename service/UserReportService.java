package com.expensesplitter.service;

import com.expensesplitter.dao.UserDAO;
import com.expensesplitter.dao.UserReportDAO;
import com.expensesplitter.model.User;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/** Generates, stores, and exports user CLOB reports. */
public class UserReportService {

    private final UserReportDAO userReportDAO = new UserReportDAO();
    private final UserDAO userDAO = new UserDAO();
    private final UserExpenseExportService expenseExportService = new UserExpenseExportService();

    public List<UserReportDAO.ReportSummary> listReports(int userId) throws SQLException {
        return userReportDAO.listSummariesByUserId(userId);
    }

    /**
     * Builds the comprehensive all-groups report for the user and stores it as a CLOB.
     * {@code groupId}/{@code groupName} are retained for call-site compatibility but are
     * not used for report content — the export covers every group the user belongs to.
     */
    public long generateAndStoreReport(int userId, int groupId, String groupName, String reportText)
            throws Exception {
        User user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found."));
        String fullReport = expenseExportService.buildCompleteExpenseExport(user);
        String reportName = user.getUsername() + "_full_report_" + System.currentTimeMillis();
        return userReportDAO.insertReport(userId, reportName, fullReport);
    }

    public String exportClobToSelectedFolder(long reportId, int userId, String directory) throws Exception {
        String reportText = userReportDAO.readClobTextById(reportId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found for this user."));
        File outputDirectory = new File(directory);
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new IOException("Could not create export folder: " + outputDirectory.getPath());
        }
        if (!outputDirectory.isDirectory()) {
            throw new IOException("Export path is not a folder: " + outputDirectory.getPath());
        }

        String fileName = "report_" + reportId + ".txt";
        File outputFile = new File(outputDirectory, fileName);
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(reportText);
        }
        return outputFile.getAbsolutePath();
    }
}
