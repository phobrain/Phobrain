package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  UniquePairDao  - pr.pair_local - make sure pairs not seen twice.
 **/

import org.phobrain.db.record.ApprovedPair;
import org.phobrain.db.record.Picture;
import org.phobrain.db.record.ShowingPair;

import org.phobrain.util.ID;
import org.phobrain.util.HashCount;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.naming.InvalidNameException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UniquePairDao extends DaoBase {

    private static final Logger log = LoggerFactory.getLogger(
                                                 UniquePairDao.class);

    private final static String PAIR_FIELDS =
        " id1, id2 ";

    private final static String SQL_INSERT =
        "INSERT INTO pr.pair_local (curator, id1, id2) VALUES (?, ?, ?)";

    public static void insert(Connection conn, 
                              String curator, String id1, String id2)
            throws SQLException {

        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(SQL_INSERT);
            ps.setString(1, curator);
            ps.setString(2, id1);
            ps.setString(3, id2);
            int rows = ps.executeUpdate();
            if (rows != 1) {
                throw new SQLException("Insert returned " + rows);
            }

        } finally {
            closeSQL(ps);
        }
    }

    private static final String SQL_SEEN1 =
        "SELECT EXISTS(SELECT 1 FROM pr.pair_local " +
        " WHERE id1 = ? AND id2 = ?)";

    private static final String SQL_SEEN2 =
        "SELECT EXISTS(SELECT 1 FROM approved_pair " +
        " WHERE id1 = ? AND id2 = ?)";

    public static boolean seenPair(Connection conn, String id1, String id2)
            throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
           
            ps = conn.prepareStatement(SQL_SEEN2); // approved_pair tbl bigger

            ps.setString(1, id1);
            ps.setString(2, id2);

            rs = ps.executeQuery();

            if (!rs.next()) {
                throw new SQLException("No bool resultset/1");
            }
            if (rs.getBoolean(1)) {
                return true;
            }

            closeSQL(rs);
            closeSQL(ps);

            ps = conn.prepareStatement(SQL_SEEN1);

            ps.setString(1, id1);
            ps.setString(2, id2);

            rs = ps.executeQuery();

            if (!rs.next()) {
                throw new SQLException("No bool resultset/2");
            }
            return rs.getBoolean(1);

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_GET_PICS_SEEN =
        "SELECT XX FROM pr.pair_local WHERE YY = ?";
    // HACK
    private static final String SQL_GET_PICS_SEEN2 =
        "SELECT XX FROM approved_pair WHERE YY = ? AND (status=1 OR status=2)";

    public static Set<String> getPicsSeen(Connection conn, 
                           String id, String column)
            throws SQLException {

        String otherColumn;
        if ("id1".equals(column)) {
            otherColumn = "id2";
        } else if ("id2".equals(column)) {
            otherColumn = "id1";
        } else {
            throw new RuntimeException("Unknown column: " + column);
        }
        String query = SQL_GET_PICS_SEEN.replaceAll("XX", otherColumn)
                                        .replaceAll("YY", column);

        //log.info("QQ [" + query + "]");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(query,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, id);
            rs = ps.executeQuery();

            Set<String> ret = new HashSet<>();

            while (rs.next()) {
                ret.add(rs.getString(1));
            }
            //log.info("Q [" + query + "]: " + ret.size());

            closeSQL(rs);
            closeSQL(ps);
            query = SQL_GET_PICS_SEEN2.replaceAll("XX", otherColumn)
                                      .replaceAll("YY", column);
            ps = conn.prepareStatement(query,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, id);
            rs = ps.executeQuery();
            while (rs.next()) {
                ret.add(rs.getString(1));
            }
            
            return ret;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_GET_ALL_SEEN =
        "SELECT id1, id2, create_time FROM pr.pair_local ORDER BY create_time ASC";

    // TODO switch to Pair now ApprovedPair extends
    public static List<ApprovedPair> getAllSeen(Connection conn)
            throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(SQL_GET_ALL_SEEN,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            rs = ps.executeQuery();

            List<ApprovedPair> ret = new ArrayList<>();

            while (rs.next()) {
                ApprovedPair ap = new ApprovedPair(rs.getString(1), 
                                                   rs.getString(2));
                ap.createTime = rs.getTimestamp(3);
                ret.add(ap);
            }
            //log.info("Q [" + query + "]: " + ret.size());
            
            return ret;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_DELETE =
        "DELETE FROM pr.pair_local WHERE id1 = ? AND id2 = ?";

    public static void delete(Connection conn, String a[])
            throws SQLException {

        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(SQL_DELETE);
            ps.setString(1, a[0]);
            ps.setString(2, a[1]);

            int r = ps.executeUpdate();

            // ignore empty deletes, since they may be from redundant records
            if (r < 0) {
                throw new SQLException("No delete on " + a[0] + " " + a[1] +
                                       ": " + r);
            } else if (r > 1) {
                log.info(">1 delete on " + a[0] + " " + a[1] +
                                       ": " + r);
            }

        } finally {
            closeSQL(ps);
        }
    }


    private static Picture fnameTrans(Connection conn, int archive,
                                    String fname, Set<String> noPicForFname) 
            throws SQLException {

        try {
            ID newid = new ID(archive, fname);
            Picture p = PictureDao.getPictureById(conn, newid.id);
            if (p != null) {
                return p;
            }
            noPicForFname.add(fname);
            return null;
        } catch (InvalidNameException ine) {
            throw new SQLException("Bad arch/fname: " + archive + " " +
                                                        fname +
                                                        ": " + ine);
        }
    }

    private static String getSeq(String s) {

        int start = 0;
        while (start < s.length()  &&  !Character.isDigit(s.charAt(start))) {
            start++;
        }
        if (start == s.length()) {
            return null;
        }
        int end = start+1;
        while (end < s.length()  &&  Character.isDigit(s.charAt(end))) {
            end++;
        }
        return s.substring(start, end);
    }

    private final static boolean UPDATE = false;
    private static int upd = 0;

    private static Map<String, Picture> pMap = new HashMap<>();
    private static Set<String> pNoMap = new HashSet<>();
    private static Set<String> pSkipMap = new HashSet<>();
    private static Set<String> pExtraMap = new HashSet<>();

    private static Picture getPic(Connection conn, 
                                    Map<String, String[]> multiX,
                                    String id, boolean vertical)
            throws SQLException {

        String mapid = id + "_" + vertical;

        if (pSkipMap.contains(mapid)) {
            return null;
        }

        Picture p = pMap.get(mapid);
        
        if (p != null) {
            return p;
        }

        ID iid;
        try {
            iid = new ID(id);
        } catch (InvalidNameException ine) {
            throw new SQLException("Translating id: " + ine);
        }

        List<Picture> l = PictureDao.getPicturesByArchiveSequence(
                                                    conn, 
                                                    iid.arch, iid.seq);
        if (l == null  ||  l.size() == 0) {
            pNoMap.add(mapid);
            pSkipMap.add(mapid);
            return null;
        }
        p = l.get(0);
        if (p.vertical == vertical) {
            pMap.put(mapid, p);
            return p;
        }
        if (l.size() == 1) {
            pSkipMap.add(mapid);
            return null;
        }
        int extra = 0;
        for (int i=1; i<l.size(); i++) {
            Picture p1 = l.get(i);
            if (p1.vertical == vertical) {
                if (p == null) {
                    p = p1;
                } else {
                    extra++;
                }
            }
        }
        if (extra == 0  &&  p != null) {
            return p;
        }
        if (p == null) {
            log.error("None " + mapid);
            pSkipMap.add(mapid);
            return null;
        }

        p = null;

        // see if a hand-coded one

        String[] fnames = multiX.get(id); // match orient
        if (fnames != null) {
            //log.warn("No fnames on multi: " + id);
        //} else {
            for (String fname : fnames) {
                for (int i=1; i<l.size(); i++) {
                    p = l.get(i);
                    if (p.vertical != vertical) {
                        continue;
                    }
                    if (p.id.equals(iid.id)) {
                        return p;
                    }
                }
            }
        }

        // check the extras, just in case

        p = null;
        Picture tmp = null;
        for (int i=1; i<l.size(); i++) {
            tmp = l.get(i);
            if (tmp.vertical != vertical) {
                continue;
            }
            if (p == null) {
                p = tmp;
            } else {
                p = null;
                break;
            }
        }
        if (p != null) {
            return p;
        }

        //log.error("Multi: " + mapid);
        pSkipMap.add(mapid);
        pExtraMap.add(mapid);
        return null;
    }

}

