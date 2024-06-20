package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  BrowserDao  - browser_version
 **/

import org.phobrain.util.RandomUtil;

import org.phobrain.db.record.Browser;

import java.util.List;
import java.util.ArrayList;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrowserDao extends DaoBase {

    private final static String RECORD_FIELDS =
        " id, create_time, ";

    private final static String BROWSER_FIELDS =
        " version, session_tag ";


    private static final Logger log = LoggerFactory.getLogger(BrowserDao.class);

    final static String SQL_INSERT_BROWSER = 
                    "INSERT INTO browser_version (version, session_tag) VALUES (?, ?)";

    public static Browser insertBrowser(Connection conn, String version) 
            throws SQLException {

        Browser b = new Browser();
        b.version = version;
        int rows = -1;
        for (int i=0; i<50; i++) {

            b.sessionTag = RandomUtil.makeRandomTag(8);

            PreparedStatement ps = null;
            ResultSet generatedKeys = null;
            try {
                ps = conn.prepareStatement(SQL_INSERT_BROWSER, 
                                           Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, version);
                ps.setString(2, b.sessionTag);
                rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new SQLException("Insert returned 0");
                }
                generatedKeys = ps.getGeneratedKeys();
                if (generatedKeys.next()) {
                    b.id = generatedKeys.getLong(1);
                } else {
                    throw new SQLException(
                              "Creating browser_version failed, no ID obtained.");
                }
                break;
            } catch (SQLException sqe) {
                String state = sqe.getSQLState();
                if (!"23000".equals(state)) {
                    // not an index collision on session tag
                    throw sqe;
                }
            } finally {
                closeSQL(generatedKeys);
                closeSQL(ps);
            }
        }
        if (rows != 1) {
            throw new SQLException("Creating browser_version failed, no unique tag.");
        }
        return b;
    }

    final static String SQL_UPDATE_BROWSER = 
                    "UPDATE browser_version SET session_tag = ? WHERE id = ?";

    public static void updateLegacyTag(Connection conn, Browser b) 
            throws SQLException {

        int rows = -1;
        for (int i=0; i<50; i++) {

            b.sessionTag = RandomUtil.makeRandomTag(8);

            PreparedStatement ps = null;
            try {
                ps = conn.prepareStatement(SQL_UPDATE_BROWSER);
                ps.setString(1, b.sessionTag);
                ps.setLong(2, b.id);
                rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new SQLException("Update returned 0");
                }
                break;
            } catch (SQLException sqe) {
                String state = sqe.getSQLState();
                if (!"23000".equals(state)) {
                    // not an index collision on session tag
                    throw sqe;
                }
            } finally {
                closeSQL(ps);
            }
        }
        if (rows != 1) {
            throw new SQLException("Updatting browser_version failed, no unique tag.");
        }
    }

    private static Browser browserFromResultSet(ResultSet rs) throws SQLException {
        Browser b = new Browser();
        b.id = rs.getLong(1);
        b.createTime = rs.getTimestamp(2);
        b.version = rs.getString(3);
        b.sessionTag = rs.getString(4);
        return b;
    }

    final static String SQL_GET_BY_ID = 
                    "SELECT " + RECORD_FIELDS + BROWSER_FIELDS +
                    " FROM browser_version WHERE id = ?";

    public static Browser getBrowserById(Connection conn, long id) 
            throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(SQL_GET_BY_ID, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setLong(1, id);
            rs = ps.executeQuery();

            if (!rs.next()) {
                return null;
            }
            return browserFromResultSet(rs);

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    final static String SQL_GET_BY_SESSION = 
                    "SELECT " + RECORD_FIELDS + BROWSER_FIELDS +
                    " FROM browser_version WHERE session_tag = ?";

    public static Browser getBrowserBySession(Connection conn, 
                                              String sessionTag) 
            throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(SQL_GET_BY_SESSION, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, sessionTag);
            rs = ps.executeQuery();

            if (!rs.next()) {
                return null;
            }
            return browserFromResultSet(rs);

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }


    final static String SQL_GET_BY_VERSION = 
                "SELECT " + RECORD_FIELDS + BROWSER_FIELDS +
                " FROM browser_version WHERE version like '@%' ORDER BY id ASC";

    public static List<Browser> getBrowsersByVersion(Connection conn, 
                                                     String version) 
            throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            String s = SQL_GET_BY_VERSION.replace("@", version);
            ps = conn.prepareStatement(s, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            rs = ps.executeQuery();

            List<Browser> l = new ArrayList<>();

            while (rs.next()) {
                l.add(browserFromResultSet(rs));
            }

            return l;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    final static String SQL_GET_BROWSERS = 
                    "SELECT " + RECORD_FIELDS + BROWSER_FIELDS +
                    " FROM browser_version ORDER BY id ASC";
    
    public static List<Browser> getBrowsers(Connection conn) 
            throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(SQL_GET_BROWSERS, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            rs = ps.executeQuery();

            List<Browser> l = new ArrayList<>();

            while (rs.next()) {
                l.add(browserFromResultSet(rs));
            }

            return l;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }
}

