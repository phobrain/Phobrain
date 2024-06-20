package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  ShowingPairDao  - pr.showing_pair - record what shown and response
 **/

import org.phobrain.db.record.ShowingPair;
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

    public static void insertShowingPair(Connection conn, ShowingPair s) 
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

    public static ShowingPair getLastShowingToBrowser(Connection conn, 
                                                  long browserID)
              throws SQLException {
        long showingID = getLastShowingID(conn, browserID);
        if (showingID == -1) {
            return null;
        }
        return getShowingPairByID(conn, showingID);
    }

    private final static String SQL_GET_LAST_SHOWING_ID =
        "SELECT MAX(id) FROM pr.showing_pair WHERE browser_id = ?";

    public static long getLastShowingID(Connection conn, long browserID)
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
                throw new SQLException("getLastShowing: no result");
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

    private static ShowingPair showingPairFromResultSet(ResultSet rs) 
            throws SQLException {

        ShowingPair sp = new ShowingPair();

        sp.id = rs.getLong(1);
        sp.createTime = rs.getTimestamp(2);

        sp.browserID = rs.getLong(3);
        sp.callCount = rs.getInt(4);
        sp.orderInSession = rs.getInt(5);
 
        sp.id1 = rs.getString(6);
        sp.archive1 = rs.getInt(7);
        sp.fileName1 = rs.getString(8);
        sp.selMethod1 = rs.getString(9);

        sp.id2 = rs.getString(10);
        sp.archive2 = rs.getInt(11);
        sp.fileName2 = rs.getString(12);
        sp.selMethod2 = rs.getString(13);

        sp.rating = rs.getInt(14);
        sp.ratingScheme = rs.getInt(15);

        sp.rateTime = getTimestamp(rs, 16);
        sp.userTime = rs.getInt(17);
        sp.userTime2 = rs.getInt(18);
        sp.watchDotsTime = rs.getInt(19);
        sp.mouseDownTime = rs.getInt(20);

        sp.mouseDist = rs.getInt(21);
        sp.mouseDist2 = rs.getInt(22);

        sp.mouseDx = rs.getInt(23);
        sp.mouseDy = rs.getInt(24);
        sp.mouseVecx = rs.getInt(25);
        sp.mouseVecy = rs.getInt(26);

        sp.mouseMaxv = rs.getInt(27);
        sp.mouseMaxa = rs.getInt(28);
        sp.mouseMina = rs.getInt(29);
        sp.mouseMaxj = rs.getInt(30);

        sp.clickTime = rs.getInt(31);
        sp.loadTime = rs.getInt(32);
        sp.pixInPic = rs.getInt(33);
        sp.dotCount = rs.getInt(34);
        sp.pixOutPic = rs.getInt(35);

        sp.atomImpact = AtomSpec.fromInt(rs.getInt(36));
        sp.impactFactor = rs.getFloat(37);
        sp.picClik = rs.getString(38);

        sp.nTogs = rs.getInt(39);
        sp.togSides = rs.getString(40);
        String togts = rs.getString(41);
        if (togts != null) {
            sp.togTimes = MiscUtil.base64ToIntArray(togts);
        }

        sp.vertical = rs.getBoolean(42);
        sp.bigTime = rs.getInt(43);
        sp.bigStime = rs.getInt(44);

        sp.dotDist = rs.getInt(45);
        sp.dotVecLen = rs.getInt(46);
        sp.dotVecAng = rs.getInt(47);

        sp.dotMaxVel = rs.getInt(48);
        sp.dotMaxAccel = rs.getInt(49);
        sp.dotMaxJerk = rs.getInt(50);

        sp.dotStartScreen = rs.getInt(51);
        sp.dotEndScreen = rs.getInt(52);

        return sp;
    }

    private static ShowingPair showingPairIDBothFromResultSet(ResultSet rs) 
            throws SQLException {

        ShowingPair sp = new ShowingPair();

        sp.createTime = rs.getTimestamp(1);

        sp.id1 = rs.getString(2);
        sp.archive1 = rs.getInt(3);
        sp.fileName1 = rs.getString(4);

        sp.id2 = rs.getString(5);
        sp.archive2 = rs.getInt(6);
        sp.fileName2 = rs.getString(7);

        sp.vertical = rs.getBoolean(8);

        return sp;
    }
    private final static String SQL_GET_SHOWING_PAIR_BY_ID =
        "SELECT " +
          RECORD_FIELDS +
          SHOWING_PAIR_FIELDS +
        " FROM pr.showing_pair WHERE id = ?";

    public static ShowingPair getShowingPairByID(Connection conn, 
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
                throw new SQLException("getLastShowingPairByID: no result");
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

    public static ShowingPair getShowingPairByIDsAndTime(Connection conn, 
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

            ShowingPair sp = null;
            int skipped = 0;

            while (rs.next()) {
                ShowingPair tsp = showingPairIDBothFromResultSet(rs);
                if (Math.abs(t - tsp.createTime.getTime()) > 2000) {
                    skipped++;
                    continue;
                }
                if (sp != null) {
                    log.warn(">1 sp's at time");
                    return null;
                }
                sp = tsp;
            }

            return sp;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    public static List<ShowingPair> getShowingPairsByIDs(Connection conn, 
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

            List<ShowingPair> ret = new ArrayList<>();

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

    public static List<ShowingPair> getLastShowings(Connection conn, 
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

            List<ShowingPair> l = new ArrayList<>();

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

    public static int countShowings(Connection conn, long browserID) 
              throws SQLException {
        return countShowings(conn, browserID, null);
    }

    public static int countShowings(Connection conn, long browserID, 
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

    public static List<ShowingPair> getAllShowings(Connection conn, 
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

            List<ShowingPair> l = new ArrayList<>();

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

    public static void updateShowingPair(Connection conn, ShowingPair sp)
              throws SQLException {

        if ("na".equals(sp.togSides)) {
            sp.togSides = null;
        }
        if (MiscUtil.NULL_BASE64.equals(sp.toggleTStr)) {
            sp.toggleTStr = null;
        }

        if (sp.toggleTStr != null  &&  sp.toggleTStr.length() > 1024) {
            log.warn("TRUNCATING togTimestr: " + sp.toggleTStr);
            int i = 1024;
            while (sp.toggleTStr.charAt(i) != '_' && i > 0) i--;
            if (i == 0) {
                log.error("BAD togTimes setting -> null");
                sp.toggleTStr = null;
            } else {
                sp.toggleTStr = sp.toggleTStr.substring(0, i);
                log.warn("TRUNCATED togTimes: " + sp.toggleTStr);
            }
        }

        if (sp.clickTime > 32767) {
            // TODO - seems to happen when clicking on pic then going away
            log.warn("TRUNCating clickTime " + sp.clickTime + " to 32767");
            sp.clickTime = 32767;
        }
        if (sp.bigTime > 32767) {
            // TODO - seems to happen when building pairs indexes
            log.warn("TRUNCating bigTime " + sp.bigTime + " to 32767");
            sp.bigTime = 32767;
        }

        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(SQL_ADD_RATING);

            ps.setTimestamp(1,  sp.rateTime);

            ps.setInt(2,  sp.rating);
            ps.setInt(3,  sp.ratingScheme);

            ps.setInt(4,  sp.userTime);
            ps.setInt(5,  sp.userTime2);
            ps.setInt(6,  sp.clickTime); // mouse_time==draw time
            ps.setInt(7,  sp.watchDotsTime);
            ps.setInt(8,  sp.mouseDownTime);
            ps.setInt(9,  sp.loadTime);

            ps.setInt(10,  sp.mouseDist);
            ps.setInt(11,  sp.mouseDist2);

            ps.setInt(12,  sp.mouseDx);
            ps.setInt(13,  sp.mouseDy);

            ps.setInt(14,  sp.mouseVecx);
            ps.setInt(15,  sp.mouseVecy);

            ps.setInt(16,  sp.mouseMaxv);
            ps.setInt(17,  sp.mouseMaxa);

            ps.setInt(18,  sp.mouseMina);
            ps.setInt(19,  sp.mouseMaxj);

            ps.setInt(20,  sp.pixInPic);
            ps.setInt(21,  sp.dotCount);

            ps.setInt(22,  sp.pixOutPic);
            ps.setString(23, sp.picClik);

            ps.setInt(24,  sp.nTogs);
            ps.setString(25, sp.togSides);

            ps.setString(26, sp.toggleTStr);
            ps.setInt(27, sp.bigTime);

            ps.setInt(28, sp.dotStartScreen);
            ps.setInt(29, sp.dotEndScreen);

            ps.setInt(30, sp.dotDist);
            ps.setInt(31, sp.dotVecLen);

            ps.setInt(32, sp.dotVecAng);
            ps.setInt(33, sp.dotMaxVel);

            ps.setInt(34, sp.dotMaxAccel);
            ps.setInt(35, sp.dotMaxJerk);

            // where
            ps.setLong(36, sp.id);

            int rows = ps.executeUpdate();
            if (rows != 1) {
                throw new SQLException("updateShowingPair update rows != 1: " + 
                                       rows);
            }
        } catch (SQLException sqe) {
            throw sqe;
        } finally {
            closeSQL(ps);
        }
    }
}

