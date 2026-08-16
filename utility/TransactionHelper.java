package com.expensesplitter.utility;

import com.expensesplitter.database.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Professional JDBC transaction utilities used across services.
 *
 * <pre>
 *   Connection conn = DBConnection.getConnection();
 *   conn.setAutoCommit(false);
 *   try {
 *       // multiple INSERT/UPDATE/DELETE on same Connection
 *       conn.commit();      // all permanent
 *   } catch (Exception e) {
 *       conn.rollback();    // all discarded
 *       throw e;
 *   } finally {
 *       conn.setAutoCommit(true);
 *       conn.close();
 *   }
 * </pre>
 */
public final class TransactionHelper {

    private TransactionHelper() {
    }

    @FunctionalInterface
    public interface TransactionWork<T> {
        T execute(Connection conn) throws Exception;
    }

    @FunctionalInterface
    public interface TransactionAction {
        void execute(Connection conn) throws Exception;
    }

    /**
     * Runs work inside one transaction: setAutoCommit(false) → work → commit,
     * or rollback on any failure.
     */
    public static <T> T execute(TransactionWork<T> work) throws Exception {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            T result = work.execute(conn);

            conn.commit();
            return result;
        } catch (Exception e) {
            safeRollback(conn);
            throw e;
        } finally {
            safeClose(conn);
        }
    }

    /** Same as {@link #execute(TransactionWork)} but with no return value. */
    public static void run(TransactionAction action) throws Exception {
        execute(conn -> {
            action.execute(conn);
            return null;
        });
    }

    public static void safeRollback(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            if (!conn.isClosed()) {
                conn.rollback();
            }
        } catch (SQLException ignored) {
            // Best-effort; original exception is preserved by caller
        }
    }

    public static void safeClose(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            try {
                if (!conn.isClosed()) {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException ignored) {
                // ignore restore failure
            }
            conn.close();
        } catch (SQLException ignored) {
            // ignore close failure
        }
    }
}
