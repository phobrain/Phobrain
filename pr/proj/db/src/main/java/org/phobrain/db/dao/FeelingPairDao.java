package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  FeelingPairDao  - pr.feeling_pair - record what shown and response
 **/

import org.phobrain.db.record.FeelingPair;
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

public class FeelingPairDao extends DaoBase {

    private static final Logger log = LoggerFactory.getLogger(
                                                   FeelingPairDao.class);

    private final static String RECORD_FIELDS =
        " id, create_time, ";

    private final static String FEELING_PAIR_FIELDS =
        " browser_id, call_count, order_in_session," +
        " id1, archive1, file_name1, sel_method1," +
        " id2, archive2, file_name2, sel_method2," +
        " pair_rating, flow_rating, rating_scheme," +
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

    private final static String SQL_INSERT_FEELING_PAIR =
        "INSERT INTO pr.feeling_pair " +
        " (browser_id, vertical, call_count, order_in_session," +
        "  id1, archive1, file_name1, sel_method1," +
        "  id2, archive2, file_name2, sel_method2," +
        "  atom_impact, impact_factor, big_stime)" +
        " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public static void insertFeelingPair(Connection conn, FeelingPair fp) 
                                                  throws SQLException {

        log.info("insertFeelingPair: " + fp);

        PreparedStatement ps = null;
        ResultSet rs = null;
        ResultSet generatedKeys = null;

        try {
            ps = conn.prepareStatement(SQL_INSERT_FEELING_PAIR,
                                       Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1,    fp.browserID);
            ps.setBoolean(2, fp.vertical);
            ps.setInt(3,     fp.callCount);
            ps.setInt(4,     fp.orderInSession);
            ps.setString(5,  fp.id1);
            ps.setInt(6,     fp.archive1);
            ps.setString(7,  fp.fileName1);
            ps.setString(8,  fp.selMethod1);
            ps.setString(9,  fp.id2);
            ps.setInt(10,    fp.archive2);
            ps.setString(11, fp.fileName2);
            ps.setString(12, fp.selMethod2);
            ps.setInt(13,    fp.atomImpact.intValue());
            ps.setFloat(14,  fp.impactFactor);
            if (fp.bigStime > 32767) {
                ps.setInt(15, 32767);
            } else {
                ps.setInt(15, fp.bigStime);
            }

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Insert returned 0");
            }
            generatedKeys = ps.getGeneratedKeys();
            if (generatedKeys.next()) {
                fp.id = generatedKeys.getLong(1);
                log.info("insertFeelingPair: INSERTED " + fp);
                return;
            } 
            throw new SQLException( "Creating feeling_pair failed, no ID obtained.");

        } finally {
            closeSQL(generatedKeys);
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_COUNT_FEELING_PAIRS =
        "SELECT count(*) FROM pr.feeling_pair" +
        " WHERE create_time > now() - interval '5 seconds'";

    public static int getCount(Connection conn) 
                          throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(SQL_COUNT_FEELING_PAIRS,
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
        "SELECT id1, id2 FROM pr.feeling_pair WHERE browser_id = ?";

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
        "SELECT id1, id2 FROM pr.feeling_pair WHERE sel_method1 @@ " +
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

    public static FeelingPair getLastFeelingToBrowser(Connection conn, 
                                                  long browserID)
              throws SQLException {
        long feelingID = getLastFeelingID(conn, browserID);
        if (feelingID == -1) {
            return null;
        }
        return getFeelingPairByID(conn, feelingID);
    }

    private final static String SQL_GET_LAST_FEELING_ID =
        "SELECT MAX(id) FROM pr.feeling_pair WHERE browser_id = ?";

    public static long getLastFeelingID(Connection conn, long browserID)
              throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            ps = conn.prepareStatement(SQL_GET_LAST_FEELING_ID, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setLong(1, browserID);
            rs = ps.executeQuery();
            if (!rs.next()) {   // impossible?
                throw new SQLException("getLastFeeling: no result");
            }
            long feelingID = rs.getLong(1);
            if (feelingID == 0) {
                return -1; // seems better
            }
            return feelingID;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static FeelingPair feelingPairFromResultSet(ResultSet rs) 
            throws SQLException {

        FeelingPair fp = new FeelingPair();

        fp.id = rs.getLong(1);
        fp.createTime = rs.getTimestamp(2);

        fp.browserID = rs.getLong(3);
        fp.callCount = rs.getInt(4);
        fp.orderInSession = rs.getInt(5);
 
        fp.id1 = rs.getString(6);
        fp.archive1 = rs.getInt(7);
        fp.fileName1 = rs.getString(8);
        fp.selMethod1 = rs.getString(9);

        fp.id2 = rs.getString(10);
        fp.archive2 = rs.getInt(11);
        fp.fileName2 = rs.getString(12);
        fp.selMethod2 = rs.getString(13);

        fp.pairRating = rs.getInt(14);
        fp.flowRating = rs.getInt(15);
        fp.ratingScheme = rs.getInt(16);  // vestigial

        fp.rateTime = getTimestamp(rs, 17);
        fp.userTime = rs.getInt(18);
        fp.userTime2 = rs.getInt(19);
        fp.watchDotsTime = rs.getInt(20);
        fp.mouseDownTime = rs.getInt(21);

        fp.mouseDist = rs.getInt(22);
        fp.mouseDist2 = rs.getInt(23);

        fp.mouseDx = rs.getInt(24);
        fp.mouseDy = rs.getInt(25);
        fp.mouseVecx = rs.getInt(26);
        fp.mouseVecy = rs.getInt(27);

        fp.mouseMaxv = rs.getInt(28);
        fp.mouseMaxa = rs.getInt(29);
        fp.mouseMina = rs.getInt(30);
        fp.mouseMaxj = rs.getInt(31);

        fp.clickTime = rs.getInt(32);
        fp.loadTime = rs.getInt(33);
        fp.pixInPic = rs.getInt(34);
        fp.dotCount = rs.getInt(35);
        fp.pixOutPic = rs.getInt(36);

        fp.atomImpact = AtomSpec.fromInt(rs.getInt(37));
        fp.impactFactor = rs.getFloat(38);
        fp.picClik = rs.getString(39);

        fp.nTogs = rs.getInt(40);
        fp.togSides = rs.getString(41);
        String togts = rs.getString(42);
        if (togts != null) {
            fp.togTimes = MiscUtil.base64ToIntArray(togts);
        }

        fp.vertical = rs.getBoolean(43);
        fp.bigTime = rs.getInt(44);
        fp.bigStime = rs.getInt(45);

        fp.dotDist = rs.getInt(46);
        fp.dotVecLen = rs.getInt(47);
        fp.dotVecAng = rs.getInt(48);

        fp.dotMaxVel = rs.getInt(49);
        fp.dotMaxAccel = rs.getInt(50);
        fp.dotMaxJerk = rs.getInt(51);

        fp.dotStartScreen = rs.getInt(52);
        fp.dotEndScreen = rs.getInt(53);

        return fp;
    }

    private static FeelingPair feelingPairIDBothFromResultSet(ResultSet rs) 
            throws SQLException {

        FeelingPair fp = new FeelingPair();

        fp.createTime = rs.getTimestamp(1);

        fp.id1 = rs.getString(2);
        fp.archive1 = rs.getInt(3);
        fp.fileName1 = rs.getString(4);

        fp.id2 = rs.getString(5);
        fp.archive2 = rs.getInt(6);
        fp.fileName2 = rs.getString(7);

        fp.vertical = rs.getBoolean(8);

        return fp;
    }
    private final static String SQL_GET_FEELING_PAIR_BY_ID =
        "SELECT " +
          RECORD_FIELDS +
          FEELING_PAIR_FIELDS +
        " FROM pr.feeling_pair WHERE id = ?";

    public static FeelingPair getFeelingPairByID(Connection conn, 
                                                 long feelingID) 
              throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            ps = conn.prepareStatement(SQL_GET_FEELING_PAIR_BY_ID, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setLong(1, feelingID);
            rs = ps.executeQuery();
            if (!rs.next()) {   // impossible?
                throw new SQLException("getLastFeelingPairByID: no result");
            }

            return feelingPairFromResultSet(rs);

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

 
    private final static String SQL_GET_FEELING_PAIRS_BY_IDS =
        "SELECT create_time, id1, archive1, file_name1, " +
        " id2, archive2, file_name2, vertical " +
        " FROM pr.feeling_pair_ids_both WHERE id1 = ? AND id2 = ?";

    public static FeelingPair getFeelingPairByIDsAndTime(Connection conn, 
                                                 String id1, String id2, 
                                                 long t) 
              throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            ps = conn.prepareStatement(SQL_GET_FEELING_PAIRS_BY_IDS, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, id1);
            ps.setString(2, id2);
            rs = ps.executeQuery();

            FeelingPair fp = null;
            int skipped = 0;

            while (rs.next()) {
                FeelingPair tfp = feelingPairIDBothFromResultSet(rs);
                if (Math.abs(t - tfp.createTime.getTime()) > 2000) {
                    skipped++;
                    continue;
                }
                if (fp != null) {
                    log.warn(">1 fp's at time");
                    return null;
                }
                fp = tfp;
            }

            return fp;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    public static List<FeelingPair> getFeelingPairsByIDs(Connection conn, 
                                                 String id1, String id2) 
              throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            ps = conn.prepareStatement(SQL_GET_FEELING_PAIRS_BY_IDS, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, id1);
            ps.setString(2, id2);
            rs = ps.executeQuery();

            List<FeelingPair> ret = new ArrayList<>();

            while (rs.next()) {
                ret.add(feelingPairIDBothFromResultSet(rs));
            }

            return ret;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private final static String SQL_GET_LAST_FEELINGS =
        "SELECT " +
          RECORD_FIELDS +
          FEELING_PAIR_FIELDS +
        " FROM pr.feeling_pair WHERE browser_id = ? ORDER BY id DESC LIMIT ?";

    public static List<FeelingPair> getLastFeelings(Connection conn, 
                                                long browserID, int n) 
              throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            ps = conn.prepareStatement(SQL_GET_LAST_FEELINGS, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setLong(1, browserID);
            ps.setInt(2, n);
            rs = ps.executeQuery();

            List<FeelingPair> l = new ArrayList<>();

            while (rs.next()) {
                l.add(feelingPairFromResultSet(rs));
            }
            return l;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private final static String SQL_COUNT_BROWSER_FEELINGS =
        "SELECT COUNT(*)  FROM pr.feeling_pair WHERE browser_id = ?";

    public static int countFeelings(Connection conn, long browserID) 
              throws SQLException {
        return countFeelings(conn, browserID, null);
    }

    public static int countFeelings(Connection conn, long browserID, 
                                                     String orient) 
              throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            String query = SQL_COUNT_BROWSER_FEELINGS;
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


    private final static String SQL_GET_ALL_FEELINGS =
        "SELECT " +
          RECORD_FIELDS +
          FEELING_PAIR_FIELDS +
        " FROM pr.feeling_pair WHERE browser_id = ? ORDER BY id ASC";

    public static List<FeelingPair> getAllFeelings(Connection conn, 
                                                   long browserID) 
              throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            ps = conn.prepareStatement(SQL_GET_ALL_FEELINGS, 
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setLong(1, browserID);
            rs = ps.executeQuery();

            List<FeelingPair> l = new ArrayList<>();

            while (rs.next()) {
                l.add(feelingPairFromResultSet(rs));
            }
            return l;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private final static String SQL_CHECK_SEEN =
        "SELECT COUNT(*) FROM pr.feeling_pair " +
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
        "UPDATE pr.feeling_pair SET rate_time = ?," +
                        " pair_rating = ?, flow_rating = ?, rating_scheme = ?," +
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

    /*
    **  updateFeelingPair - add user response: 
    **                      rating [u u n a a]
    **                      drawing that was done after rating it
    **                              that triggered next FeelingPair.
    */

    public static void updateFeelingPair(Connection conn, FeelingPair fp)
              throws SQLException {


        if (fp.flowRating <= 0) {
            log.error("updateFeelingPair: flowRating is " + fp.flowRating +
                        " (still updating to save other info): " + fp);
        } else {
            log.info("updateFeelingPair to " + fp);
        }
        // clean up

        if ("na".equals(fp.togSides)) {
            fp.togSides = null;
        }
        if (MiscUtil.NULL_BASE64.equals(fp.toggleTStr)) {
            fp.toggleTStr = null;
        }

        if (fp.toggleTStr != null  &&  fp.toggleTStr.length() > 1024) {
            log.warn("TRUNCATING togTimestr: " + fp.toggleTStr);
            int i = 1024;
            while (fp.toggleTStr.charAt(i) != '_' && i > 0) i--;
            if (i == 0) {
                log.error("BAD togTimes setting -> null");
                fp.toggleTStr = null;
            } else {
                fp.toggleTStr = fp.toggleTStr.substring(0, i);
                log.warn("TRUNCATED togTimes: " + fp.toggleTStr);
            }
        }

        if (fp.clickTime > 32767) {
            // TODO - seems to happen when clicking on pic then going away
            log.warn("TRUNCating clickTime " + fp.clickTime + " to 32767");
            fp.clickTime = 32767;
        }
        if (fp.bigTime > 32767) {
            // TODO - seems to happen when building pairs indexes
            log.warn("TRUNCating bigTime " + fp.bigTime + " to 32767");
            fp.bigTime = 32767;
        }

        // update

        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(SQL_ADD_RATING);

            ps.setTimestamp(1,  fp.rateTime);

            ps.setInt(2,  fp.pairRating);
            ps.setInt(3,  fp.flowRating);
            ps.setInt(4,  fp.ratingScheme);

            ps.setInt(5,  fp.userTime);
            ps.setInt(6,  fp.userTime2);
            ps.setInt(7,  fp.clickTime); // mouse_time==draw time
            ps.setInt(8,  fp.watchDotsTime);
            ps.setInt(9,  fp.mouseDownTime);
            ps.setInt(10,  fp.loadTime);

            ps.setInt(11,  fp.mouseDist);
            ps.setInt(12,  fp.mouseDist2);

            ps.setInt(13,  fp.mouseDx);
            ps.setInt(14,  fp.mouseDy);

            ps.setInt(15,  fp.mouseVecx);
            ps.setInt(16,  fp.mouseVecy);

            ps.setInt(17,  fp.mouseMaxv);
            ps.setInt(18,  fp.mouseMaxa);

            ps.setInt(19,  fp.mouseMina);
            ps.setInt(20,  fp.mouseMaxj);

            ps.setInt(21,  fp.pixInPic);
            ps.setInt(22,  fp.dotCount);

            ps.setInt(23,  fp.pixOutPic);
            ps.setString(24, fp.picClik);

            ps.setInt(25,  fp.nTogs);
            ps.setString(26, fp.togSides);

            ps.setString(27, fp.toggleTStr);
            ps.setInt(28, fp.bigTime);

            ps.setInt(29, fp.dotStartScreen);
            ps.setInt(30, fp.dotEndScreen);

            ps.setInt(31, fp.dotDist);
            ps.setInt(32, fp.dotVecLen);

            ps.setInt(33, fp.dotVecAng);
            ps.setInt(34, fp.dotMaxVel);

            ps.setInt(35, fp.dotMaxAccel);
            ps.setInt(36, fp.dotMaxJerk);

            // where
            ps.setLong(37, fp.id);

            int rows = ps.executeUpdate();
            if (rows != 1) {
                throw new SQLException("updateFeelingPair update rows != 1: " + 
                                       rows);
            }
        } catch (SQLException sqe) {
            throw sqe;
        } finally {
            closeSQL(ps);
        }
        log.info("updateFeelingPair UPDATED " + fp.id + "  -> flowRating " + fp.flowRating);
    }
}

