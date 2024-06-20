package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  BIPDao  - browser/ip info.
 **/

import java.util.List;
import java.util.ArrayList;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BIPDao extends DaoBase { // Browser IP Dao

    private final static String RECORD_FIELDS =
        " id, create_time, ";

    private final static String DATA_FIELDS =
        " browser_version, ip ";


    private static final Logger log = LoggerFactory.getLogger(BIPDao.class);

    private final static String SQL_INSERT =
        "INSERT INTO pr.browser_ip (browser_version, ip) VALUES (?, ?)";

    public static void insertIP(Connection conn, long browserID, String ip) 
                throws SQLException {

        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(SQL_INSERT);
            ps.setLong(1, browserID);
            ps.setString(2, ip);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Insert returned 0");
            }
        } finally {
            closeSQL(ps);
        }
    }

    private final static String SQL_GET =
        "SELECT ip FROM pr.browser_ip WHERE browser_version = ? ORDER BY id DESC LIMIT 1";

    public static String getLastIP(Connection conn, long browserID)
                throws SQLException { 

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(SQL_GET);
            ps.setLong(1, browserID);
            rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            return rs.getString(1);
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }
}

