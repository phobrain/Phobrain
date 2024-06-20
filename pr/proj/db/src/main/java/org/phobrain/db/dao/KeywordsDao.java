package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  KeywordsDao - keywords not in use but could come back 
 **                 with nn classification.
 **/

import org.phobrain.util.HashCount;
import org.phobrain.util.MiscUtil;

import org.phobrain.db.record.Keywords;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class KeywordsDao extends DaoBase {

    private static final Logger log = 
                                     LoggerFactory.getLogger(KeywordsDao.class);

    private final static String RECORD_FIELDS =
        " create_time, ";

    private final static String KEYWORDS_FIELDS =
        " id, type, coder, keywords ";

    private final static String SQL_INSERT_KEYWORDS =
        "INSERT INTO ##keywords (" + KEYWORDS_FIELDS + ") VALUES (?, ?, ?, ?)";

    public static void insertKeywords(Connection conn, Keywords k) 
                throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        int rows = 0;
        String stmt = chooseDB(SQL_INSERT_KEYWORDS);
        try {
            ps = conn.prepareStatement(stmt, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, k.id);
            ps.setString(2, k.type);
            ps.setString(3, k.coder);
            ps.setString(4, k.keywords);

            rows = ps.executeUpdate();
        } catch (SQLException sqe) {
            log.error("Insert " + k.id + ": [" + k.keywords + "] len " + k.keywords.length() + "\nstmt: " + stmt);
            throw sqe;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
        if (rows == 0) {
            throw new SQLException("Insert returned 0");
        }
    }

    private static final String SQL_GET_ID_LIST_BY_KWD =
        "SELECT id FROM ##keywords " +
        "WHERE type = 'pic' AND coder = ? " +
        " AND keywords LIKE '% @ %' " +
        " OR keywords LIKE '@ %' OR keywords LIKE '% @' " +
        " OR keywords = '@' ";

    public static List<String> getIdsCoderKwd(Connection conn, 
                                              String coder, String kwd) 
            throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            String s = chooseDB(SQL_GET_ID_LIST_BY_KWD)
                                .replaceAll("@", kwd);
            ps = conn.prepareStatement(s);
            ps.setString(1, coder);
            rs = ps.executeQuery();
            List<String> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
            return ids;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }


    private static Keywords kwdsFromRS(ResultSet rs) throws SQLException {

        Keywords k = new Keywords();

        k.createTime = rs.getTimestamp(1);
        k.id = rs.getString(2);
        k.type = rs.getString(3);
        k.coder = rs.getString(4);
        k.keywords = rs.getString(5);

        return k;
    }

    private static final String SQL_KWDS_BY_ID =
        "SELECT " + RECORD_FIELDS + KEYWORDS_FIELDS +
        " FROM ##keywords " +
        "WHERE id = ? ORDER BY coder";

    public static Map<String, Keywords> getKeywords(Connection conn, String id)
            throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_KWDS_BY_ID));
            ps.setString(1, id);
            rs = ps.executeQuery();

            // TODO - assuming one set of kwds per coder per picture

            Map<String, Keywords> coderKwds = new HashMap<>();
            String coder = null;
            while (rs.next()) {
                Keywords k = kwdsFromRS(rs);
                coderKwds.put(k.coder, k);
            }
            return coderKwds;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_KWDS_BY_PICID_CODER =
        "SELECT " + RECORD_FIELDS + KEYWORDS_FIELDS +
        " FROM ##keywords " +
        "WHERE id = ? AND coder = ?";

    public static Keywords getKeywordsByIdCoder(Connection conn, 
                          String id, String coder)
            throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_KWDS_BY_PICID_CODER));
            ps.setString(1, id);
            ps.setString(2, coder);
            rs = ps.executeQuery();

            if (!rs.next()) { // picture deleted since it was shown
                log.info("No result [" + id + "," +coder+ "]");
                return null;
            }
            return kwdsFromRS(rs);

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_KWD_SET_BY_PICID =
        "SELECT keywords FROM ##keywords " +
        "WHERE id = ?";

    public static Set<String> getKwdSet(Connection conn, String id)
            throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_KWD_SET_BY_PICID));
            ps.setString(1, id);
            rs = ps.executeQuery();

            if (!rs.next()) { // picture deleted since it was shown
                log.info("No result [" + id + "]");
                return null;
            }
            String kwds = rs.getString(1);
            String ss[] = kwds.split(" ");
            Set<String> ret = new HashSet<>();
            for (String kwd : ss) {
                ret.add(kwd);
            }
            return ret;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    public static boolean idsHaveKwd(Connection conn, String kwd, String[] ids)
            throws SQLException {
        for (String id : ids) {
            Set<String> kwds = getKwdSet(conn, id);
            if (kwds == null) {
                log.error("No kwds: " + id + " ids: " + ids[0] + " " + ids[1]);
                throw new RuntimeException("WWWW");
                //return false;
            }
            if (!kwds.contains(kwd)) {
                return false;
            }
        }
        return true;
    }

    private static final String SQL_KWDS_BY_CODER =
        "SELECT " + RECORD_FIELDS + KEYWORDS_FIELDS +
        " FROM ##keywords WHERE coder = ?";

    public static HashCount getCoderCounts(Connection conn)
                          throws SQLException {

        log.warn("HACK -assuming coder 'm'");
        String coder = "m";
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_KWDS_BY_CODER));
            ps.setString(1, coder);
            rs = ps.executeQuery();

            HashCount hc = new HashCount();

            while (rs.next()) {
                Keywords k = kwdsFromRS(rs);
                for (String s : k.keywords.split(" ")) {
                    hc.add(s);
                }
            }

            return hc;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }
}

