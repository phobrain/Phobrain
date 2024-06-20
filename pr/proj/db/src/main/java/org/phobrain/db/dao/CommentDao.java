package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  CommentDao  - track comment count per browser
 **/

import org.phobrain.db.record.Comment;

import java.util.List;
import java.util.ArrayList;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommentDao extends DaoBase {

    private static final Logger log = LoggerFactory.getLogger(CommentDao.class);
    private final static String RECORD_FIELDS =
        " id, create_time, ";

    private final static String COMMENT_INFO_FIELDS =
        " ip, count ";

    private final static String SQL_INSERT_COMMENT =
        "INSERT INTO pr.comment (" + COMMENT_INFO_FIELDS + ") VALUES (?, ?)";

    public static long insert(Connection conn, Comment com) throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(SQL_INSERT_COMMENT,
                                      Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, com.ip);
            ps.setInt(2, 1);

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Insert returned 0");
            }
            ResultSet generatedKeys = ps.getGeneratedKeys();
            if (generatedKeys.next()) {
                com.id = generatedKeys.getLong(1);
                return com.id;
            } 
            throw new SQLException( "Creating comment failed, no ID obtained.");

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private final static String SQL_GET_COMMENTS_BY_IP =
        "SELECT " +
          RECORD_FIELDS +
          COMMENT_INFO_FIELDS +
        " FROM pr.comment WHERE ip = ?";

    public static List<Comment> getByIP(Connection conn, String ip) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            ps = conn.prepareStatement(SQL_GET_COMMENTS_BY_IP, ResultSet.TYPE_SCROLL_INSENSITIVE,
                                                             ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, ip);
            rs = ps.executeQuery();

            List<Comment> l = new ArrayList<>();

            while (rs.next()) {
                Comment c = new Comment(ip);
                c.id = rs.getLong(1);
                c.createTime = rs.getTimestamp(2);
                c.count = rs.getInt(4);
                l.add(c);
            }

            return l;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }
 
    private final static String SQL_UPDATE_COMMENT_COUNT =
        "UPDATE pr.comment SET count = ? WHERE id = ?";

    public static void incrementCount(Connection conn, Comment c) throws SQLException {

        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(SQL_UPDATE_COMMENT_COUNT);
            ps.setInt(1, c.count+1);
            ps.setLong(2, c.id);

            int rows = ps.executeUpdate();
            if (rows != 1) {
                throw new SQLException("incrementCount update rows != 1: " + rows);
            }
            c.count++;
        } finally {
            closeSQL(ps);
        }
    }

}

