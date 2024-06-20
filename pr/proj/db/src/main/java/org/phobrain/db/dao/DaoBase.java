package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  DaoBase - utility for Daos
 **/

import org.phobrain.util.Stdio;

import com.pgvector.PGvector;

/**
 **  dbcp / not finished
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDriver;
 **/


//c3p0
import javax.naming.InitialContext;
import javax.naming.NamingException;

import java.util.Random;
import java.lang.StringBuilder;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DaoBase extends Stdio {

    public static boolean NOOP = false;

    private static final Logger log = LoggerFactory.getLogger(DaoBase.class);

/*
dbcp
    static {
        GenericObjectPool connectionPool = new GenericObjectPool(null);
        ConnectionFactory connectionFactory = new 
                                DriverManagerConnectionFactory(
                                    "java:comp/env/jdbc/pr",
                                    "pr", "@@pr");
         PoolableConnectionFactory poolableConnectionFactory = new 
                        PoolableConnectionFactory(connectionFactory,
                        connectionPool, null, null, false, true);
        PoolingDriver driver = new PoolingDriver();
        driver.registerPool("pr_dbcp", connectionPool);
    }
*/

    public static Connection getConn() throws NamingException, SQLException {

        // c3p0
        InitialContext ic = new InitialContext();
        DataSource ds = (DataSource)ic.lookup("java:comp/env/jdbc/pr");

        for (int i=0; i<10; i++) {

            Connection conn = null;
            Statement st = null;
            ResultSet rs = null;

            try {

                long t0 = System.currentTimeMillis();

                // c3p0
                conn = ds.getConnection();
/*
                // dbcp
                conn = DriverManager.getConnection("jdbc:apache:commons:dbcp:pr_dbcp");
*/
                conn.setAutoCommit(true);
                st = conn.createStatement();

                if (!st.execute("SELECT NOW();")) {
                    log.error("SELECT NOW() returns false");
                    continue;
                }
                rs = st.getResultSet();
                if (!rs.next()) {
                    log.error("rs.next() returns false");
                    continue;
                }

                long diff = System.currentTimeMillis() - t0;
                if (diff > 5) {
                    Timestamp ts = rs.getTimestamp(1);
                    log.warn("DB TIME: " + ts + " in " + diff);
                }
/*
                // for unindexed pgvectors, default workers is 2 TODO fails
                rs.close();
                rs = null;
                if (!st.execute("SET max_parallel_workers_per_gather = 4;")) {
                    log.error("FAILED: SET max_parallel_workers_per_gather = 4;");
                }
*/
                PGvector.addVectorType(conn);
                return conn;

            } catch (SQLException sqe) {
                log.error("Getting/checking connection/" + i + ": " + sqe);
                closeSQL(conn);
            } finally {
                closeSQL(rs);
                closeSQL(st);
            }
            // back off on retry
            try { Thread.sleep(100 * (i+1)); } catch (Exception ignore) {}
        }
        log.error("DB CONN FAILURE");
        throw new SQLException("DB CONN FAILURE");
    }

    protected static Timestamp getTimestamp(ResultSet rs, int num) 
            throws SQLException {
        return rs.getTimestamp(num);
    }

    private static String SQL_TEST_TABLE =
        "SELECT EXISTS " +
        "   ( SELECT FROM information_schema.tables" +
        "       WHERE table_schema='SSS' " +
        "           AND table_name = 'TTT' );";

    private static String SQL_TEST_TABLE_COLUMN =
        "SELECT EXISTS " +
        "   ( SELECT FROM information_schema.columns " +
        "       WHERE table_schema='SSS' " +
        "           AND table_name = 'TTT' " +
        "           AND column_name = 'CCC' );";

    public static boolean haveTable(Connection conn, String table) {

        String schema = "pr";
        if (isTrim()) {
            schema = "public";
            table = "trim_" + table;
        }

        String query = null;

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            query = SQL_TEST_TABLE.replace("SSS", schema)
                                  .replace("TTT", table);
            ps = conn.prepareStatement(query);
            rs = ps.executeQuery();
            if (!rs.next()) {
                log.error("No next() - exiting: " + query);
                System.exit(1);
            }
            return rs.getBoolean(1);

        } catch (SQLException sqe) {

            log.error("Failed to establish table status - exiting:\n" + query + "\n" + sqe);
            System.exit(1);

        } finally {
            closeSQL(ps);
        }

        // for compiler
        System.exit(1);
        return false;
    }

    public static boolean haveTableColumn(Connection conn, String table, String column) {

        String schema = "pr";
        if (isTrim()) {
            schema = "public";
            table = "trim_" + table;
        }

        String query = null;

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            query = SQL_TEST_TABLE_COLUMN.replace("SSS", schema)
                                         .replace("TTT", table)
                                         .replace("CCC", column);

            ps = conn.prepareStatement(query);
            rs = ps.executeQuery();
            if (!rs.next()) {
                log.error("No next() - exiting: " + query);
                System.exit(1);
            }
            return rs.getBoolean(1);

        } catch (SQLException sqe) {

            log.error("Failed to establish table.column status - exiting:\n" + query + "\n" + sqe);
            System.exit(1);

        } finally {
            closeSQL(ps);
        }

        // for compiler
        System.exit(1);
        return false;
    }

    public static void closeSQL(AutoCloseable obj) {
        if (obj == null) {
            return;
        }
        try {
            obj.close(); 
        } catch (Exception ignore) {}
    }

    private static String db = "pr.";

    public static void useTrimDB() {
        log.info("USING TRIM DB");
        db = "public.trim_";
    }
    public static void useRealDB() {
        log.info("USING REAL DB");
        db = "pr.";
    }

    public static boolean isTrim() {
        return db.contains("trim_");
    }

    public static String chooseDB(String stmt) {

        return stmt.replaceAll("##", db);

    }
}

