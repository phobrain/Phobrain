package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  ApprovalDao  - add to pr.approved_pairs, update status.
 **
 **/

import org.phobrain.util.MiscUtil;
import org.phobrain.util.ID;

import org.phobrain.db.record.ApprovedPair;
import org.phobrain.db.record.Picture;

import javax.naming.InvalidNameException;

import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApprovalDao extends DaoBase {

    private static final Logger log = LoggerFactory.getLogger(
                                                 ApprovalDao.class);

    // trim should work
    private final static String SQL_CHECK =
        "SELECT count(*) FROM ##approved_pair " +
        "WHERE browser_id = ? AND id1 = ? AND id2 = ?";

    private final static String SQL_BLIND_CHECK =
        "SELECT EXISTS( SELECT 1 FROM ##approved_pair " +
        "WHERE id1 = ? AND id2 = ?)";

    public static boolean any(Connection conn, String id1, String id2)
            throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(chooseDB(SQL_BLIND_CHECK));

            ps.setString(1, id1);
            ps.setString(2, id2);

            rs = ps.executeQuery();

            if (!rs.next()) {
                throw new SQLException("No bool resultset");
            }
            return rs.getBoolean(1);

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    // curation
    private final static String SQL_SETTLED_CHECK =
        "SELECT EXISTS( SELECT 1 FROM pr.approved_pair " +
        "WHERE id1 = ? AND id2 = ? AND (status=1 OR status=2))";

    public static boolean settled(Connection conn, String id1, String id2)
            throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(SQL_SETTLED_CHECK);

            ps.setString(1, id1);
            ps.setString(2, id2);

            rs = ps.executeQuery();

            if (!rs.next()) {
                throw new SQLException("No bool resultset");
            }
            return rs.getBoolean(1);

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    // curation?
    private final static String SQL_ID_CHECK =
        "SELECT COUNT(*) FROM pr.approved_pair " +
        "WHERE (id1 = ? OR id2 = ?) AND (status = 1 OR status = 0)";

    public static int idStatusCount(Connection conn, String id)
            throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(chooseDB(SQL_ID_CHECK));

            ps.setString(1, id);
            ps.setString(2, id);

            rs = ps.executeQuery();

            if (!rs.next()) {
                throw new SQLException("No int resultset");
            }
            return rs.getInt(1);

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }


    private final static String SQL_GENERAL_CHECK =
        "SELECT curator, browser_id, status, create_time FROM pr.approved_pair " +
        "WHERE id1 = ? AND id2 = ?";

    private final static String SQL_INSERT =
        "INSERT INTO pr.approved_pair " +
        " (browser_id, curator, id1, id2, status, vertical," + // has_kwd, +
        "  r, g, b, ll, rgb_radius, lab_contrast, density," +
        "  d0, match_people)" +
        " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public static String insert(Connection conn, boolean skipIfAny,//ignored
                                               ApprovedPair ap)
            throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // only screens browser hitting button twice
            ps = conn.prepareStatement(chooseDB(SQL_GENERAL_CHECK));
            ps.setString(1, ap.id1);
            ps.setString(2, ap.id2);
            rs = ps.executeQuery();
            if (rs.next()) {
                String otherCurator = rs.getString(1);
                Integer status = rs.getInt(3);
                Timestamp create_time = rs.getTimestamp(4);
                closeSQL(rs);
                closeSQL(ps);
                if (ap.status == status) {
                    log.info("insert: status " + status + " exists for " + 
                              ap.id1 + " " + ap.id2 +
                              " curator " + otherCurator);
                
                    if (status == 0) {
                        // if old enough, promote
                        if (create_time.getTime() < 
                                        System.currentTimeMillis() - 
                                        TimeUnit.HOURS.toMillis(4)) {

                            log.info("Converting insert/0 -> 1 on " +
                                     "already-inserted/status=0 from " + 
                                     otherCurator + ": " +
                                     ap.curator + " " + ap.id1 + " " + ap.id2);
                               return update(conn, ap.curator, 1, 
                                                   ap.id1, ap.id2);
                        }
                        log.info("insert: ignoring 0 on 0" +
                                 " since existing 0 < 4 hours old");
                    } else {
                        log.info("insert: ignoring same-status " + status +
                                     " " + ap.id1 + " " + ap.id2);
                    }
                    return null;
                }
                if (status == 1  &&  ap.status == 0) {
                    log.info("insert 0: ignoring status 1" + 
                                     " " + ap.id1 + " " + ap.id2);
                    return null;
                }
                if (status != 2  && (ap.status == 0  ||  ap.status == 1)) {
                    log.info("Converting insert " + ap.status +
                              " on already-inserted status " + status + 
                              " to status 1" +
                              " insert curator: " + otherCurator + ": " +
                              ap.curator + " " + ap.id1 + " " + ap.id2);
                    return update(conn, ap.curator, 1, ap.id1, ap.id2);
                }
                log.info("Converting insert on already-inserted " +
                              " status " + status + 
                              " to status " + ap.status +
                              " insert curator: " + otherCurator + ": " +
                              ap.curator + " " + ap.id1 + " " + ap.id2);
                String msg = update(conn, ap.curator, ap.status, 
                                          ap.id1, ap.id2);
                if ((status == 1  ||  status == 2)  &&
                    (ap.status == 1  ||  ap.status == 2)) {

                    if (msg == null) {
                        return "Flipped " + status + "->" + ap.status;
                    }
                    return msg + " (Flip attempt " + 
                                    status + "->" + ap.status + ")";
                }
            }

            closeSQL(rs);
            closeSQL(ps);

            ps = conn.prepareStatement(chooseDB(SQL_INSERT));
            ps.setLong(1,     ap.browserID);
            ps.setString(2,   ap.curator);
            ps.setString(3,   ap.id1);
            ps.setString(4,   ap.id2);
            ps.setInt(5,      ap.status);
            ps.setBoolean(6,  ap.vertical);
            //ps.setBoolean(7,  ap.hasKwd);
            ps.setInt(7,      ap.r);
            ps.setInt(8,      ap.g);
            ps.setInt(9,      ap.b);
            ps.setInt(10,     ap.ll);
            ps.setInt(11,     ap.rgbRadius);
            ps.setInt(12,     ap.labContrast);
            ps.setInt(13,     ap.density);
            ps.setLong(14,    ap.d0);
            ps.setBoolean(15, ap.matchPeople);

            int rows = ps.executeUpdate();
            if (rows != 1) {
                throw new SQLException("Insert returned " + rows);
            }
            return null;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    // Assuming only used on a basic non-trim db, 
    // even w/ trim in effect on other tables, since
    // used for curation. Might turn out to be
    // used in trim-only situation, be aware.
    private static final String SQL_GET_APPROVED_PAIR =
        "SELECT create_time, curator, status, id1, id2, vertical," + // has_kwd, + 
          "  r, g, b, ll, rgb_radius, lab_contrast, density," +
          " d0, match_people" +
          " FROM pr.approved_pair" +
          " WHERE id1 = ? AND id2 = ? ";

    public static ApprovedPair getApprovedPair(Connection conn, 
                                                String id1, String id2)
            throws SQLException {
        return getApprovedPair(conn, 1, id1, id1);
    }

    private static ApprovedPair truncPairFromRs(ResultSet rs) 
            throws SQLException {

        String id1 = rs.getString(4);
        String id2 = rs.getString(5);

        ApprovedPair ap = new ApprovedPair(id1, id2);

        ap.createTime = rs.getTimestamp(1);
        ap.browserID = -1;
        ap.curator = rs.getString(2);
        ap.status = rs.getInt(3);
        //ap.id1 = rs.getString(4);
        //ap.id2 = rs.getString(5);
        ap.vertical = rs.getBoolean(6);
        //ap.hasKwd = rs.getBoolean(7);

        ap.r = rs.getInt(7);
        ap.g = rs.getInt(8);
        ap.b = rs.getInt(9);
        ap.ll = rs.getInt(10);
        ap.rgbRadius = rs.getInt(11);
        ap.labContrast = rs.getInt(12);
        ap.density = rs.getInt(13);

        ap.d0 = rs.getLong(14);
        ap.matchPeople = rs.getBoolean(15);

        return ap;
    }
    /**
     **  getApprovedPair: status==-1 for any status
     **/
    public static ApprovedPair getApprovedPair(Connection conn, int status,
                                                String id1, String id2)
            throws SQLException {

        String query = SQL_GET_APPROVED_PAIR;
        if (status < 0) {
            query = query.replace("XXXX", "");
        } else {
            query = query + " AND status = ?";
        }

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(query,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, id1);
            ps.setString(2, id2);
            if (status >= 0) {
                ps.setInt(3, status);
            }
            rs = ps.executeQuery();
            if (rs.next()) {
                return truncPairFromRs(rs);
            }
            return null;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    // likewise update is a non-trim thing for now

    private final static String SQL_UPDATE = 
        "UPDATE pr.approved_pair SET status = ? WHERE id1 = ? AND id2 = ?";

    private final static String SQL_UPDATE_UPPER = 
        "UPDATE pr.approved_pair SET status = ?, curator = upper(curator)" +
        "  WHERE id1 = ? AND id2 = ?";

    private final static String SQL_UPDATE_LOWER = 
        "UPDATE pr.approved_pair SET status = ?, curator = lower(curator)" +
        "  WHERE id1 = ? AND id2 = ?";

    public static String update(Connection conn, String curator, int status,
                                               String id1, String id2) 
            throws SQLException {

        boolean uppercase = false;
        if (status < 0) {
            status *= -1;
            uppercase = true;
        }
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            ps = conn.prepareStatement(chooseDB(SQL_GENERAL_CHECK));
            ps.setString(1, id1);
            ps.setString(2, id2);
            rs = ps.executeQuery();
            if (!rs.next()) {
                // servlet depends on starting "No update: "
                String msg = "No update: no record: " + id1 + " " + id2;
                log.info(msg);
                return msg;
            }
            String s = rs.getString(1);
            if (!s.equalsIgnoreCase(curator)  &&  
                !"bill".equalsIgnoreCase(curator)  &&
                !"bill2".equalsIgnoreCase(curator)  &&
                !"bill3".equalsIgnoreCase(curator)  &&
                !"bill4".equalsIgnoreCase(curator)  &&
                !"ripplay".equals(curator)) {

                log.warn("Skipping non-owner/non-bill/ripplay update: " + 
                             curator + " updating " + s + " " + 
                             id1 + " " + id2);
                return "Owned by " + s;
            }
            int oldstatus = rs.getInt(3);
            if (status == oldstatus) {
                if (uppercase) {
                    log.info("skipping redundant bulk update");
                    return null;
                } else if (Character.isUpperCase(s.charAt(0))) {
                    log.info("overriding bulk update w/ manual");
                } else {
                    log.info("skipping redundant non-bulk update");
                    return null;
                }
            }
            ps.close();
            ps = null;
            if (uppercase) {
                ps = conn.prepareStatement(chooseDB(SQL_UPDATE_UPPER));
            } else if (status == oldstatus) {
                ps = conn.prepareStatement(chooseDB(SQL_UPDATE_LOWER));
            } else {
                ps = conn.prepareStatement(chooseDB(SQL_UPDATE));
            }
            ps.setInt(1, status);
            ps.setString(2, id1);
            ps.setString(3, id2);
            int r = ps.executeUpdate();
            if (r == 0) {
                // servlet depends on starting "No update: "
                String msg = "No update: " + id1 + " " + id2 + " " + r;
                log.info(msg);
                return msg;
            } else if (r != 1) {
                String msg = "Updated >1 rows: " + id1 + " " + id2 + ": " + r;
                log.error(msg);
                return msg;
            }
            log.info("Updated to status " + status + ": " + id1 + " " + id2 + 
                                             (uppercase ? "/Upper" : ""));
            if (status != 1) {
                return null;
            }

            ApprovedPair flipap = getApprovedPair(conn, id2, id1);
            if (flipap != null) {
                log.info("(flip exists, status=" + flipap.status + ")");
                return null;
            }

            if (any(conn, id2, id1)) {
                //log.warn("(flip exists -v2??)");
                return null;
            }

            // flipped version seen
            if (UniquePairDao.seenPair(conn, id2, id1)) {
                log.info("(flip seen/rejected)");
                return null;
            }

            log.info("Adding virgin flipped version as 8: " + 
                     id2 + " " + id1 + " " + (uppercase ? "/Upper" : ""));
            
            ApprovedPair ap = getApprovedPair(conn, id1, id2);
            if (ap == null) {
                log.error("Skipping reverse add since [??] no original approved pair: " + id1 + " " + id2);
                return null;
            }

            ap.id1 = id2;
            ap.id2 = id1;
            ap.status = 8;

            if (uppercase) {
                ap.curator = curator.toUpperCase();
            } else {
                ap.curator = curator;
            }

            String extramsg = insert(conn, true, ap);
            if (extramsg != null) {
                log.error("AOOGA! insert 8 failed, eating it: " + extramsg);
            }

            return null;  // after all that

        } finally {
            rs.close();
            ps.close();
        }
    }

    // curation so non-trim
    private final static String SQL_DELETE = 
        "DELETE FROM pr.approved_pair WHERE id1 = ? AND id2 = ?";

    // not sure.. renaming
    public static String deleteWHY(Connection conn, String curator, long browserID,
                                               String id1, String id2) 
            throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_GENERAL_CHECK));
            ps.setString(1, id1);
            ps.setString(2, id2);
            rs = ps.executeQuery();
            if (rs.next()) {
                String s = rs.getString(1);
                if (!s.equals(curator)  &&  !"bill".equals(curator)  &&
                    !"BILL".equals(curator)) {
                    log.warn("Skipping non-owner/non-bill delete: " + 
                             curator + " deleting " + s + " " + 
                             id1 + " " + id2);
                    return s;
                }
/*
                long l = rs.getLong(2);
                if (l != browserID  ||  Character.isUppercase(s.charAt(0))) {
                    log.warn("Replacing delete of bulk or non-browserID delete " +
                             "with offline: " + 
                             curator + " deleting " + s + " " + 
                             id1 + " " + id2 + 
                             " (" + browserID + ", " + l + ")");
                    update(conn, curator, 2, id1, id2);
                    return "marked offline";
                }
*/
               update(conn, curator, 2, id1, id2);
               return null;
            } else {
                log.info("Not found: " + id1 + " " + id2);
                return "not found";
            }
/*
            ps = conn.prepareStatement(SQL_DELETE);
            ps.setString(1, id1);
            ps.setString(2, id2);
            int r = ps.executeUpdate();
            if (r != 1) {
                log.info("No delete: " + id1 + " " + id2);
            }
            return null;
*/
        } finally {
            rs.close();
            ps.close();
        }
    }

    private static ApprovedPair pairFromRs(ResultSet rs) 
            throws SQLException {

        String id1 = rs.getString(5);
        String id2 = rs.getString(6);

        ApprovedPair ap = new ApprovedPair(id1, id2);

        ap.createTime = rs.getTimestamp(1);
        ap.browserID = rs.getLong(2);;
        ap.curator = rs.getString(3);
        ap.status = rs.getInt(4);
if (!ap.id1.equals(rs.getString(5))) {
log.error("ID1 " + id1 + " " + rs.getString(5));
}
if (!ap.id2.equals(rs.getString(6))) {
log.error("ID2 " + id2 + " " + rs.getString(6));
}
        //ap.id1 = rs.getString(5);
        //ap.id2 = rs.getString(6);
        ap.vertical = rs.getBoolean(7);
        //ap.hasKwd = rs.getBoolean(8);

        ap.r = rs.getInt(8);
        ap.g = rs.getInt(9);
        ap.b = rs.getInt(10);
        ap.ll = rs.getInt(11);
        ap.rgbRadius = rs.getInt(12);
        ap.labContrast = rs.getInt(13);
        ap.density = rs.getInt(14);
        ap.d0 = rs.getLong(15);
        ap.matchPeople = rs.getBoolean(16);

        return ap;
    }


    private static String addWheres(String query, 
                                    Integer status, 
                                    String orient, 
                                    Boolean curatorUpper)
            throws SQLException {

        boolean where = false;
        if (status != null) {
            query += " WHERE status = " + status;
            where = true;
        }
        if (orient != null) {
            if (!where) {
                query += " WHERE ";
                where = true;
            } else {
                query += " AND ";
            }
            if ("v".equals(orient)) {
                query += " vertical IS TRUE";
            } else if ("h".equals(orient)) {
                query += " vertical IS FALSE";
            } else {
                throw new SQLException("Bad orient: " + orient);
            }
        }
        if (curatorUpper != null) {
            if (!where) {
                query += " WHERE ";
                where = true;
            } else {
                query += " AND ";
            }
            if (curatorUpper) {
                query += " SUBSTRING(curator FROM 1 FOR 1)" +
                         " != LOWER(SUBSTRING(curator FROM 1 FOR 1))";
            } else {
                query += " SUBSTRING(curator FROM 1 FOR 1)" +
                         " = LOWER(SUBSTRING(curator FROM 1 FOR 1))";
            }
        }
        return query;
    }

    // readonly and needed for trim
    private static final String SQL_GET_ALL_APPROVED_PAIRS =
        "SELECT create_time, browser_id, curator, status, " +
        " id1, id2, vertical," + // has_kwd, +
        " r, g, b, ll, rgb_radius, lab_contrast, density," +
        " d0, match_people" +
        " FROM ##approved_pair";

    public static List<ApprovedPair> getAllApprovedPairs(Connection conn,
                           Integer status, String orient, Boolean curatorUpper,
                           boolean d0, Boolean first, Boolean forward,
                           Set<String> select) 
            throws SQLException {

        if (select != null  &&  select.size() == 0) {
            select = null;
        }

        String query = chooseDB(SQL_GET_ALL_APPROVED_PAIRS);

        query = addWheres(query, status, orient, curatorUpper);

        if (d0) {
            query += "  ORDER BY d0 DESC";
        } else if (first != null) {
            if (first) {
                if (forward) {
                    query += " ORDER BY id1, id2";
                } else {
                    query += " ORDER BY id1 DESC, id2 DESC";
                }
            } else {
                if (forward) {
                    query += " ORDER BY id2, id1";
                } else {
                    query += " ORDER BY id2 DESC, id1 DESC";
                }
            }
        } else {
            query += "  ORDER BY create_time ASC";
        }
        //log.info("QQ [" + query + "]");
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(query,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            rs = ps.executeQuery();

            List<ApprovedPair> ret = new ArrayList<>();

            int skipped = 0;
            while (rs.next()) {

                ApprovedPair ap = pairFromRs(rs);

                if (orient != null) {
                    if ("v".equals(orient) != ap.vertical) {
                        throw new SQLException("Got non-" + orient + " " + ap.id1 + "_" + ap.id2);
                    }
                }

                if (select != null  &&  !select.contains(ap.id1)  &&
                                        !select.contains(ap.id2)) {
                    skipped++;
                    continue;
                }
                ret.add(ap);

            }
            //log.info("Q [" + query + "]: " + ret.size() + " skipped " + skipped);
            
            return ret;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    // trim for now
    private static final String SQL_GET_ALL_APPROVED_PAIRS_BY_TIME =
        "SELECT create_time, curator, status, id1, id2, vertical," + // has_kwd, +
        " r, g, b, ll, rgb_radius, lab_contrast, density," +
        " d0, match_people" +
        " FROM ##approved_pair";

    public static List<ApprovedPair> getAllApprovedPairsByTime(Connection conn,
                           Integer status, String orient, Boolean curatorUpper,
                           boolean forward, Set<String> select) 
            throws SQLException {

        if (select != null  &&  select.size() == 0) {
            select = null;
        }

        String query = chooseDB(SQL_GET_ALL_APPROVED_PAIRS_BY_TIME);

        query = addWheres(query, status, orient, curatorUpper);

        if (forward) {
            query += " ORDER BY create_time ASC";
        } else {
            query += " ORDER BY create_time DESC";
        }
        //log.info("QQ [" + query + "]");
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(query,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            rs = ps.executeQuery();

            List<ApprovedPair> ret = new ArrayList<>();

            int skipped = 0;
            while (rs.next()) {

                ApprovedPair ap = truncPairFromRs(rs);

                if (select != null  &&  !select.contains(ap.id1)  &&
                                        !select.contains(ap.id2)) {
                    skipped++;
                    continue;
                }
                ret.add(ap);

            }
            //log.info("Q [" + query + "]: " + ret.size() + " skipped " + skipped);
            
            return ret;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    // curation/input?, maybe not used
    private static final String SQL_UPDATE_VERTICAL =
        "UPDATE pr.approved_pair SET vertical = ? WHERE id1 = ? AND id2 = ?";

    public static void updateHasVertical(Connection conn, ApprovedPair ap)
            throws SQLException {

        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_UPDATE_VERTICAL));
            ps.setBoolean(1, ap.vertical);
            ps.setString(2, ap.id1);
            ps.setString(3, ap.id2);
            int rows = ps.executeUpdate();
            if (rows < 1) {
                throw new SQLException("updateHasVertical update rows < 1: " + 
                                        rows);
            }
        } finally {
            closeSQL(ps);
        }
    }

/*
    // curation/input?, maybe not used
    private static final String SQL_UPDATE_HAS_KWD =
        "UPDATE pr.approved_pair SET has_kwd = ? WHERE id1 = ? AND id2 = ?";

    public static void updateHasKwd(Connection conn, ApprovedPair ap)
            throws SQLException {

        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_UPDATE_HAS_KWD));
            ps.setBoolean(1, ap.hasKwd);
            ps.setString(2, ap.id1);
            ps.setString(3, ap.id2);
            int rows = ps.executeUpdate();
            if (rows < 1) {
                throw new SQLException("updateHasKwd update rows < 1: " + rows);
            }
        } finally {
            closeSQL(ps);
        }
    }
*/

    // input/mtc, maybe not used
    private static final String SQL_UPDATE_COLOR_VALS =
        "UPDATE pr.approved_pair SET ll = ?, rgb_radius = ?," +
        "  lab_contrast = ?, density = ?," +
        "  r = ?, g = ?, b = ?" +
        " WHERE id1 = ? AND id2 = ?";

    public static void updateColorVals(Connection conn, ApprovedPair ap)
            throws SQLException {

        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_UPDATE_COLOR_VALS));
            ps.setInt(1, ap.ll);
            ps.setInt(2, ap.rgbRadius);
            ps.setInt(3, ap.labContrast);
            ps.setInt(4, ap.density);
            ps.setInt(5, ap.r);
            ps.setInt(6, ap.g);
            ps.setInt(7, ap.b);
            ps.setString(8, ap.id1);
            ps.setString(9, ap.id2);
            int rows = ps.executeUpdate();
            if (rows < 1) {
                throw new SQLException("updateColorVals update rows < 1: " + 
                                            rows);
            }
        } finally {
            closeSQL(ps);
        }
    }

    // setup: proj/update/
    private static final String SQL_UPDATE_D0_AND_PEOPLE =
        "UPDATE pr.approved_pair SET d0 = ?, match_people = ?" +
        " WHERE id1 = ? AND id2 = ?";

    public static void updateApprovedPairsD0(Connection conn) 
            throws SQLException {

        long t0 = System.currentTimeMillis();

        List<ApprovedPair> all = getAllApprovedPairs(conn, 
                           null, null, null,  // no WHERE
                           false, null, null, // no ORDER BY
                           null); // no view restriction

        if (all == null) {
            throw new SQLException("AP's null");
        }
        if (all.size() == 0) {
            throw new SQLException("No AP's");
        }

        log.info("Re-d0 " + all.size());

        int orient_mismatch = 0;
        int mismatch_updated = 0;

        int d00ct = 0;

        PreparedStatement ps = null;
        PreparedStatement ps_status = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_UPDATE_D0_AND_PEOPLE));
            ps_status = conn.prepareStatement(chooseDB(SQL_UPDATE));
            for (ApprovedPair ap : all) {
                try {
                    ap.d0 = PairDao.getVal(conn, ap.id1, ap.id2,
                                         (ap.vertical ? "v" : "h"), 
                                         "d0p", true /*default 0*/);
                    if (ap.d0 == 0) {
                        d00ct++;
                    }
                } catch (SQLException sqq) {
                    orient_mismatch++;
                    try {
                        ps_status.setInt(1, 5);
                        ps_status.setString(2, ap.id1);
                        ps_status.setString(3, ap.id2);
                        int r = ps_status.executeUpdate();
                        if (r == 1) {
                            mismatch_updated++;
                        } else {
                            log.error("updating status=5, r=" + r + ": " +
                                       ap.id1 + " " +  ap.id2);
                        }
                    } catch (SQLException sqx) {
                        log.error("Updating status=5, " +
                                       ap.id1 + " " +  ap.id2 + " - " + sqx);
                    }
                    continue;
                }

                Picture p1 = PictureDao.getPictureById(conn, ap.id1);
                Picture p2 = PictureDao.getPictureById(conn, ap.id2);
                if (p1 == null  ||  p2 == null) {
                    log.warn("Skipping: No pic(s): fix?: " + 
                             ap.id1 + " " + ap.id2);
                    continue;
                }
                ap.matchPeople = p1.people == p2.people;
                ps.setLong(1, ap.d0);
                ps.setBoolean(2, ap.matchPeople);
                ps.setString(3, ap.id1);
                ps.setString(4, ap.id2);
                int rows = ps.executeUpdate();
                if (rows < 1) {
                    throw new SQLException("updateD0: update rows < 1: " + 
                                            rows + ": " + 
                                            ap.id1 + " " + ap.id2);
                }
            }
        } finally {
            closeSQL(ps);
        }
        log.info("Done in " + (System.currentTimeMillis()-t0) + " millis " +
            "Orient mismatch/missing pics: " + orient_mismatch +
            " set to status=5: " + mismatch_updated +
            " d0=0: " + d00ct);
    }


    // trim seems right
    private static final String SQL_GET_APPROVED_PAIRS =
        "SELECT create_time, curator, status, id1, id2, vertical," + // has_kwd, + 
          "  r, g, b, ll, rgb_radius, lab_contrast, density," +
          "  d0, match_people" +
          " FROM ##approved_pair WHERE status = 1 AND" +
          "   ((id1 = 'ZZ') OR (id2 = 'ZZ')) QQ " +
          " ORDER BY create_time ASC";

    public static List<ApprovedPair> getApprovedPairsWithPic(Connection conn, 
                            String id, // Boolean hasKwd, 
                            Set<String> select) 
            throws SQLException {

        String query = chooseDB(SQL_GET_APPROVED_PAIRS)
                                .replaceAll("ZZ", id);
        query = query.replace("QQ", "");
/*
        if (hasKwd == null) {
            query = query.replace("QQ", "");
        } else if (hasKwd) {
            query = query.replace("QQ", "AND has_kwd IS TRUE");
        } else {
            query = query.replace("QQ", "AND has_kwd IS FALSE");
        }
*/

        if (select != null  &&  select.size() == 0) {
            select = null;
        }

        //log.info("QQ [" + query + "]");
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(query,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            rs = ps.executeQuery();

            List<ApprovedPair> ret = new ArrayList<>();

            int skipped = 0;
            while (rs.next()) {

                ApprovedPair ap = truncPairFromRs(rs);

                if (id.equals(ap.id1)) {
                    ap.otherId = ap.id2;
                    ap.otherFirst = false;
                } else {
                    ap.otherId = ap.id1;
                    ap.otherFirst = true;
                }
                if (select != null  &&  !select.contains(ap.otherId)) {
                    skipped++;
                    continue;
                }
                ret.add(ap);

            }
            //log.info("Q [" + query + "]: " + ret.size() + " skipped " + skipped);
            
            return ret;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    // TODO - just get id's
    // trim for now.. used?
    private static final String SQL_GET_APPROVED_PAIRS_2 =
        "SELECT create_time, curator, status, id1, id2, vertical," + // has_kwd, + 
          "  r, g, b, ll, rgb_radius, lab_contrast, density," +
          "  d0, match_people" +
          " FROM ##approved_pair WHERE status = 1 AND" +
          "   ((id1 in (ZZ)) OR (id2 in (ZZ))) QQ ";

    // trim for now.. used?
    private static final String SQL_GET_APPROVED_PAIRS_3 =
        "SELECT create_time, curator, status, id1, id2, vertical," + // has_kwd, + 
          "  r, g, b, ll, rgb_radius, lab_contrast, density," +
          "  d0, match_people" +
          " FROM ##approved_pair WHERE status = 1 AND" +
          "   ((id1 IN (ZZ)) OR (id2 IN (ZZ))) AND" +
          "   (id1 not in (AA)) AND (id2 NOT IN (AA))  QQ ";

    public static List<ApprovedPair> getSecondOrderPairs(Connection conn, 
                            List<String> ids, // Boolean hasKwd, 
                            Set<String> select) 
            throws SQLException {

        String id1 = ids.get(0);
        String id2 = ids.get(1);

        String idClause = "'" + id1 + "','" + id2 + "'";
        
        String query = chooseDB(SQL_GET_APPROVED_PAIRS_2)
                                .replaceAll("ZZ", idClause);
        query = query.replace("QQ", "");
/*
        if (hasKwd == null) {
            query = query.replace("QQ", "");
        } else if (hasKwd) {
            query = query.replace("QQ", "AND has_kwd IS TRUE");
        } else {
            query = query.replace("QQ", "AND has_kwd IS FALSE");
        }
*/
        if (select != null  &&  select.size() == 0) {
            select = null;
        }

        //log.info("QQ [" + query + "]");
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(query,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            rs = ps.executeQuery();

            StringBuilder oC = new StringBuilder();

            int ct = 0;
            int skipped = 0;
            while (rs.next()) {

                ApprovedPair ap = truncPairFromRs(rs);
                String otherid = null;
                if (ap.id1.equals(id1)  ||  ap.id1.equals(id2)) {
                    if (ap.id2.equals(id1)  ||  ap.id2.equals(id2)) {
                        skipped++;
                        continue;
                    }
                    otherid = ap.id2;
                } else {
                    otherid = ap.id1;
                }
                if (select != null  &&  !select.contains(otherid)) {
                    skipped++;
                    continue;
                }
                oC.append("'").append(otherid).append("',");
                ct++;
            }
            log.info("Got " + ct + " skipped " + skipped);

            closeSQL(rs);
            closeSQL(ps);

            List<ApprovedPair> ret = new ArrayList<>();

            if (ct == 0) {
                return ret;
            }

            oC.deleteCharAt(oC.length()-1);

            query = chooseDB(SQL_GET_APPROVED_PAIRS_3)
                             .replaceAll("ZZ", oC.toString());
            query = query.replaceAll("AA", idClause);

            query = query.replace("QQ", "");
            //if (hasKwd == null) {
            //} else if (hasKwd) {
            //query = query.replace("QQ", "AND has_kwd IS TRUE");
            //} else {
            //query = query.replace("QQ", "AND has_kwd IS FALSE");
            //}

            //query += " ORDER BY create_time ASC"; sorted by caller

            ps = conn.prepareStatement(query,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            rs = ps.executeQuery();

            skipped = 0;
            while (rs.next()) {

                ApprovedPair ap = truncPairFromRs(rs);

                if (select != null) {
                    if (!select.contains(ap.id1)  ||  
                        !select.contains(ap.id2)) {
                        skipped++;
                        continue;
                    }
                }
                ret.add(ap);
            }
            //log.info("Q3 [" + query + "]: " + ret.size() + " skipped " + skipped);
            
            return ret;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    // trim for now.. 
    private static final String SQL_GET_RANDOM_APPROVED_PAIR =
        "SELECT create_time, curator, status, id1, id2, vertical," + // has_kwd, + 
          " r, g, b, ll, rgb_radius, lab_contrast, density," +
        " d0, match_people" +
        " FROM ##approved_pair WHERE status = 1 AND vertical = ?" +
        "  OFFSET FLOOR(RANDOM() * (SELECT COUNT(*) FROM ##approved_pair " +
        "    WHERE status = 1 AND vertical = ?))" +
        " LIMIT 50";

    // TODO make tries depend on relative size of view, or better do in mem
    public static ApprovedPair getRandomApprovedPair(Connection conn, 
                                                     Set<String> select0,
                                                     String orient)
            throws SQLException {

        if (select0 != null  &&  select0.size() == 0) {
            select0 = null;
        }

        log.info("getRandomApprovedPair orient " + orient + " " +
                       (select0 != null ? "select set size " + select0.size()
                                        : " (open selection)"));

        //log.info("QQ [" + query + "]");
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_GET_RANDOM_APPROVED_PAIR),
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);

            if ("v".equals(orient)) {
                ps.setBoolean(1, true);
                ps.setBoolean(2, true);
            } else {
                ps.setBoolean(1, false);
                ps.setBoolean(2, false);
            }

            int skipped = 0;
            for (int i=0; i<10; i++) {
                rs = ps.executeQuery();

                while (rs.next()) {

                    ApprovedPair ap = truncPairFromRs(rs);

                    if (select0 != null) {
                        if (!select0.contains(ap.id1)) {
                            log.info("Select0: No " + ap.id1);
                            skipped++;
                            continue;
                        }
                        if (!select0.contains(ap.id2)) {
                            log.info("Select1: No " + ap.id2);
                            skipped++;
                            continue;
                        }
                    }

                    log.info("skipped " + skipped);
                    return ap;
                }
                rs.close();
            }
            log.info("got nothing, skipped " + skipped);
            
            return null;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    // trim for now.. used?
    static final String SQL_JOIN_LISTS =
        "SELECT create_time, curator, status, id1, id2, vertical," + // has_kwd, + 
          " r, g, b, ll, rgb_radius, lab_contrast, density," +
        " d0, match_people" +
        " FROM ##approved_pair WHERE status = 1 AND " +
        " id1 IN (AA) AND id2 IN (BB)";

    public static List<ApprovedPair> getApprovedPairsBySets(Connection conn,
                                            List<String> id1List, 
                                            List<String> id2List) 
            throws SQLException {

        if (id1List.size() == 0  ||  id2List.size() == 0) {
            log.error("List empty");
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String id : id1List) {
            sb.append("'").append(id).append("',");
        }
        sb.deleteCharAt(sb.length()-1);
        String aa = sb.toString();

        sb.setLength(0);
        for (String id : id2List) {
            sb.append("'").append(id).append("',");
        }
        sb.deleteCharAt(sb.length()-1);
        String bb = sb.toString();

        String query = chooseDB(SQL_JOIN_LISTS)
                                .replace("AA", aa).replace("BB", bb);

        List<ApprovedPair> ret = new ArrayList<>();

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(query,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            rs = ps.executeQuery();
            while (rs.next()) {
                ret.add(truncPairFromRs(rs));
            }
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
        log.info("getApprovedPairsBySets(" + id1List.size() + ", " + 
                  id2List.size() + ") -> " + ret.size());
        return ret;
    }

    public static void addFlips(Connection conn) throws SQLException {

        List<ApprovedPair> seen = UniquePairDao.getAllSeen(conn);

        log.info("addFlips: seen: " + seen.size());

        int skipped_orient = 0;
        int vert = 0;
        int horiz = 0;

        int d00ct = 0;

        int orig_is_1 = 0;
        int flipped_seen = 0;
        int no_pic = 0;
        int already_in_app = 0;

        for (ApprovedPair seenpair : seen) {

            // original pair
            ApprovedPair ap = getApprovedPair(conn, seenpair.id1, seenpair.id2); 
            if (ap != null  &&  ap.status == 1) {
                orig_is_1++;
                continue;
            }

            // reject, check flipped version 

            String id1 = seenpair.id2;
            String id2 = seenpair.id1;

            // flipped version seen
            if (UniquePairDao.seenPair(conn, id1, id2)) {
                flipped_seen++;
                continue;
            }

            // .. or already in use, e.g. from bulk kwd add or partial pass
            ap = getApprovedPair(conn, id1, id2); // flipped
            if (ap != null) {
                already_in_app++;
                continue;
            }

            Picture pic1, pic2;
            try {
                // could optimize here
                pic1 = PictureDao.getPictureById(conn, id1); 
                pic2 = PictureDao.getPictureById(conn, id2); 
                if (pic1 == null  ||  pic2 == null) {
                    skipped_orient++;
                    continue;
                }
            } catch (SQLException sqe) {
                no_pic++;
                continue;
            }

            //String[] pairIds = new String[2];
            //MiscUtil.ID.sortIds(pairIds, id1, id2);
            
/*
            boolean hasKwd = false;

            try {
                hasKwd = PairDao.getVal(conn, id1, id2,
                                          (pic1.vertical ? "v" : "h"),
                                          "kwd_val") > 0;
            } catch (SQLException sqe) {
                String msg = "Getting kwd val: " + sqe;
                log.error(msg);
                continue;
            }
*/
            ap = new ApprovedPair(id1, id2);

            ap.browserID = -1;
            ap.curator = "flip";
            ap.status = 6;

            ap.vertical = pic1.vertical;
            //ap.hasKwd = hasKwd;
            ap.d0 = PairDao.getVal(conn, ap.id1, ap.id2,
                                         (ap.vertical ? "v" : "h"),
                                         "d0p", true /*default 0*/);
            if (ap.d0 == 0) {
                d00ct++;
            }

            ap.matchPeople = pic1.people == pic2.people;

            ap.r = (pic1.r + pic2.r) / 2;
            ap.g = (pic1.g + pic2.g) / 2;
            ap.b = (pic1.b + pic2.b) / 2;
            ap.ll = (pic1.ll + pic2.ll) / 2;
            ap.rgbRadius = (pic1.rgbRadius + pic2.rgbRadius) / 2;
            ap.labContrast = (pic1.labContrast + pic2.labContrast) / 2;
            ap.density = (pic1.density + pic2.density) / 2;

            insert(conn, true, ap);
            if (ap.vertical) {
                vert++;
            } else {
                horiz++;
            }
        }
        log.info("addFlips: orig_is_1: " + orig_is_1 +
                            " flipped_seen: " + flipped_seen +
                            " no_pic: " + no_pic +
                            " already_in_app: " + already_in_app);
        log.info("addFlips: INSERTED " + vert + "/" + horiz + " v/h, " +
                               skipped_orient + " mismatches, " +
                               d00ct + " d0==0's");
    }

    public static void addFlips2(Connection conn) throws SQLException {

        List<ApprovedPair> negs = getAllApprovedPairs(conn, 2, null, null,
                           false, null, null, null);

        int skipped_orient = 0;
        int vert = 0;
        int horiz = 0;

        int flipped_seen = 0;
        int no_pic = 0;
        int already_in_app = 0;

        for (ApprovedPair ap : negs) {

            // flipped version seen
            if (UniquePairDao.seenPair(conn, ap.id2, ap.id1)) {
                flipped_seen++;
                continue;
            }

            // .. or already in use, e.g. from bulk kwd add
            if (any(conn, ap.id2, ap.id1)) {
                already_in_app++; 
                continue;
            }

            String id1 = ap.id2;
            String id2 = ap.id1;

            Picture pic1, pic2;
            try {
                // could optimize here
                pic1 = PictureDao.getPictureById(conn, id1);
                pic2 = PictureDao.getPictureById(conn, id2);
                if (pic1 == null  ||  pic2 == null) {
                    skipped_orient++;
                    continue;
                }
            } catch (SQLException sqe) {
                no_pic++;
                continue; 
            }

            //String[] pairIds = new String[];
            //MiscUtil.ID.sortIds(pairIds, id1, id2);

            ApprovedPair flip = new ApprovedPair(id1, id2);
            flip.status = 7;
            flip.browserID = -1;
            flip.curator = "flip2";

            // String both = id1 + " " + id2;

            // asymmetric
            flip.d0 = PairDao.getVal(conn, flip.id1, flip.id2,
                                         (flip.vertical ? "v" : "h"),
                                         "d0p", true /*default 0*/);
            // symmetric
            flip.vertical = ap.vertical;
            //flip.hasKwd = ap.hasKwd;
            flip.matchPeople = ap.matchPeople;

            flip.r = ap.r;
            flip.g = ap.g;
            flip.b = ap.b;
            flip.ll = ap.ll;
            flip.rgbRadius = ap.rgbRadius;
            flip.labContrast = ap.labContrast;
            flip.density = ap.density;

            insert(conn, true, flip);
            if (flip.vertical) {
                vert++;
            } else {
                horiz++;
            }
        }
        log.info("addFlips2: flipped_seen: " + flipped_seen +
                           " no_pic: " + no_pic +
                           " already_in_app: " + already_in_app);
        log.info("INSERTED addFlips2 " + vert + "/" + horiz + " v/h, " +
                               skipped_orient + " *mismatches*");
    }

    public static void addFlips1(Connection conn) throws SQLException {

        List<ApprovedPair> pos = getAllApprovedPairs(conn, 1, null, null,
                           false, null, null, null);

        int skipped_orient = 0;

        int tot_v = 0;
        int tot_h = 0;

        int vert = 0;
        int horiz = 0;

        int flipped_seen = 0;
        int already_in_app = 0;
        int no_pic = 0;

        for (ApprovedPair ap : pos) {

            if (ap.vertical) {
                tot_v++;
            } else {
                tot_h++;
            }

            // flipped version seen
            if (UniquePairDao.seenPair(conn, ap.id2, ap.id1)) {
                flipped_seen++;
                continue;
            }

            // .. or already in use, e.g. from bulk kwd add
            if (any(conn, ap.id2, ap.id1)) {
                already_in_app++;
                continue;
            }

            String id1 = ap.id2;
            String id2 = ap.id1;

            Picture pic1, pic2;
            try {
                // could optimize here
                pic1 = PictureDao.getPictureById(conn, id1);
                pic2 = PictureDao.getPictureById(conn, id2);
                if (pic1 == null  ||  pic2 == null) {
                    skipped_orient++;
                    continue;
                }
            } catch (SQLException sqe) {
                no_pic++;
                continue;
            }

            //String[] pairIds = new String[];
            //MiscUtil.ID.sortIds(pairIds, id1, id2);

            ApprovedPair flip = new ApprovedPair(id1, id2);
            flip.status = 8;
            flip.browserID = -1;
            flip.curator = "flip1";

            // String both = id1 + " " + id2;

            // asymmetric
            flip.d0 = PairDao.getVal(conn, flip.id1, flip.id2,
                                         (flip.vertical ? "v" : "h"),
                                         "d0p", true /*default 0*/);
            // symmetric
            flip.vertical = ap.vertical;
            //flip.hasKwd = ap.hasKwd;
            flip.matchPeople = ap.matchPeople;

            flip.r = ap.r;
            flip.g = ap.g;
            flip.b = ap.b;
            flip.ll = ap.ll;
            flip.rgbRadius = ap.rgbRadius;
            flip.labContrast = ap.labContrast;
            flip.density = ap.density;

            insert(conn, true, flip);
            if (flip.vertical) {
                vert++;
            } else {
                horiz++;
            }
        }
        log.info("addFlips1: flipped_seen: " + flipped_seen +
                           " no_pic: " + no_pic +
                           " already_in_app: " + already_in_app +
                           " mismatches!: " + skipped_orient);
        log.info("INSERTED addFlips1 " +
                 " vert: " + vert + "/" + tot_v + 
                 " (" + ( (100*vert)/tot_v) + "%) " + 
                 " horiz: " + horiz + "/" + tot_h + 
                 " (" + ( (100*horiz)/tot_h) + "%)");
    }

/*
    public static ApprovedPair getKwds(Connection conn, ApprovedPair ap)
                throws SQLException {

        ap.id1Kwds = KeywordsDao.getKwdSet(conn, ap.id1);
        if (ap.id1Kwds == null) {
            log.info("No kwds: " + ap.id1 + " v=" + ap.vertical);
            return null;
        }
        ap.id2Kwds = KeywordsDao.getKwdSet(conn, ap.id2);
        if (ap.id2Kwds == null) {
            log.info("No id2 kwds: " + ap.id2 + " v=" + ap.vertical);
            return null;
        }
        return ap;
    }
*/




}

