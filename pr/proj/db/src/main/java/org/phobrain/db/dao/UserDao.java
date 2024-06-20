package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  UserDao  - pr.pr_user table
 **/

import org.phobrain.db.record.User;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserDao extends DaoBase {

    private static final Logger log = LoggerFactory.getLogger(UserDao.class);

    private final static String RECORD_FIELDS =
        " id, create_time, ";

    private final static String USER_FIELDS =
        " name, browser_version, ip_addr, access_time ";

    private final static String SQL_INSERT_USER =
        "INSERT INTO pr.pr_user (" + USER_FIELDS + ") VALUES (?, ?, ?, NOW())";

    public static long insertUser(Connection conn, User u) throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(SQL_INSERT_USER,
                                      Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, u.name);
            ps.setLong(2, u.browserID);
            ps.setString(3, u.ipAddr);

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Insert returned 0");
            }
            ResultSet generatedKeys = ps.getGeneratedKeys();
            if (generatedKeys.next()) {
                u.id = generatedKeys.getLong(1);
                return u.id;
            } 
            throw new SQLException( "Creating user failed, no ID obtained.");

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private final static String SQL_GET_USER_BY_NAME =
        "SELECT " +
          RECORD_FIELDS +
          USER_FIELDS +
        " FROM pr.pr_user WHERE name = ?";

    public static User getUserByName(Connection conn, String name) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            ps = conn.prepareStatement(SQL_GET_USER_BY_NAME, ResultSet.TYPE_SCROLL_INSENSITIVE,
                                                             ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, name);
            rs = ps.executeQuery();
            if (!rs.next()) {   // impossible?
                return null;
            }

            User u = new User(name, rs.getLong(4), rs.getString(5));

            u.id = rs.getLong(1);
            u.createTime = rs.getTimestamp(2);
            u.accessTime = rs.getTimestamp(6);
            
            return u;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }
 
    private final static String SQL_UPDATE_USER_ACCESS =
        "UPDATE pr.pr_user SET access_time = NOW() WHERE id = ?";

    public static void updateUserAccess(Connection conn, User u) throws SQLException {

        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(SQL_UPDATE_USER_ACCESS);
            ps.setLong(1, u.id);

            int rows = ps.executeUpdate();
            if (rows != 1) {
                throw new SQLException("updateUserAccess update rows != 1: " + rows);
            }
        } finally {
            closeSQL(ps);
        }
    }

}

