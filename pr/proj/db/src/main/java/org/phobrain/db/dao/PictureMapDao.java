package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  PictureMapDao - hide real names of pics from web clients 
 **     using pr.picture_map table
 **/

import org.phobrain.db.record.PictureMap;
import org.phobrain.util.RandomUtil;

import java.util.Random;
import java.lang.StringBuilder;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PictureMapDao extends DaoBase {

    private static final Logger log = LoggerFactory.getLogger(
                                                 PictureMapDao.class);

    private final static String RECORD_FIELDS =
        " id, create_time, ";

    private final static String PICTURE_MAP_FIELDS =
        " archive, file_name, picture_id, hash ";

    private final static String SQL_INSERT_PICTURE_MAP =
        "INSERT INTO pr.picture_map " +
        " (" + PICTURE_MAP_FIELDS + ")" +
        " VALUES (?, ?, ?, ?)";

    public static PictureMap insertPictureMap(Connection conn, long id,
                                              int archive, String fileName)
                                                  throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        PictureMap pm = new PictureMap();
        pm.picID = id;
        pm.archive = archive;
        pm.fileName = fileName;
        try {
            ps = conn.prepareStatement(SQL_INSERT_PICTURE_MAP,
                                      Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1,    archive);
            ps.setString(2, fileName);
            ps.setLong(3,   id);
            for (int i=0; i<50; i++) {
                pm.hash = RandomUtil.makeRandomTag(10);
                ps.setString(4, pm.hash);
                try {
                    int rows = ps.executeUpdate();
                    if (rows == 0) {
                        throw new SQLException("Insert returned 0");
                    }
                    ResultSet generatedKeys = ps.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        pm.id = generatedKeys.getLong(1);
                        return pm;
                    }
                 } catch (Exception e) {
                    log.error("pr.picture_map insert: " + e);
                 }
            } 
            throw new SQLException( "Creating pr.picture_map failed, 50 tries.");

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_GET_PICTURE_MAP =
        "SELECT " + RECORD_FIELDS + PICTURE_MAP_FIELDS + 
          " FROM pr.picture_map WHERE hash = ?";

    public static PictureMap getPictureMap(Connection conn, String hash) 
            throws SQLException {

        if ("undefined".equals(hash)) {
            throw new SQLException("getPictureMap: hash is 'undefined'");
        }

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            ps = conn.prepareStatement(SQL_GET_PICTURE_MAP, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, hash);
            rs = ps.executeQuery();
            if (!rs.next()) {   // impossible?
                throw new SQLException("getPictureMap: no result for hash: " + hash);
            }
            
            PictureMap pm = new PictureMap();
            pm.id = rs.getLong(1);
            pm.createTime = rs.getTimestamp(2);
            pm.archive = rs.getInt(3);
            pm.fileName = rs.getString(4);
            pm.picID = rs.getLong(5);
            pm.hash = hash;

            return pm;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }
}

