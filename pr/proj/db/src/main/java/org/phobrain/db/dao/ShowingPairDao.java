package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  ShowingPairDao  - pr.showing_pair - record what shown and response
 **/

import org.phobrain.db.record.HistoryPair;
import org.phobrain.db.record.Picture;

import org.phobrain.util.AtomSpec;
import org.phobrain.util.MiscUtil;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import java.lang.StringBuilder;

import java.sql.Connection;
import java.sql.Timestamp;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShowingPairDao extends DaoBase {

    private static final Logger log = LoggerFactory.getLogger(
                                                   ShowingPairDao.class);

    private final static String RECORD_FIELDS =
        " id, create_time, ";

    private final static String SHOWING_PAIR_FIELDS =
        " browser_id, call_count, order_in_session," +
        " id1, archive1, file_name1, sel_method1," +
        " id2, archive2, file_name2, sel_method2," +
        " rating, rating_scheme," +
        " rate_time, user_time, user_time2, watch_dots_time, mouse_down_time," +
        " mouse_dist, mouse_dist2," +
        " mouse_dx, mouse_dy, mouse_vecx, mouse_vecy," +
        " mouse_maxv, mouse_maxa, mouse_mina, mouse_maxj," +
        " mouse_time, load_time, pix_in_pic, dot_count, pix_out_pic," +
        " atom_impact, impact_factor, pic_clik," +
        " n_tog_last, tog_sides, tog_times," +
        " vertical, big_time, big_stime," +
        " dot_dist,dot_vec_len,dot_vec_ang," +
        " dot_max_vel,dot_max_acc,dot_max_jerk," +
        " dot_start_scrn, dot_end_scrn ";

    private final static String SQL_INSERT_SHOWING_PAIR =
        "INSERT INTO pr.showing_pair " +
        " (browser_id, vertical, call_count, order_in_session," +
        "  id1, archive1, file_name1, sel_method1," +
        "  id2, archive2, file_name2, sel_method2," +
        "  atom_impact, impact_factor, big_stime)" +
        " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public static void insertPair(Connection conn, HistoryPair s) 
                                                  throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        ResultSet generatedKeys = null;

        try {
            ps = conn.prepareStatement(SQL_INSERT_SHOWING_PAIR,
                                       Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1,    s.browserID);
            ps.setBoolean(2, s.vertical);
            ps.setInt(3,     s.callCount);
            ps.setInt(4,     s.orderInSession);
            ps.setString(5,  s.id1);
            ps.setInt(6,     s.archive1);
            ps.setString(7,  s.fileName1);
            ps.setString(8,  s.selMethod1);
            ps.setString(9,  s.id2);
            ps.setInt(10,    s.archive2);
            ps.setString(11, s.fileName2);
            ps.setString(12, s.selMethod2);
            ps.setInt(13,    s.atomImpact.intValue());
            ps.setFloat(14,  s.impactFactor);
            if (s.bigStime > 32767) {
                ps.setInt(15, 32767);
            } else {
                ps.setInt(15,    s.bigStime);
            }

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Insert returned 0");
            }
            generatedKeys = ps.getGeneratedKeys();
            if (generatedKeys.next()) {
                s.id = generatedKeys.getLong(1);
                return;
            } 
            throw new SQLException( "Creating showing failed, no ID obtained.");

        } finally {
            closeSQL(generatedKeys);
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_COUNT_SHOWING_PAIRS =
        "SELECT count(*) FROM pr.showing_pair" +
        " WHERE create_time > now() - interval '5 seconds'";

    public static int getCount(Connection conn) 
                          throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(SQL_COUNT_SHOWING_PAIRS,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            rs = ps.executeQuery();
            if (!rs.next()) { // no result, impossible db error
                throw new SQLException("getCount: no result");
            }
            return rs.getInt(1);
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }


    private static final String SQL_IDS_SEEN =
        "SELECT id1, id2 FROM pr.showing_pair WHERE browser_id = ?";

    public static Set<String> getSeen(Connection conn, long browserID)
            throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(SQL_IDS_SEEN, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setLong(1, browserID);
            rs = ps.executeQuery();

            Set<String> ret = new HashSet<>();

            while (rs.next()) {
                ret.add(rs.getString(1));
                ret.add(rs.getString(2));
            }
            log.info("getSeen " + browserID + ": " + ret.size());
            return ret;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_METHOD =
        "SELECT id1, id2 FROM pr.showing_pair WHERE sel_method1 @@ " +
                                            " OR sel_method2 @@";

    /**
     *  Set { id1_id2 }
     */
    public static Set<String> getPairsWithMethod(Connection conn, 
                                                  boolean like,
                                                  String method)
            throws SQLException {

        String query;
        if (like) {
            query = SQL_METHOD.replaceAll("@@", "LIKE '%" + method + "%'");
        } else {
            query = SQL_METHOD.replaceAll("@@", "= '" + method + "'");
        }

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(query, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            rs = ps.executeQuery();

            Set<String> ret = new HashSet<>();

            while (rs.next()) {
                ret.add( rs.getString(1) + "_" + rs.getString(2) );
            }
            log.info("getPairsWithMethod " + method + ": " + ret.size());
            return ret;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    public static HistoryPair getLastPair(Connection conn, 
                                                  long browserID)
              throws SQLException {
        long showingID = getLastID(conn, browserID);
        if (showingID == -1) {
            return null;
        }
        return getPairByID(conn, showingID);
    }

    private final static String SQL_GET_LAST_SHOWING_ID =
        "SELECT MAX(id) FROM pr.showing_pair WHERE browser_id = ?";

    public static long getLastID(Connection conn, long browserID)
              throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            ps = conn.prepareStatement(SQL_GET_LAST_SHOWING_ID, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setLong(1, browserID);
            rs = ps.executeQuery();
            if (!rs.next()) {   // impossible?
                throw new SQLException("getLastID: no result");
            }
            long showingID = rs.getLong(1);
            if (showingID == 0) {
                return -1; // seems better
            }
            return showingID;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static HistoryPair showingPairFromResultSet(ResultSet rs) 
            throws SQLException {

        HistoryPair hp = new HistoryPair();

        hp.id = rs.getLong(1);
        hp.createTime = rs.getTimestamp(2);

        hp.browserID = rs.getLong(3);
        hp.callCount = rs.getInt(4);
        hp.orderInSession = rs.getInt(5);
 
        hp.id1 = rs.getString(6);
        hp.archive1 = rs.getInt(7);
        hp.fileName1 = rs.getString(8);
        hp.selMethod1 = rs.getString(9);

        hp.id2 = rs.getString(10);
        hp.archive2 = rs.getInt(11);
        hp.fileName2 = rs.getString(12);
        hp.selMethod2 = rs.getString(13);

        hp.pairRating = rs.getInt(14);
        hp.ratingScheme = rs.getInt(15);

        hp.rateTime = getTimestamp(rs, 16);
        hp.userTime = rs.getInt(17);
        hp.userTime2 = rs.getInt(18);
        hp.watchDotsTime = rs.getInt(19);
        hp.mouseDownTime = rs.getInt(20);

        hp.mouseDist = rs.getInt(21);
        hp.mouseDist2 = rs.getInt(22);

        hp.mouseDx = rs.getInt(23);
        hp.mouseDy = rs.getInt(24);
        hp.mouseVecx = rs.getInt(25);
        hp.mouseVecy = rs.getInt(26);

        hp.mouseMaxv = rs.getInt(27);
        hp.mouseMaxa = rs.getInt(28);
        hp.mouseMina = rs.getInt(29);
        hp.mouseMaxj = rs.getInt(30);

        hp.clickTime = rs.getInt(31);
        hp.loadTime = rs.getInt(32);
        hp.pixInPic = rs.getInt(33);
        hp.dotCount = rs.getInt(34);
        hp.pixOutPic = rs.getInt(35);

        hp.atomImpact = AtomSpec.fromInt(rs.getInt(36));
        hp.impactFactor = rs.getFloat(37);
        hp.picClik = rs.getString(38);

        hp.nTogs = rs.getInt(39);
        hp.togSides = rs.getString(40);
        String togts = rs.getString(41);
        if (togts != null) {
            hp.togTimes = MiscUtil.base64ToIntArray(togts);
        }

        hp.vertical = rs.getBoolean(42);
        hp.bigTime = rs.getInt(43);
        hp.bigStime = rs.getInt(44);

        hp.dotDist = rs.getInt(45);
        hp.dotVecLen = rs.getInt(46);
        hp.dotVecAng = rs.getInt(47);

        hp.dotMaxVel = rs.getInt(48);
        hp.dotMaxAccel = rs.getInt(49);
        hp.dotMaxJerk = rs.getInt(50);

        hp.dotStartScreen = rs.getInt(51);
        hp.dotEndScreen = rs.getInt(52);

        return hp;
    }

    private static HistoryPair showingPairIDBothFromResultSet(ResultSet rs) 
            throws SQLException {

        HistoryPair hp = new HistoryPair();

        hp.createTime = rs.getTimestamp(1);

        hp.id1 = rs.getString(2);
        hp.archive1 = rs.getInt(3);
        hp.fileName1 = rs.getString(4);

        hp.id2 = rs.getString(5);
        hp.archive2 = rs.getInt(6);
        hp.fileName2 = rs.getString(7);

        hp.vertical = rs.getBoolean(8);

        return hp;
    }
    private final static String SQL_GET_SHOWING_PAIR_BY_ID =
        "SELECT " +
          RECORD_FIELDS +
          SHOWING_PAIR_FIELDS +
        " FROM pr.showing_pair WHERE id = ?";

    public static HistoryPair getPairByID(Connection conn, 
                                                 long showingID) 
              throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            ps = conn.prepareStatement(SQL_GET_SHOWING_PAIR_BY_ID, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setLong(1, showingID);
            rs = ps.executeQuery();
            if (!rs.next()) {   // impossible?
                throw new SQLException("getPairByID: no result");
            }

            return showingPairFromResultSet(rs);

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

 
    private final static String SQL_GET_SHOWING_PAIRS_BY_IDS =
        "SELECT create_time, id1, archive1, file_name1, " +
        " id2, archive2, file_name2, vertical " +
        " FROM pr.showing_pair_ids_both WHERE id1 = ? AND id2 = ?";

    public static HistoryPair getPairByIDsAndTime(Connection conn, 
                                                 String id1, String id2, 
                                                 long t) 
              throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            ps = conn.prepareStatement(SQL_GET_SHOWING_PAIRS_BY_IDS, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, id1);
            ps.setString(2, id2);
            rs = ps.executeQuery();

            HistoryPair hp = null;
            int skipped = 0;

            while (rs.next()) {
                HistoryPair thp = showingPairIDBothFromResultSet(rs);
                if (Math.abs(t - thp.createTime.getTime()) > 2000) {
                    skipped++;
                    continue;
                }
                if (hp != null) {
                    log.warn(">1 hp's at time");
                    return null;
                }
                hp = thp;
            }

            return hp;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    public static List<HistoryPair> getPairsByIDs(Connection conn, 
                                                 String id1, String id2) 
              throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            ps = conn.prepareStatement(SQL_GET_SHOWING_PAIRS_BY_IDS, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, id1);
            ps.setString(2, id2);
            rs = ps.executeQuery();

            List<HistoryPair> ret = new ArrayList<>();

            while (rs.next()) {
                ret.add(showingPairIDBothFromResultSet(rs));
            }

            return ret;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private final static String SQL_GET_LAST_SHOWINGS =
        "SELECT " +
          RECORD_FIELDS +
          SHOWING_PAIR_FIELDS +
        " FROM pr.showing_pair WHERE browser_id = ? ORDER BY id DESC LIMIT ?";

    public static List<HistoryPair> getLastPairs(Connection conn, 
                                                long browserID, int n) 
              throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            ps = conn.prepareStatement(SQL_GET_LAST_SHOWINGS, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setLong(1, browserID);
            ps.setInt(2, n);
            rs = ps.executeQuery();

            List<HistoryPair> l = new ArrayList<>();

            while (rs.next()) {
                l.add(showingPairFromResultSet(rs));
            }
            return l;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private final static String SQL_COUNT_BROWSER_SHOWINGS =
        "SELECT COUNT(*)  FROM pr.showing_pair WHERE browser_id = ?";

    public static int countPairs(Connection conn, long browserID) 
              throws SQLException {
        return countPairs(conn, browserID, null);
    }

    public static int countPairs(Connection conn, long browserID, 
                                                     String orient) 
              throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            String query = SQL_COUNT_BROWSER_SHOWINGS;
            if (orient != null) {
                if ("v".equals(orient)) {
                    query += " AND vertical IS TRUE";
                } else {
                    query += " AND vertical IS FALSE";
                }
            }

            ps = conn.prepareStatement(query);
            ps.setLong(1, browserID);
            rs = ps.executeQuery();

            while (rs.next()) {
                int ct = rs.getInt(1);
                return ct;
            }
            return -99;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }


    private final static String SQL_GET_ALL_SHOWINGS =
        "SELECT " +
          RECORD_FIELDS +
          SHOWING_PAIR_FIELDS +
        " FROM pr.showing_pair WHERE browser_id = ? ORDER BY id ASC";

    public static List<HistoryPair> getAll(Connection conn, 
                                                   long browserID) 
              throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            ps = conn.prepareStatement(SQL_GET_ALL_SHOWINGS, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setLong(1, browserID);
            rs = ps.executeQuery();

            List<HistoryPair> l = new ArrayList<>();

            while (rs.next()) {
                l.add(showingPairFromResultSet(rs));
            }
            return l;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private final static String SQL_CHECK_SEEN =
        "SELECT COUNT(*) FROM pr.showing_pair " +
        " WHERE browser_id = ? AND id1 = ? AND id2 = ?";

    public static boolean checkSeen(Connection conn, long browserID,
                                    String id1, String id2) 
              throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(SQL_CHECK_SEEN);
            ps.setLong(1, browserID);
            ps.setString(2, id1);
            ps.setString(3, id2);

            rs = ps.executeQuery();

            if (!rs.next()) {
                throw new SQLException("No result");
            }
            return rs.getInt(1) > 0;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
   }

    private final static String SQL_ADD_RATING =
        "UPDATE pr.showing_pair SET rate_time = ?," +
                        " rating = ?, rating_scheme = ?," +
                        " user_time = ?, user_time2 = ?," +
                        " mouse_time = ?, watch_dots_time = ?," +
                        " mouse_down_time = ?, load_time = ?," +
                        " mouse_dist = ?, mouse_dist2 = ?," +
                        " mouse_dx = ?, mouse_dy = ?," +
                        " mouse_vecx = ?, mouse_vecy = ?," +
                        " mouse_maxv = ?, mouse_maxa = ?," +
                        " mouse_mina = ?, mouse_maxj = ?," +
                        " pix_in_pic = ?, dot_count = ?," +
                        " pix_out_pic = ?, pic_clik = ?, " +
                        " n_tog_last = ?, tog_sides = ?, " +
                        " tog_times = ?, big_time = ?, " +
                        " dot_start_scrn = ?, dot_end_scrn = ?, " +
                        " dot_dist = ?, dot_vec_len = ?, " +
                        " dot_vec_ang = ?, dot_max_vel = ?, " +
                        " dot_max_acc = ?, dot_max_jerk = ?" +
                        " WHERE id = ?";

    public static void updatePair(Connection conn, HistoryPair hp)
              throws SQLException {

        if ("na".equals(hp.togSides)) {
            hp.togSides = null;
        }
        if (MiscUtil.NULL_BASE64.equals(hp.toggleTStr)) {
            hp.toggleTStr = null;
        }

        if (hp.toggleTStr != null  &&  hp.toggleTStr.length() > 1024) {
            log.warn("TRUNCATING togTimestr: " + hp.toggleTStr);
            int i = 1024;
            while (hp.toggleTStr.charAt(i) != '_' && i > 0) i--;
            if (i == 0) {
                log.error("BAD togTimes setting -> null");
                hp.toggleTStr = null;
            } else {
                hp.toggleTStr = hp.toggleTStr.substring(0, i);
                log.warn("TRUNCATED togTimes: " + hp.toggleTStr);
            }
        }

        if (hp.clickTime > 32767) {
            // TODO - seems to happen when clicking on pic then going away
            log.warn("TRUNCating clickTime " + hp.clickTime + " to 32767");
            hp.clickTime = 32767;
        }
        if (hp.bigTime > 32767) {
            // TODO - seems to happen when building pairs indexes
            log.warn("TRUNCating bigTime " + hp.bigTime + " to 32767");
            hp.bigTime = 32767;
        }

        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(SQL_ADD_RATING);

            ps.setTimestamp(1,  hp.rateTime);

            ps.setInt(2,  hp.pairRating);
            ps.setInt(3,  hp.ratingScheme);

            ps.setInt(4,  hp.userTime);
            ps.setInt(5,  hp.userTime2);
            ps.setInt(6,  hp.clickTime); // mouse_time==draw time
            ps.setInt(7,  hp.watchDotsTime);
            ps.setInt(8,  hp.mouseDownTime);
            ps.setInt(9,  hp.loadTime);

            ps.setInt(10,  hp.mouseDist);
            ps.setInt(11,  hp.mouseDist2);

            ps.setInt(12,  hp.mouseDx);
            ps.setInt(13,  hp.mouseDy);

            ps.setInt(14,  hp.mouseVecx);
            ps.setInt(15,  hp.mouseVecy);

            ps.setInt(16,  hp.mouseMaxv);
            ps.setInt(17,  hp.mouseMaxa);

            ps.setInt(18,  hp.mouseMina);
            ps.setInt(19,  hp.mouseMaxj);

            ps.setInt(20,  hp.pixInPic);
            ps.setInt(21,  hp.dotCount);

            ps.setInt(22,  hp.pixOutPic);
            ps.setString(23, hp.picClik);

            ps.setInt(24,  hp.nTogs);
            ps.setString(25, hp.togSides);

            ps.setString(26, hp.toggleTStr);
            ps.setInt(27, hp.bigTime);

            ps.setInt(28, hp.dotStartScreen);
            ps.setInt(29, hp.dotEndScreen);

            ps.setInt(30, hp.dotDist);
            ps.setInt(31, hp.dotVecLen);

            ps.setInt(32, hp.dotVecAng);
            ps.setInt(33, hp.dotMaxVel);

            ps.setInt(34, hp.dotMaxAccel);
            ps.setInt(35, hp.dotMaxJerk);

            // where
            ps.setLong(36, hp.id);

            int rows = ps.executeUpdate();
            if (rows != 1) {
                throw new SQLException("updatePair update rows != 1: " + 
                                       rows);
            }
        } catch (SQLException sqe) {
            throw sqe;
        } finally {
            closeSQL(ps);
        }
    }
}

