package com.expensesplitter.service;

import com.expensesplitter.dao.ExpenseDAO;
import com.expensesplitter.model.Expense;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportService {

    private final ExpenseDAO expenseDAO = new ExpenseDAO();

    public BigDecimal getTotalSpending(int groupId) throws Exception {
        return expenseDAO.findByGroupId(groupId).stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Map<String, BigDecimal> getCategoryReport(int groupId) throws Exception {
        Map<String, BigDecimal> report = new HashMap<>();
        for (Expense e : expenseDAO.findByGroupId(groupId)) {
            report.merge(e.getCategory(), e.getAmount(), BigDecimal::add);
        }
        return report;
    }

    public String getTopSpender(int groupId) throws Exception {
        Map<String, BigDecimal> spenders = new HashMap<>();
        for (Expense e : expenseDAO.findByGroupId(groupId)) {
            spenders.merge(e.getPayerName(), e.getAmount(), BigDecimal::add);
        }
        return spenders.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + " — ₹" + e.getValue())
                .orElse("No expenses yet.");
    }

    public List<Expense> getAllExpenses(int groupId) throws Exception {
        return expenseDAO.findByGroupId(groupId);
    }
}
