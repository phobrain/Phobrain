package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  PairDao  - pr.pairs_[vh]
 **/

// leaving pairtop_col angles here with pairs angle

import org.phobrain.util.ListHolder;
import org.phobrain.util.ID;
import org.phobrain.util.MiscUtil.SeenIds;

import javax.naming.InvalidNameException;
import javax.naming.NamingException;

import org.phobrain.db.record.Pair;
import org.phobrain.db.record.ApprovedPair;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PairDao extends DaoBase {

    private static final Logger log = LoggerFactory.getLogger(
                                                 PairDao.class);
/*
inserts done via table build/load

    private static Pair pairFromRs(ResultSet rs) throws SQLException {
        Pair p = new Pair(rs.getString(1), rs.getString(2));
        p.values = new int[8];
        p.values[0] = rs.getInt(3);
        p.values[1] = rs.getInt(4);
        p.values[2] = rs.getInt(5);
        p.values[3] = rs.getInt(6);
        p.values[4] = rs.getInt(7);
        p.values[5] = rs.getInt(8);
        p.values[6] = rs.getInt(9);
        p.values[7] = rs.getInt(10);

        return p;
    }

*/

    /*
    private static final String SQL_UPDATE_FIELD =
        "UPDATE ##pairs_00 SET @ = ? WHERE id1 = ? AND id2 = ?";

    public static void updatePair(Connection conn, 
                                  String id1, String id2, 
                                  String orient, String column, int val)
            throws SQLException {

        String query = chooseDB(SQL_UPDATE_FIELD)
                                .replace("00", orient)
                                .replace("@", column);
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(query);
            ps.setInt(1, val);
            ps.setString(2, id1);
            ps.setString(3, id2);
            int ret = ps.executeUpdate();
            if (ret == 0) {
                log.info("Ignoring update on " + id1 + " " + id2);
            } else if (ret != 1) {
                throw new SQLException("Update returned " + ret);
            }
        } finally {
            closeSQL(ps);
        }
    }
*/

    private static Boolean have_v = null;
    private static Boolean have_h = null;
    private static int v_missing_msg = 0;
    private static int h_missing_msg = 0;
    private static Boolean have_v_d0 = null;
    private static Boolean have_h_d0 = null;

    public static synchronized void testTables(Connection conn) {

        have_v = haveTable(conn, "pairs_v");
        have_h = haveTable(conn, "pairs_h");

        if (!have_v) {
            have_v_d0 = false;
        } else {
            have_v_d0 = haveTableColumn(conn, "pairs_v", "a_d012");
        }
        if (!have_h) {
            have_h_d0 = false;
        } else {
            have_h_d0 = haveTableColumn(conn, "pairs_h", "a_d012");
        }

        log.info("CONFIG testTables: have_v/h: " + have_v + "/" + have_h +
                            " d0: " + have_v_d0 + "/" + have_h_d0);
    }

    public static Boolean hasTable(String orient) {

        return "v".equals(orient) ? have_v : have_h;

    }

    public static Boolean hasD0(String orient) {

        return "v".equals(orient) ? have_v_d0 : have_h_d0;
    }

    public static String tablesAvailable() {

        return "pairs_v/h: " + have_v + "/" + have_h;

    }

    /*
    **  tblChek: return false if no problem
    **              if problem: 
    **                      throw msg if asked
    **                      or log and return true
    */
    private static boolean tblChek(String caller, 
                                    boolean vertical, 
                                    boolean throwit) 
            throws SQLException {

        // don't want no trouble

        if (vertical  &&  have_v == Boolean.TRUE) return false;
        if (!vertical  &&  have_h == Boolean.TRUE) return false;

        // now you done it

        final String CONTRACT_MSG = "Table does not exist";
        String err = 
                caller + 
                ": " + CONTRACT_MSG + ": pr.pairs_" + 
                (vertical ? "v" : "h");

        if (throwit) {
            throw new SQLException(err);
        }

        // log.warn frequency

        if (vertical) {

            if (v_missing_msg < 50  ||
                v_missing_msg % 50 == 0) {

                log.warn(err);
                v_missing_msg++;
            }

        } else {

            if (h_missing_msg < 50  ||
                h_missing_msg % 50 == 0) {

                log.warn(err);
                h_missing_msg++;
            }
        }

        return true;
    }

    private static final String SQL_GET_COLS =
        "SELECT column_name FROM information_schema.columns" +
        " WHERE table_schema = 'pr'" +
        " AND table_name = 'XX'" +
        " ORDER BY column_name";

    /*
    **  Use xxx12 version and strip off 12 ending
    **      Expected to init have_v/have_h in ServletData
    **      init phase.
    */
    public static synchronized List<List<String>> getPairNNCols(Connection conn, 
                                                    boolean vertical)
            throws SQLException {

        if (tblChek("getPairNNCols", vertical, false)) {

            log.info("getPairNNCols: no pairs table pr.pairs_" +
                                    (vertical ? "v" : "h"));

            return null;
        }

        List<List<String>> ret = new ArrayList<>();
        List<String> n_xxx = new ArrayList<>();
        List<String> a_xxx = new ArrayList<>();

        ret.add(n_xxx);
        ret.add(a_xxx);

        String query = SQL_GET_COLS.replaceAll("XX", 
                                    vertical ? "pairs_v" : "pairs_h");

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(query);
            rs = ps.executeQuery();

            while (rs.next()) {
                String col = rs.getString(1);
                if (!col.endsWith("12")) {
                    continue;
                }
                col = col.substring(0, col.length()-2);

                if (col.startsWith("n_")) {
                    n_xxx.add(col);
                } else if (col.startsWith("a_")) {
                    a_xxx.add(col);
                } else {
                    log.info("Non NN: " + col);
                }
            }

        } catch (SQLException sqe) {
            log.info("Query: " + query, sqe);
            throw sqe;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
        log.info("SCHEMA query " + query + " sizes n/a: " +
                            n_xxx.size() + "/" +
                            a_xxx.size());

        return ret;
    }

    private static final String SQL_GET_VAL =
        "SELECT @ FROM ##pairs_00 WHERE id1 = ? AND id2 = ?";

    public static long getVal(Connection conn, String id1, String id2, 
                                              String orient, String col)
            throws SQLException {

        boolean vertical = "v".equals(orient);

        if (tblChek("getVal", vertical, false)) {
            return -999;
        }

//log.info("getVal " + orient + " have=h/v " + have_h + " " + have_v);
        return getVal(conn, id1, id2, orient, col, false);
    }

/*
kwds out for now
    private final static String SQL_HAS_KWD =
        "SELECT kwd FROM ##pairs_@@ WHERE id1=? AND id2=?";


    public static boolean hasKwd(Connection conn, String id1, String id2,
                                              String orient)
            throws SQLException {

        boolean vertical = "v".equals(orient);
        tblChek("hasKwd", vertical, true);

        String query = chooseDB(SQL_HAS_KWD).replaceAll("@@", orient);

        //log.info("QQ " + query + " -> " + orient);

        String[] sorted;
        try {
           sorted = ID.sortIds(id1, id2);
        } catch (InvalidNameException ine) {
            throw new SQLException("Bad id: " + ine);
        }
        //boolean flipped = id1.equals(sorted[0]);
        //log.info("Sorted: " + sorted[0] + " " + sorted[1]);

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(query);
            ps.setString(1, sorted[0]);
            ps.setString(2, sorted[1]);
            rs = ps.executeQuery();
            while (rs.next()) {
                return rs.getBoolean(1);
            }
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
        throw new SQLException("No kwd result: " + sorted[0] + " " + sorted[1]);
    }
*/

    public static long getVal(Connection conn, String id1, String id2, 
                                              String orient, String col,
                                              boolean def0)
            throws SQLException {

        boolean vertical = "v".equals(orient);
        if (tblChek("getVal", vertical, false)) {
            return -999;
        }

        String[] sorted;
        try {
           sorted = ID.sortIds(id1, id2);
        } catch (InvalidNameException ine) {
            throw new SQLException("Bad id: " + ine);
        }
        boolean flipped = id2.equals(sorted[0]);
        //log.info("Sorted: " + sorted[0] + " " + sorted[1]);

        if (flipped) {
            col += "21";
        } else {
            col += "12";
        }

        String query = chooseDB(SQL_GET_VAL).replaceAll("@", col)
                                  .replaceAll("00", orient);
        //log.info("Q " + query);

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(query);
            ps.setString(1, sorted[0]);
            ps.setString(2, sorted[1]);
            rs = ps.executeQuery();
            while (rs.next()) {
                return rs.getLong(1);
            }
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
        if (def0) {
            log.info("PairDao.getVal: " + id1 + "_" + id2 + " " + col +
                                          ": defaulting to 0");
            return 0;
        }
        throw new SQLException("No result for " + col + "/" + orient + ": " + 
                                id1 + " " + id2 + 
                                " query: (" + query + ") " +
                                " sorted: " + sorted[0] + " " + sorted[1]);
    }

    private static final String SQL_GET_LIST =
        "SELECT id1, id2, @ FROM ##pairs_00 WHERE ";

    public static ListHolder getCollectionVals(Connection conn, 
                                              String sort_id, 
                                              Collection<String> ids,
                                              String orient, String col,
                                              String ascdesc)
            throws SQLException {

        if (ids == null  ||  ids.size() == 0) {
            throw new SQLException("No ids");
        }

        boolean vertical = "v".equals(orient);
        tblChek("getCollectionVals", vertical, true);

        if ("d0".equals(col)) {
            log.info("TODO: default d0->a_d0");
            col = "a_d0";
        }

        String query = chooseDB(SQL_GET_LIST).replaceAll("@", col)
                                   .replaceAll("00", orient);

        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();

        try {
            for (String id : ids) {
                if (id.equals(sort_id)) {
                    //log.warn("sort id included " + sort_id);
                    continue;
                }
                String[] sorted = ID.sortIds(sort_id, id);
                if (sort_id.equals(sorted[0])) {
                    sb1.append("'").append(id).append("',");
                } else {
                    sb2.append("'").append(id).append("',");
                }
            }
        } catch (InvalidNameException ine) {
            throw new SQLException("Bad id: " + ine);
        }
        if (sb1.length() > 0) {
            sb1.deleteCharAt(sb1.length()-1);
        }
        if (sb2.length() > 0) {
            sb2.deleteCharAt(sb2.length()-1);
        }
        int phrases = 1; // count from 1
        if (sb1.length() > 0) {
            query += "(id1 = '" + sort_id + "' AND id2 in (" + 
                                               sb1.toString() + ")) ";
            phrases++;
            if (sb2.length() > 0) {
                query += " OR (id2 = '" + sort_id + "' AND id1 in (" + 
                                               sb2.toString() + ")) ";
                phrases++;
            }
        } else if (sb2.length() > 0) {
            query += " (id2 = '" + sort_id + "' AND id1 in (" + 
                                                sb2.toString() + ")) ";
            phrases++;
        } else {
            throw new SQLException("No ids to query, given " + 
                                    String.join("", ids));
        }
        query += " ORDER BY " + col + " " + ascdesc;

        //log.info("Q\n" + query);

        ListHolder lh = new ListHolder();

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(query);
            rs = ps.executeQuery();
            while (rs.next()) {
                String id1  = rs.getString(1);
                String id2  = rs.getString(2);
                Long val = rs.getLong(3);
                if (id1.equals(sort_id)) {
                    lh.id2_l.add(id2);
                } else {
                    lh.id2_l.add(id1);
                }
                lh.value_l.add(val);
            }
        } catch (SQLException sqe) {
            if (sqe.getMessage().contains("does not exist")) {
                if (vertical) have_v = false;
                else have_h = false;
            }
            throw sqe;
        }

        if ("ASC".equals(ascdesc)  &&  lh.value_l.size() > 1) {
            // flip values
            long topVal = lh.value_l.get(lh.value_l.size()-1);
            log.info("flip min " + lh.value_l.get(0) + " max " + topVal);
            topVal += 10;
            for (int i=0; i<lh.value_l.size(); i++) {
                long v = topVal - lh.value_l.get(i);
                lh.value_l.set(i, v);
            }
        }

        return lh;
    }

    private static void invertList(ListHolder lh) {

        if (lh.value_l.size() < 2) {
            return;
        }
        // flip values
        long start = lh.value_l.get(0);
        long end = lh.value_l.get(lh.value_l.size()-1);
        long min = start;
        long max = end;
        if (min > max) {
            min = end;
            max = start;
        }
        log.info("flip min " + min + " max " + max);
        long topVal = max + 10;
        for (int i=0; i<lh.value_l.size(); i++) {
            long v = topVal - lh.value_l.get(i);
            lh.value_l.set(i, v);
        }
    }

    private static final String SQL_GET_D0_A =
        "SELECT a_d012, a_d021 FROM ##pairs_00 WHERE id1 = ? OR id2 = ?";

    public static void setD0pSum(Connection conn, String orient, String id)
            throws SQLException {

        boolean vertical = "v".equals(orient);
        tblChek("setD0pSum", vertical, true);

        String query = chooseDB(SQL_GET_D0_A).replaceAll("00", orient);

        PreparedStatement ps = null;
        ResultSet rs = null;
        PreparedStatement updatePic = null;
        try {
            ps = conn.prepareStatement(query);
            ps.setString(1, id);
            ps.setString(2, id);
            rs = ps.executeQuery();
            double sum = 0.0;
            while (rs.next()) {
                sum += rs.getFloat(1);
                sum += rs.getFloat(2);
            }
            updatePic = conn.prepareStatement("UPDATE picture SET sum_d0 = ?" +
                                               " WHERE id = ?");
            updatePic.setFloat(1, (float)sum);
            updatePic.setString(2, id);
            updatePic.executeUpdate();

            log.info("Updated " + id + " " + sum);

        } finally {
            closeSQL(rs);
            closeSQL(ps);
            closeSQL(updatePic);
        }
    }


    private static final String SQL_GET_PAIRS_BY_D0_12 =
        "SELECT id1, id2, a_d012" + 
        " FROM ##pairs_00 " +
        " ORDER BY a_d012 # " +
        " LIMIT @";
    private static final String SQL_GET_PAIRS_BY_D0_21 =
        "SELECT id2, id1, a_d021" + 
        " FROM ##pairs_00 " +
        " ORDER BY a_d021 # " +
        " LIMIT @";

    public static List<Pair> getPairsByD0(Connection conn, String orient, boolean top, int limit, 
                            Set<String> exclude1, Set<String> exclude2)
            throws SQLException {

        boolean vertical = "v".equals(orient);
        tblChek("getPairsByD0", vertical, true);

        int half_limit = (int) ((double)limit * 0.6);
        log.info("getPairsByD0: half_limit: " + half_limit);

        String query12 = chooseDB(SQL_GET_PAIRS_BY_D0_12).replaceAll("00", orient)
                                                       .replaceAll("#", (top ? "DESC" : "ASC"))
                                                       .replaceAll("@", Integer.toString(half_limit));
        String query21 = chooseDB(SQL_GET_PAIRS_BY_D0_21).replaceAll("00", orient)
                                                       .replaceAll("#", (top ? "DESC" : "ASC"))
                                                       .replaceAll("@", Integer.toString(half_limit));

        List<Pair> ret = new ArrayList<>();
        int excl1 = 0;
        int excl2 = 0;

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(query12,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            rs = ps.executeQuery();
            while (rs.next()) {
                String id1 = rs.getString(1);
                String id2 = rs.getString(2);

                String key = id1 + "|" + id2;
                if (exclude1.contains(key)) {
                    excl1++;
                    continue;
                }
                if (exclude2.contains(key)) {
                    excl2++;
                    continue;
                }

                Pair p = new Pair(id1, id2);
                p.sortVal = rs.getInt(3);
                ret.add(p);
            }
            closeSQL(rs);
            closeSQL(ps);
            ps = conn.prepareStatement(query21,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            rs = ps.executeQuery();
            while (rs.next()) {
                String id1 = rs.getString(1);
                String id2 = rs.getString(2);

                String key = id1 + "|" + id2;
                if (exclude1.contains(key)) {
                    excl1++;
                    continue;
                }
                if (exclude2.contains(key)) {
                    excl2++;
                    continue;
                }

                Pair p = new Pair(id1, id2);
                p.sortVal = rs.getInt(3);
                ret.add(p);
            }
        } catch (SQLException sqe) {
            log.info("Queries: [" + query12 + "] [" + query21 + "] Exception: " + sqe);
            throw sqe;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
        log.info("-- TOT " + ret.size() + " limit " + limit + " excl1 " + excl1 + "/" + excl2);

        Collections.sort(ret);
        int trim = ret.size() - limit;
        if (trim > 0) {
            log.info("trim: " + trim);

            if (top) {
                int ix = 0;
                for (int i=0; i<trim; i++) {
                    ret.remove(ix++);
                }
            } else {
                int ix = ret.size() - 1;
                for (int i=0; i<trim; i++) {
                    ret.remove(ix--);
                }
            }
        }
        log.info("TOT: " + ret.size() +
                " range/values " + ret.get(0).sortVal + ".." + ret.get(ret.size()-1).sortVal);

        return ret;
    }

    private static final String SQL_GET_PAIRS =
        "SELECT id1, id2, @" + 
        " FROM ##pairs_00 WHERE ((id1 = 'ZZ') OR (id2 = 'ZZ')) " +
          " XX ORDER BY @ #";

    public static ListHolder getPairs(Connection conn, 
                           String id, String orient, String column,
                           boolean invert, String direction, int limit, 
                           Boolean kwdVal, Set<String> select) 
            throws SQLException {

        if (id == null  ||  orient == null  ||  direction == null) {
            throw new SQLException("Need non-null: id, orient, direction");
        }

        boolean vertical = "v".equals(orient);
        tblChek("getPairs", vertical, true);

        if (column.startsWith("d0")) {
            throw new SQLException("getPairs: use getPairsD0");
        }
        // kwds from NNs could be re-added, these were manually-made
        if (kwdVal != null) {
            log.warn("getPairs: Ignoring kwd option: " + kwdVal);
            kwdVal = null;
        }

        String query = chooseDB(SQL_GET_PAIRS).replaceAll("@", column)
                                    .replaceAll("00", orient)
                                    .replaceAll("#", direction)
                                    .replaceAll("ZZ", id);
        if (kwdVal == null) {
            query = query.replaceAll("XX", "");
        } else {
            query = query.replaceAll("XX", "AND kwd IS " + kwdVal);
            //throw new SQLException("Not doing kwd");
        }

        if (select != null  &&  select.size() == 0) {
            select = null;
        }
        if (select == null  &&  limit != 0) {
            query = query + " LIMIT " + limit;
        }

        //log.info("QQ [" + query + "]");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(query,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            rs = ps.executeQuery();

            ListHolder lh = new ListHolder();
            int skipped = 0;
            while (rs.next()) {
                String i1 = rs.getString(1);
                String i2 = rs.getString(2);
                long value = rs.getLong(3);

                String id2 = i1;
                if (id.equals(id2)) {
                    id2 = i2;
                }
                if (select != null  &&  !select.contains(id2)) {
                    skipped++;
                    continue;
                }
                lh.id2_l.add(id2);
                lh.value_l.add(value);
                if (lh.id2_l.size() == limit) {
                    break;
                }
            }
            if (invert) {
                invertList(lh);
            }

            //log.info("Q [" + query + "]: " + lh.id2_l.size() + " skipped " + skipped);
            
            return lh;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_GET_D0_NBRS =
        "SELECT id1, id2, a_d012, a_d021 FROM ##pairs_00 " +
            " WHERE ((a_d012 > ? AND a_d012 < ?) " +
                " OR (a_d021 > ? AND a_d021 < ?))";

    private static void getD0Neighbors2(Connection conn, String orient, 
                                            String curator,
                                            Set<String> select,
                                            SeenIds seenIds,
                                            int d0, int d0_lo, int d0_hi, int factor,
                                            List<Pair> upList, List<Pair> downList) 
            throws SQLException {

        String query = chooseDB(SQL_GET_D0_NBRS).replaceAll("00", orient);

        int row_count = 0;
        int skipped_ids = 0;

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {

            ps = conn.prepareStatement(query);

            ps.setInt(1, d0_lo);
            ps.setInt(2, d0_hi);
            ps.setInt(3, d0_lo);
            ps.setInt(4, d0_hi);

            rs = ps.executeQuery();

            while (rs.next()) {

                row_count++;

                String n_id1 = rs.getString(1);

                if (select != null  &&  !select.contains(n_id1)) {
                    skipped_ids++;
                    continue;
                }
                if (seenIds.contains(n_id1)) {
                    skipped_ids++;
                    continue;
                }

                String n_id2 = rs.getString(2);

                if (select != null  &&  !select.contains(n_id2)) {
                    skipped_ids++;
                    continue;
                }
                if (seenIds.contains(n_id2)) {
                    skipped_ids++;
                    continue;
                }

                int d0_12 =  rs.getInt(3);
                int d0_21 =  rs.getInt(4);

                if (d0_12 >= d0_lo  &&  d0_12 <= d0_hi) {
                    if (curator != null) {
                        ApprovedPair ap = ApprovalDao.getApprovedPair(conn, n_id1, n_id2);
                        if (ap != null  &&  ap.status == 1) {
                            continue;
                        }
                    }
                    Pair p = new Pair(n_id1, n_id2);
                    p.values = new int[]{ d0_12, d0_21 };
                    if (d0_12 <= d0) {
                        downList.add(p);
                    } else {
                        upList.add(p);
                    }
                }
                if (d0_21 >= d0_lo  &&  d0_21 <= d0_hi) {
                    if (curator != null) {
                        ApprovedPair ap = ApprovalDao.getApprovedPair(conn, n_id2, n_id1);
                        if (ap != null  &&  ap.status == 1) {
                            continue;
                        }
                    }
                    Pair p = new Pair(n_id2, n_id1); // swap
                    p.values = new int[]{ d0_21, d0_12 };
                    if (d0_21 <= d0) {
                        downList.add(p);
                    } else {
                        upList.add(p);
                    }
                } 
            }
            log.info("getD0Neighbors2 factor: " + factor + 
                        " d0/range: " + d0 + "/" + d0_lo + "-" + d0_hi +
                        " rows: " + row_count + 
                        " skipped_ids: " + skipped_ids + 
                        " pairs>d0: " + upList.size() +
                        " pairs<d0: " + downList.size() +
                        " seenIds: " + ("v".equals(orient) ?
                                            seenIds.vertical : 
                                            seenIds.horizontal) +
                        " idSetSize: " + select.size()
                    );
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    public static void getD0Neighbors(Connection conn, String orient, 
                            String curator,
                            Set<String> select,
                            SeenIds seenIds,
                            String id1, String id2,
                            int circle,
                            List<Pair> upList, List<Pair> downList) 
            throws SQLException {

        boolean vertical = "v".equals(orient);
        tblChek("getD0Neighbors", vertical, true);

        String[] sorted;
        try {
           sorted = ID.sortIds(id1, id2);
        } catch (InvalidNameException ine) {
            throw new SQLException("Bad id: " + ine);
        }

        boolean flipped = id2.equals(sorted[0]);

        String col = (flipped ? "a_d021" : "a_d012");

        String query = chooseDB(SQL_GET_VAL).replaceAll("@", col)
                                  .replaceAll("00", orient);
        //log.info("Q " + query);

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            // get d0 of current pair

            int d0 = -1;

            ps = conn.prepareStatement(query);
            ps.setString(1, sorted[0]);
            ps.setString(2, sorted[1]);
            rs = ps.executeQuery();
            if (rs.next()) {
                d0 = rs.getInt(1);
            }
            closeSQL(rs);
            closeSQL(ps);

            if (d0 == -1) {
                log.error("d0==-1: " + id1 + " " + id2 + "  " + orient);
                return;
            }

            log.info("d0 " + id1 + " " + id2 + "  " + d0);

            // calc range

            int factor = 2;
            if (select.size() < 10000) {
                factor *= 50;
                log.info("D0Neighbors: bumping factor to " + factor);
            }

            int d0_low = d0 - factor;
            int d0_high = d0 + factor;


            if (circle == 0) {

                // center

                getD0Neighbors2(conn, orient, curator,
                            select, seenIds,
                            d0, d0_low, d0_high, factor,
                            upList, downList);

            } else {  // ring > 0

                // adjust inner circle

                d0_low -= factor * (circle-1);
                d0_high += factor * (circle-1);

                int d0_lolow = d0_low - factor;
                int d0_hihigh = d0_high + factor;

                // bottom

                if (d0_low < 0  ||  d0_lolow < 0) {
                    // TODO - wrap?
                    log.warn("d0_low/d0_lolow < 0 " + d0_low + "/" + d0_lolow);
                } else {

                    getD0Neighbors2(conn, orient, curator,
                            select, seenIds,
                            d0, d0_lolow, d0_low, factor,
                            upList, downList);

                }

                // top TODO - check/wrap max

                getD0Neighbors2(conn, orient, curator,
                            select, seenIds,
                            d0, d0_high, d0_hihigh, factor,
                            upList, downList);

            }
/*
            log.info("getD0Neighbors factor: " + factor + 
                        " range: " + d0_low + "-" + d0_high +
                        " rows: " + row_count + 
                        " skipped_ids: " + skipped_ids + 
                        " pairs>d0: " + upList.size() +
                        " pairs<d0: " + downList.size() +
                        " seenIds: " + ("v".equals(orient) ?
                                            seenIds.vertical : 
                                            seenIds.horizontal) +
                        " idSetSize: " + select.size()
                    );
*/
            
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_GET_D0_BAD =
        "SELECT id1, id2, a_d012, a_d021 FROM ##pairs_ZZ " +
            " WHERE ((a_d012 < ?) " +
                " OR (a_d021 < ?)) LIMIT 500000";

    private static boolean notIn(List<Pair> list, String id1, String id2) {
        for (Pair p : list) {
            if (p.idsMatch(id1, id2)) {
                return false;
            }
        }
        return true;
    }

    final static int CUT_BAD = 200000;

    public static void getD0Bad(Connection conn, String orient, 
                            Set<String> select,
                            SeenIds seenIds,
                            List<Pair> list) 
            throws SQLException {

        boolean vertical = "v".equals(orient);
        tblChek("getD0Bad", vertical, true);

        int start = list.size();

        log.info("getD0Bad start count " + start);

        String query = chooseDB(SQL_GET_D0_BAD)
                                  .replaceAll("ZZ", orient);
        //log.info("NEW Q " + query);

        PreparedStatement ps = null;
        ResultSet rs = null;
        int skipped_ids = 0;
        int dupe_ids = 0;
        int skipped_cuts = 0;

        try {

            Set<String> ids = new HashSet<>();

            ps = conn.prepareStatement(query);

            ps.setInt(1, CUT_BAD);
            ps.setInt(2, CUT_BAD);

            rs = ps.executeQuery();

            int rows = 0;
            while (rs.next()) {
                rows++;

                //if (rows % 10000 == 0) { log.info("NEW+10000"); }

                String id1 = rs.getString(1);
                if (ids.contains(id1)) {
                    dupe_ids++;
                    continue;
                }
                if (seenIds.contains(id1)) {
                    skipped_ids++;
                    continue;
                }
                if (select != null  &&  !select.contains(id1)) {
                    skipped_ids++;
                    continue;
                }

                String id2 = rs.getString(2);
                if (ids.contains(id2)) {
                    dupe_ids++;
                    continue;
                }
                if (seenIds.contains(id2)) {
                    skipped_ids++;
                    continue;
                }
                if (select != null  &&  !select.contains(id2)) {
                    skipped_ids++;
                    continue;
                }

                int d0_12 =  rs.getInt(3);
                int d0_21 =  rs.getInt(4);

                if (d0_12 < CUT_BAD) {
                    if (notIn(list, id1, id2)) {
                        Pair p = new Pair(id1, id2);
                        p.sortVal = d0_12;
                        list.add(p);
                        ids.add(id1);
                        ids.add(id2);
                    }
                } else {
                    skipped_cuts++;
                }
                if (d0_21 < CUT_BAD) {
                    if (notIn(list, id2, id1)) {
                        Pair p = new Pair(id2, id1);
                        p.sortVal = d0_21;
                        list.add(p);
                        ids.add(id1);
                        ids.add(id2);
                    }
                } else {
                    skipped_cuts++;
                }

                if (list.size() > 50) {
                    break;
                }
            }

            log.info("getD0Bad " + start + "->" + list.size() + 
                        " skipped ids " + skipped_ids +
                        " dupes " + dupe_ids +
                        " cuts " + skipped_cuts);

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }
/*
    private static final String SQL_GET_PAIRS_D0_12 =
        "SELECT id1, id2, @" + 
        " FROM ##pairs_00 WHERE ((id1 = 'ZZ') OR (id2 = 'ZZ')) " +
          " XX ORDER BY @ #";
    private static final String SQL_GET_PAIRS_D0_21 =
        "SELECT id2, id1, @" + 
        " FROM ##pairs_00 WHERE ((id1 = 'ZZ') OR (id2 = 'ZZ')) " +
          " XX ORDER BY @ #";

    public static ListHolder getPairsD0(Connection conn, 
                           String id, boolean id_left,
                           String orient, String column,
                           Boolean kwdVal, String direction, 
                           int limit, Set<String> select) 
            throws SQLException {

        if (id == null  ||  orient == null  ||  direction == null) {
            throw new SQLException("Need non-null: id, orient, direction");
        }

        boolean vertical = "v".equals(orient);
        tblChek("getPairsD0", vertical, true);

        // kwds from NNs could be re-added, these were manually-made
        if (kwdVal != null) {
            log.warn("Ignoring kwd option: " + kwdVal);
            kwdVal = null;
        }

        String query12 = SQL_GET_TOP_PAIRS_D0_12 // id1, id2
                                    .replaceAll("@", col+"12")
                                    .replaceAll("ZZ", id)
                                    .replaceAll("00", orient)
                                    .replaceAll("#", direction);
        String query21 = SQL_GET_TOP_PAIRS_D0_21 // id2, id1
                                    .replaceAll("@", col+"21")
                                    .replaceAll("ZZ", id)
                                    .replaceAll("00", orient)
                                    .replaceAll("#", direction);

        if (kwdVal == null) {
            query12 = query12.replaceAll("XX", "");
        } else {
            query21 = query21.replaceAll("XX", "AND kwd IS " + kwdVal);
        }

        if (select != null  &&  select.size() == 0) {
            select = null;
        }
        if (select == null  &&  limit != 0) {
            // will be double requested, w/ 12,21
            query12 = query12 + " LIMIT " + limit; 
            query21 = query21 + " LIMIT " + limit; 
        }

//log.info("QQ [" + query1 + "\n  " + query2 "]");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            // 12

            ps = conn.prepareStatement(query12,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            rs = ps.executeQuery();

            ListHolder lh = new ListHolder();
            int skipped = 0;
            while (rs.next()) {
                String id1 = rs.getString(1);
                String id2 = rs.getString(2);
                long value = rs.getInt(3);

                String id2 = i1;
                if (id.equals(id2)) {
                    id2 = i2;
                }
                if (select != null  &&  !select.contains(id2)) {
                    skipped++;
                    continue;
                }
                lh.id2_l.add(id2);
                lh.value_l.add(value);
                if (lh.id2_l.size() == limit) {
                    break;
                }
            }
            if (invert) {
                invertList(lh);
            }
//log.info("Q [" + query + "]: " + lh.id2_l.size() + " skipped " + skipped);
            
            return lh;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }
*/


    private static final String SQL_GET_TOP_PAIRS =
        "SELECT id1, id2 FROM ##pairs_00 ORDER BY YY DESC " +
        " LIMIT ";

    public static List<String[]> getTopPairs(Connection conn, 
                                                String orient, int limit)
            throws SQLException {

        boolean vertical = "v".equals(orient);
        tblChek("getTopPairs", vertical, true);

        String query1 = chooseDB(SQL_GET_TOP_PAIRS)
                        .replaceAll("00", orient);

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {

            List<String[]> ret = new ArrayList<>();

            String q = query1.replaceAll("YY", "a_d012");
            q += limit;

            log.info("getTopPairs: q1: [" + q + "]");

            ps = conn.prepareStatement(q,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            rs = ps.executeQuery();
            while (rs.next()) {
                ret.add(new String[] { rs.getString(1), rs.getString(2) } );
                /*
                if ("h".equals(orient)  &&  ret.size() % 1000000 == 0) {
                    log.info("COUNT: " + ret.size());
                }
                */
            }
            closeSQL(rs);
            closeSQL(ps);

            q = query1.replaceAll("YY", "a_d021");
            q += limit;

            log.info("getTopPairs: q2: [" + q + "]");

            ps = conn.prepareStatement(q,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            rs = ps.executeQuery();
            while (rs.next()) {
                ret.add(new String[] { rs.getString(2), rs.getString(1) } );
                /*
                if ("h".equals(orient)  &&  
                        (ret.size() % 1000000 == 0  ||  ret.size() > 87000000)) {
                    log.info("COUNT: " + ret.size());
                }
                */
            }

            log.info("topPairs/" + orient + ": got " + ret.size());

            return ret;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }

    }

    private static final String SQL_GET_D0_BY_N_LEFT_1 =
        "SELECT id2, ppp12 FROM ##pairs_00 WHERE id1 = ? AND nnn12 < ? " +
        " ORDER BY ppp12 DESC";
    private static final String SQL_GET_D0_BY_N_LEFT_2 =
        "SELECT id1, ppp21 FROM ##pairs_00 WHERE id2 = ? AND nnn21 < ? " +
        " ORDER BY ppp21 DESC";

    private static final String SQL_GET_D0_BY_N_RIGHT_1 =
        "SELECT id1, ppp12 FROM ##pairs_00 WHERE id2 = ? AND nnn12 < ? " +
        " ORDER BY ppp12 DESC";
    private static final String SQL_GET_D0_BY_N_RIGHT_2 =
        "SELECT id2, ppp21 FROM ##pairs_00 WHERE id1 = ? AND nnn21 < ? " +
        " ORDER BY ppp21 DESC";

    public static ListHolder getTopPairsByNegCut(Connection conn, 
                                             String orient,
                                             String root_id, boolean left, 
                                             int d0nLimit, // 0..100% netreject
                                             int limit,
                                             String negCol, String posCol,
                                             Set<String> picSet)
            throws SQLException {

        boolean vertical = "v".equals(orient);
        tblChek("getTopPairsByNegCut", vertical, true);

        if (d0nLimit < 1) {
            d0nLimit = 1;
        }
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {

            String query1 = SQL_GET_D0_BY_N_LEFT_1;
            String query2 = SQL_GET_D0_BY_N_LEFT_2;
            if (!left) {
                query1 = SQL_GET_D0_BY_N_RIGHT_1;
                query2 = SQL_GET_D0_BY_N_RIGHT_2;
            }
            query1 = chooseDB(query1)
                .replaceAll("00", orient)
                .replaceAll("nnn", negCol)
                .replaceAll("ppp", posCol);
            query2 = chooseDB(query2)
                .replaceAll("00", orient)
                .replaceAll("nnn", negCol)
                .replaceAll("ppp", posCol);

            Map<Long, List<String>> map = new TreeMap<>();

            ps = conn.prepareStatement(query1,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, root_id);
            ps.setInt(2, d0nLimit);
            rs = ps.executeQuery();
            while (rs.next()) {
                String idx = rs.getString(1);
                if (picSet != null  &&  !picSet.contains(idx)) {
                    continue;
                }
                Long val = rs.getLong(2);
                List<String> l = map.get(val);
                if (l == null) {
                    l = new ArrayList<>();
                    map.put(val, l);
                }
                l.add(idx);
            }
            closeSQL(rs);
            closeSQL(ps);

            ps = conn.prepareStatement(query2,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, root_id);
            ps.setInt(2, d0nLimit);
            rs = ps.executeQuery();
            while (rs.next()) {
                String idx = rs.getString(1);
                if (picSet != null  &&  !picSet.contains(idx)) {
                    continue;
                }
                Long val = rs.getLong(2);
                List<String> l = map.get(val);
                if (l == null) {
                    l = new ArrayList<>();
                    map.put(val, l);
                }
                l.add(idx);
            }

            List<Map.Entry<Long, List<String>>> entries = new ArrayList<>();
            for (Map.Entry<Long, List<String>> entry : map.entrySet()) {
                entries.add(entry);
            }
            ListHolder ret = new ListHolder();
            for (int i=entries.size()-1; ret.id2_l.size()<limit && i>-1; i--) {

                Map.Entry<Long, List<String>>  entry = 
                    (Map.Entry<Long, List<String>>) entries.get(i);
                // log.info("e.key descends " + (Integer) entry.getKey());
                Long val = (Long) entry.getKey();
                List<String> l = (List<String>) entry.getValue();
                for (String id2 : l) {
                    ret.id2_l.add(id2);
                    ret.value_l.add(val);
                }
            }
            return ret;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    public static ListHolder getPosPairsParallel(Connection conn, 
                                            String orient, 
                                            String curator,
                                            String posColumn,
                                            List<String> root_ids, 
                                            boolean desc, // true=top pairs
                                            int limit,
                                            Set<String> picSet)
            throws SQLException {

        boolean vertical = "v".equals(orient);
        tblChek("getPosPairsParallel", vertical, true);

        int limit2 = (int)(1.8 * limit); // for aggregation
        if (limit < 1) {
            limit = 99999;
            limit2 = 99999;
        }
        if (limit > picSet.size()) {
            limit = picSet.size() / 3;
            log.info("Limit>set: reset to " + limit);
        }
        final int limit2f = limit2;

        log.info("getPosPairsParallel: limits " + limit + " -> " + limit2f);

        long t0 = System.currentTimeMillis();

        final ListHolder lh0 = new ListHolder();
        final ListHolder lh1 = new ListHolder();

        Thread th1 = new Thread(new Runnable() {
            public void run() {
                try {
                    int_getTopPosPairs(lh0, orient, curator, posColumn,
                                            root_ids.get(0), false,
                                            null, desc,
                                            limit2f, picSet);
                } catch (SQLException sqe) {
                    log.error("th1: " + sqe);
                }
            }
        });
        th1.start();

        String tcol = posColumn;

        if (posColumn.endsWith("12")) {
            tcol = posColumn.replace("12", "21");
        } else if (posColumn.endsWith("21")) {
            tcol = posColumn.replace("21", "12");
        }
        final String col = tcol;

        Thread th2 = new Thread(new Runnable() {
            public void run() {
                try {
                    int_getTopPosPairs(lh1, orient, curator, col,
                                            root_ids.get(1), true,
                                            null, desc,
                                            limit2f, picSet);
                } catch (SQLException sqe) {
                    log.error("th2: " + sqe);
                }
            }
        });
        th2.start();

        try {
            th1.join();
            th2.join();
        } catch (Exception e) {
            log.error("Waiting: " + e);
            throw new SQLException("Waiting for int query thread", e);
        }

        // idSeen-less copy from GetEngine interleaveLists
        ListHolder lh_max = lh1;
        int max = lh_max.id2_l.size();
        int min = lh0.id2_l.size();
        if (max < min) {
            lh_max = lh0;
            int t = min;
            min = max;
            max = t;
        }
        Set<String> ids = new HashSet<>();

        ListHolder lh = new ListHolder();
        // interleave
        for (int i=0; i<min && lh.id2_l.size()<limit; i++) {

            String id1 = lh0.id2_l.get(i);
            if (!ids.contains(id1)  &&
                picSet.contains(id1)) {

                ids.add(id1);

                lh.id2_l.add(id1);
                lh.value_l.add(lh0.value_l.get(i));
            }

            String id2 = lh1.id2_l.get(i);
            if (!ids.contains(id2)  &&
                    picSet.contains(id2)) {

                ids.add(id2);

                lh.id2_l.add(id2);
                lh.value_l.add(lh1.value_l.get(i));
            }
        }

        // overflow
        for (int i=min; i<max && i<limit; i++) {
            String id = lh_max.id2_l.get(i);
            if (!ids.contains(id)  &&
                    picSet.contains(id)) {

                ids.add(id); // just in case something gets added further on

                lh.id2_l.add(id);
                lh.value_l.add(lh_max.value_l.get(i));
            }
        }
        log.info("getPosPairsParallel haul " + lh.id2_l.size() + 
                                   " msec " + (System.currentTimeMillis()-t0));

        return lh;
    }

    public static ListHolder getPosPairs(Connection conn, 
                                            String orient, String curator,
                                            String posColumn, String root_id, 
                                            Boolean get_left, Boolean hasKwd,
                                            boolean desc, // true=top pairs
                                            int limit,
                                            Set<String> picSet)
            throws SQLException {

        boolean vertical = "v".equals(orient);
        tblChek("getPosPairs", vertical, true);

        int limit2 = (int)(1.8 * limit); // for aggregation
        if (limit < 1) {
            limit = 99999;
            limit2 = 99999;
        }
        if (limit > picSet.size()) {
            limit = picSet.size() / 3;
            log.info("Limit>set: reset to " + limit);
        }
        log.info("getPosPairs: get_left " + get_left +
                            " limits " + limit + " -> " + limit2);

        if (get_left != null) {
            // should be true for desc==false
            log.info("int_getTopPosPairs get_left=" + get_left +
                                       " limit " + limit);
            return int_getTopPosPairs(orient, curator, posColumn, root_id, 
                                        get_left, hasKwd, desc, limit, picSet);
        }
        // get_left is null

        ListHolder lh0 = int_getTopPosPairs(orient, curator, posColumn,
                                                     root_id, true,
                                                     hasKwd, desc,
                                                     limit2, picSet);
        String col;
        if (posColumn.endsWith("12")) {
            col = posColumn.replace("12", "21");
        } else if (posColumn.endsWith("21")) {
            col = posColumn.replace("21", "12");
        } else {
            col = posColumn;
        }
        ListHolder lh1 = int_getTopPosPairs(orient, curator, col,
                                                     root_id, false,
                                                     hasKwd, desc,
                                                     limit2, picSet);
        // idSeen-less copy from GetEngine interleaveLists
        ListHolder lh_max = lh1;
        int max = lh_max.id2_l.size();
        int min = lh0.id2_l.size();
        if (max < min) {
            lh_max = lh0;
            int t = min;
            min = max;
            max = t;
        }
        Set<String> ids = new HashSet<>();

        ListHolder lh = new ListHolder();
        // interleave
        for (int i=0; i<min && lh.id2_l.size()<limit; i++) {

            String id1 = lh0.id2_l.get(i);
            if (!ids.contains(id1)  &&
                picSet.contains(id1)) {

                ids.add(id1);

                lh.id2_l.add(id1);
                lh.value_l.add(lh0.value_l.get(i));
            }

            String id2 = lh1.id2_l.get(i);
            if (!ids.contains(id2)  &&
                    picSet.contains(id2)) {

                ids.add(id2);

                lh.id2_l.add(id2);
                lh.value_l.add(lh1.value_l.get(i));
            }
        }

        // overflow
        for (int i=min; i<max && i<limit; i++) {
            String id = lh_max.id2_l.get(i);
            if (!ids.contains(id)  &&
                    picSet.contains(id)) {

                ids.add(id); // just in case somehing gets added further on

                lh.id2_l.add(id);
                lh.value_l.add(lh_max.value_l.get(i));
            }
        }
        log.info("Merged Size " + lh.id2_l.size());

        return lh;
    }

    // ordering after mix: no discrim between AB,BA - let buyer beware
    // and check which they got if it's an issue; ignoring
    private static final String SQL_GET_POS_LEFT_1 =
        "SELECT id2, POS12 FROM ##pairs_00 WHERE id1 = ? " +
        " KKK ORDER BY POS12 DDD";
    private static final String SQL_GET_POS_LEFT_2 =
        "SELECT id1, POS21 FROM ##pairs_00 WHERE id2 = ? " +
        " KKK ORDER BY POS21 DDD";
    private static final String SQL_GET_POS_RIGHT_1 =
        "SELECT id1, POS12 FROM ##pairs_00 WHERE id2 = ? " +
        " KKK ORDER BY POS12 DDD";
    private static final String SQL_GET_POS_RIGHT_2 =
        "SELECT id2, POS21 FROM ##pairs_00 WHERE id1 = ? " +
        " KKK ORDER BY POS21 DDD";
/*
alternate reality / simplistic
    private static final String SQL_GET_D0_LEFT_1 =
        "SELECT id2, d0p12 FROM ##pairs_00 WHERE id1 = ? " +
        " KKK ORDER BY d0p12 DDD";
    private static final String SQL_GET_D0_LEFT_2 =
        "SELECT id1, d0p21 FROM ##pairs_00 WHERE id2 = ? " +
        " KKK ORDER BY d0p21 DDD";

    private static final String SQL_GET_D0_RIGHT_1 =
        "SELECT id1, d0p21 FROM ##pairs_00 WHERE id2 = ? " +
        " KKK ORDER BY d0p21 DDD"; 
    private static final String SQL_GET_D0_RIGHT_2 =
        "SELECT id2, d0p12 FROM ##pairs_00 WHERE id1 = ? " +
        " KKK ORDER BY d0p12 DDD";
*/

    private static ListHolder int_getTopPosPairs(
                                            String orient,
                                            String curator,
                                            String posColumn,
                                            String id, boolean get_left, 
                                            Boolean hasKwd,
                                            boolean desc, // true=top pairs
                                            int limit,
                                            Set<String> picSet)
            throws SQLException {

        // parallel versions need to feed static ret, this for legacy

        ListHolder ret = new ListHolder();

        return int_getTopPosPairs(ret, orient, curator, posColumn, id, get_left,
                                            hasKwd, desc, limit, picSet);
    }

    private static ListHolder int_getTopPosPairs(ListHolder ret,
                                                    String orient,
                                                    String curator,
                                                    String posColumn,
                                                    String id, 
                                                    boolean get_left, 
                                                    Boolean hasKwd,
                                                    boolean desc, // true=top pairs
                                                    int limit,
                                                    Set<String> picSet)
            throws SQLException {

        // kwds from NNs could be re-added, these were manually-made
        if (hasKwd != null) {
            log.warn("Ignoring kwd option: " + hasKwd);
            hasKwd = null;
        }
        boolean vertical = "v".equals(orient);

        String query1 = SQL_GET_POS_LEFT_1;
        String query2 = SQL_GET_POS_LEFT_2;

        if (!get_left) {
            query1 = SQL_GET_POS_RIGHT_1;
            query2 = SQL_GET_POS_RIGHT_2;
        }
        query1 = chooseDB(query1).replaceAll("POS", posColumn);
        query2 = chooseDB(query2).replaceAll("POS", posColumn);
            
        String direction = "DESC";
        if (!desc) {
            direction = "ASC";
        }

        query1 = query1.replaceAll("00", orient)
                       .replaceAll("DDD", direction);
        query2 = query2.replaceAll("00", orient)
                       .replaceAll("DDD", direction);
        if (hasKwd == null) {
            query1 = query1.replaceAll("KKK", "");
            query2 = query2.replaceAll("KKK", "");
        } else if (hasKwd) {
            query1 = query1.replaceAll("KKK", " AND kwd IS true");
            query2 = query2.replaceAll("KKK", " AND kwd IS true");
        } else {
            query1 = query1.replaceAll("KKK", " AND kwd IS false");
            query2 = query2.replaceAll("KKK", " AND kwd IS false");
        }
        int query_limit = 150; //2 * limit;
        query1 += " LIMIT " + query_limit;
        query2 += " LIMIT " + query_limit;

        log.info("Q1/2 \n" + query1 + "\n" + query2);

        long t0 = System.currentTimeMillis();

        final String query1f = query1;
        final String query2f = query2;

        final ListHolder lh1 = new ListHolder();
        final ListHolder lh2 = new ListHolder();

        Thread th1 = new Thread(new Runnable() {
            PreparedStatement ps = null;
            ResultSet rs = null;
            public void run() {
                Connection c2 = null;
                try {
                    c2 = getConn();
                    ps = c2.prepareStatement(query1f,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
                    ps.setString(1, id);
                    rs = ps.executeQuery();
                    while (rs.next()) {
                        String idx = rs.getString(1);
                        if (picSet != null  &&  !picSet.contains(idx)) {
                            continue;
                        }
                        lh1.id2_l.add(idx);
                        lh1.value_l.add(rs.getLong(2));
                    }
                } catch (Exception e) {
                    log.info("int_get th1 " + e);
                    return;
                } finally {
                    closeSQL(rs);
                    closeSQL(ps);
                    closeSQL(c2);
                }
                log.info("th1 done");
            }
        });
        th1.start();

        Thread th2 = new Thread(new Runnable() {
            PreparedStatement ps = null;
            ResultSet rs = null;
            public void run() {
                Connection c2 = null;
                try {
                    c2 = getConn();
                    ps = c2.prepareStatement(query2f,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
                    ps.setString(1, id);
                    rs = ps.executeQuery();
                    while (rs.next()) {
                        String idx = rs.getString(1);
                        if (picSet != null  &&  !picSet.contains(idx)) {
                            continue;
                        }
                        lh2.id2_l.add(idx);
                        lh2.value_l.add(rs.getLong(2));
                    }
                } catch (Exception e) {
                    log.info("int_get th2 " + e);
                    return;
                } finally {
                    closeSQL(rs);
                    closeSQL(ps);
                    closeSQL(c2);
                }
                log.info("th2 done");
            }
        });
        th2.start();

        try {
            th1.join();
            log.info("Joined th1");
            th2.join();
            log.info("Joined th2");
        } catch (Exception e) {
            throw new SQLException("int_get joins ", e);
        }
        log.info("int_ thread hauls: " + lh1.size() + " " + lh2.size() +
                    " in " + (System.currentTimeMillis()-t0));

        Map<Long, List<String>> map = new TreeMap<>();

        for (int i=0; i<lh1.size(); i++) {
            long val = lh1.value_l.get(i);
            List<String> l = map.get(val);
            if (l == null) {
                l = new ArrayList<>();
                map.put(val, l);
            }
            l.add(lh1.id2_l.get(i));
        }

        for (int i=0; i<lh2.size(); i++) {
            long val = lh2.value_l.get(i);
            List<String> l = map.get(val);
            if (l == null) {
                l = new ArrayList<>();
                map.put(val, l);
            }
            l.add(lh2.id2_l.get(i));
        }

        List<Map.Entry<Long, List<String>>> entries = new ArrayList<>();
        for (Map.Entry<Long, List<String>> entry : map.entrySet()) {
            entries.add(entry);
        }
        int start = 0;
        int end = entries.size();
        int inc = 1;
        if (desc) {
            start = entries.size()-1;
            end = -1;
            inc = -1;
        }
        Connection conn = null;
        if (curator != null) {
            try {
                conn = getConn();
            } catch (Exception e) {
                log.error("getConn failed, skipping curator check: " + e);
                conn = null;
            }
        }
        for (int i=start; i!=end; i+=inc) {

            Map.Entry<Long, List<String>>  entry = 
                 (Map.Entry<Long, List<String>>) entries.get(i);
                // log.info("e.key descends " + (Integer) entry.getKey());

            Long val = (Long) entry.getKey();
            List<String> l = (List<String>) entry.getValue();
            for (String id2 : l) {
                if (curator != null) {
                    ApprovedPair ap = (get_left ?
                                        ApprovalDao.getApprovedPair(conn, id, id2) :
                                        ApprovalDao.getApprovedPair(conn, id2, id));
                    if (ap != null  &&  ap.status == 1) {
                        continue;
                    }
                }
                ret.id2_l.add(id2);
                ret.value_l.add(val);
            }
            if (ret.size() > limit) {
                break;
            }
        }
        log.info("ret size " + ret.size());
                     //" v0 " + (ret.id2_l.size()==0 ? "skipped=" + skipped : 
                     //          "" + ret.value_l.get(0) + 
                     //          ".." + ret.value_l.get(ret.id2_l.size()-1)));

        if (curator != null) {
            closeSQL(conn);
        }

        return ret;
    }

    private static final String SQL_GET_BY_ANGLE =
        " SELECT id1, id2, a_d012, a_d021, angle FROM ##pairs_00 WHERE " +
        "  ((id1 = ? AND angle > ? AND angle < ?) OR" +
        "  (id2 = ? AND angle > ? AND angle < ?))";

    private static final int DELTA = 4;

    public static ListHolder getPairsAtAngle(Connection conn, 
                           String id, String orient, int angle, 
                           Set<String> select, int limit)
        throws SQLException {

        boolean vertical = "v".equals(orient);
        tblChek("getPairsAtAngle", vertical, true);

        // HACK - force range divide at boundary
        // here's what gets lost: SIZE 11324,11434,11430,11317,11401
        //                             / 11517

        if (angle - DELTA < -179) {
            log.info("HACK: forcing range at -179");
            angle = -179 + DELTA;
        } else if (angle + DELTA > 180) {
            log.info("HACK: forcing range at 180");
            angle = 180 - DELTA;
        }
        int t = -1 * angle;
        log.info("Range " + (angle-DELTA) + ".." + (angle+DELTA) + " / " +
                            (t-DELTA) + ".." + (t+DELTA));

        String query = chooseDB(SQL_GET_BY_ANGLE).replaceAll("00", orient);

        //log.info("Q " + query);

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(query,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, id);
            ps.setInt(2, angle - DELTA);
            ps.setInt(3, angle + DELTA);
            ps.setString(4, id);
            ps.setInt(5, t - DELTA);
            ps.setInt(6, t + DELTA);

            rs = ps.executeQuery();

            int skipped = 0;

            Map<Long, List<Object[]>> map = new TreeMap<>();

            while (rs.next()) {

                String i1 = rs.getString(1);
                String i2 = rs.getString(2);
                int d0p12 = rs.getInt(3);
                int d0p21 = rs.getInt(4);
                long value = rs.getLong(5);

                String id2 = i1;
                int d0p = d0p12;
                if (id.equals(id2)) {
                    id2 = i2;
                    d0p = d0p21;
                }
                if (select != null  &&  !select.contains(id2)) {
                    skipped++;
                    continue;
                }

                Object[] oo = { id2, value };

                List<Object[]> l = map.get(d0p);
                if (l == null) {
                    l = new ArrayList<>();
                    map.put((long)d0p, l);
                }
                l.add(oo);
            }
            List<Map.Entry> entries = new ArrayList<>();
            for (Map.Entry<Long, List<Object[]>> entry : map.entrySet()) {
                entries.add(entry);
            }

            ListHolder lh = new ListHolder();
            for (int i=entries.size()-1; i>-1; i--) {

                Map.Entry<Long, List<Object[]>>  entry = entries.get(i);
                // log.info("e.key descends " + (Integer) entry.getKey());

                List<Object[]> l = entry.getValue();
                for (Object[] oo : l) {
                    lh.id2_l.add((String)oo[0]);
                    lh.value_l.add((long) oo[1]); 
                    if (lh.id2_l.size() == limit) {
                        break;
                    }
                }
            }

            return lh;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    // GOLDEN ANGLE: 137.5077640500378546463487..

    private static final String SQL_GET_BY_GOLDEN_RGB =
        " SELECT id1, id2, ABS(rgb_angle - 137) AS delta FROM ##pairs_00 WHERE " +
        "  ((id1 = ? AND rgb_angle > 136 AND rgb_angle < 139) OR" +
        "  (id2 = ? AND rgb_angle > 136 AND rgb_angle < 139))" +
        " ORDER BY delta ASC";

    private static final String SQL_GET_BY_GOLDEN_ANGLE =
        " SELECT id1, id2, val FROM ##pairtop_col_00 WHERE " +
        "  tag='@' AND (id1 = ? OR id2 = ?)" +
        " ORDER BY val ASC";

    private static final String SQL_GET_BY_MY_ANGLE =
        " SELECT id1, id2, val FROM ##pairtop_col_00 WHERE " +
        "  tag = '@' AND (id1 = ? OR id2 = ?)" +
        " ORDER BY val ASC";

    public static ListHolder getGolden(Connection conn,
                           String id, String orient,
                           String angleColumn, Set<String> select)
        throws SQLException {

        boolean vertical = "v".equals(orient);
        tblChek("getGolden", vertical, true);

        return getAngle(conn, id, orient, angleColumn, select,
                              SQL_GET_BY_GOLDEN_ANGLE);
    }
    public static ListHolder getGoldenRGB(Connection conn,
                           String id, String orient, Set<String> select)
        throws SQLException {

        boolean vertical = "v".equals(orient);
        tblChek("getGoldenRGB", vertical, true);

        return getAngle(conn, id, orient, "n/a", select,
                              SQL_GET_BY_GOLDEN_RGB);
    }

    public static ListHolder getMyAngle(Connection conn,
                           String id, String orient,
                           String angleColumn, Set<String> select)
        throws SQLException {

        boolean vertical = "v".equals(orient);
        tblChek("getMyAngle", vertical, true);

        return getAngle(conn, id, orient, angleColumn, select, 
                              SQL_GET_BY_MY_ANGLE);
    }

    private static ListHolder getAngle(Connection conn,
                           String id, String orient,
                           String angleColumn, Set<String> select,
                           String query)
        throws SQLException {

        query = chooseDB(query)
                     .replaceAll("00", orient)
                     .replaceAll("@", angleColumn)
                     .replaceAll("XX", "");

/*
            if (kwdVal) {
                query = query.replaceAll("XX", "AND (kwd = TRUE)");
            } else {
                query = query.replaceAll("XX", "AND (kwd = FALSE)");
            }
*/
       
        //log.info("angle Q: " + query);

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(query,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, id);
            ps.setString(2, id);

            rs = ps.executeQuery();

            ListHolder lh = new ListHolder();
            int seen = 0;
            int skipped = 0;

            while (rs.next()) {

                seen++;

                String i1 = rs.getString(1);
                String i2 = rs.getString(2);
                long value = rs.getLong(3);

                String id2 = i1;
                if (id.equals(id2)) {
                    id2 = i2;
                }
                if (select != null  &&  !select.contains(id2)) {
                    skipped++;
                    continue;
                }
                lh.id2_l.add(id2);
                lh.value_l.add(value);
/*
                if (lh.id2_l.size() == limit) {
                    break;
                }
*/
            }

            //log.info("Seen: " + seen + " skipped: " + skipped);

            return lh;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

/*
    private static final String SQL_UPDATE_KWD =
        "UPDATE pairs SET kwd = ? WHERE id1 = ? AND id2 = ?";

    public static void updateKwd(Connection conn, String id1, String id2, 
                                 boolean val) 
            throws SQLException {
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(SQL_UPDATE_KWD);
            ps.setBoolean(1, val);
            ps.setString(2, id1);
            ps.setString(3, id2);
            int ret = ps.executeUpdate();
            if (ret == 0) {
                throw new SQLException("Error on update on " + id1 + " " + id2);
            } else if (ret != 1) {
                throw new SQLException("Update returned " + ret);
            }
        } finally {
            closeSQL(ps);
        }
    }
*/

    // data layout: pairs_x tables are complete pairs for the pics
    // pic1,pic2 in a row are ordered by arch:seqtag
    // each pic is naturally an id1 to pics 'greater' than it by that sort.

    // id1:
    //  a_d012 val for pic when on left
    //  a_d021 val for pic when on right
    private static String SQL_QUERY_D0_SUMS_ID1 =
        "SELECT a_d012,a_d021 FROM ##pairs_00" + 
        " WHERE id1 = ?";

    // id2:
    //  a_d012 val for pic when on right
    //  a_d021 val for pic when on left
    private static String SQL_QUERY_D0_SUMS_ID2 =
        "SELECT a_d021,a_d012 FROM ##pairs_00" +
        " WHERE id2 = ?";

    private static long HALF = 10000000 / 2;
    // TODO: query and round up/divide

    //private static List<String> unpopular_v = new ArrayList<>();
    //private static List<String> unpopular_h = new ArrayList<>();

/*
    // superseded by .pairs-based PairsBin

    public static double[] getD0SumsLR(Connection conn, 
                                        boolean vertical, 
                                        String id) 
            throws SQLException {

        String query_id1 = chooseDB(SQL_QUERY_D0_SUMS_ID1).replaceAll("00", 
                                                   vertical ? "v" : "h");
        String query_id2 = chooseDB(SQL_QUERY_D0_SUMS_ID2).replaceAll("00", 
                                                   vertical ? "v" : "h");

        //log.info("getD0SumsLR q's\n" + query_id1 + "\n" + query_id2);

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {

            int n = 0;
            int n_ok = 0;
            int n_nok = 0;

            long sum = 0;
            long sum_ok = 0;
            long sum_nok = 0;

            ps = conn.prepareStatement(query_id1);
            ps.setString(1, id);
            rs = ps.executeQuery();

            long sum_left_12 = 0;
            long sum_right_12 = 0;

            while (rs.next()) {
                n++;
                // id1:
                // a_d012 val for pic when on left
                // a_d021 val for pic when on right
                Long left_12 = rs.getLong(1);
                Long right_12 = rs.getLong(2);
                if (left_12 == null  ||  right_12 == null) {
                    throw new SQLException(
                                "id1: left_12 == null  ||  right_12 == null");
                }

                sum_left_12 += left_12;
                sum_right_12 += right_12;

                long sum12 = left_12 + right_12;
                sum += sum12;
                if (left_12 > HALF  ||  right_12 > HALF) {
                    n_ok++;
                    sum_ok += sum12;
                } else {
                    n_nok++;
                    sum_nok += sum12;
                }
            }

            closeSQL(rs);
            closeSQL(ps);

            ps = conn.prepareStatement(query_id2);
            ps.setString(1, id);
            rs = ps.executeQuery();

            long sum_left_21 = 0;
            long sum_right_21 = 0;

            while (rs.next()) {
                n++;
                // id2:
                // a_d012 val for pic when on right
                // a_d021 val for pic when on left
                Long right_21 = rs.getLong(1);
                Long left_21 = rs.getLong(2);
                if (left_21 == null  ||  right_21 == null) {
                    throw new SQLException(
                                "id2: left_21 == null  ||  right_21 == null");
                }

                sum_left_21 += left_21;
                sum_right_21 += right_21;

                long sum21 = left_21 + right_21;
                sum += sum21;

                if (left_21 > HALF  ||  right_21 > HALF) {
                    n_ok++;
                    sum_ok += sum21;
                } else {
                    n_nok++;
                    sum_nok += sum21;
                }
            }

            if (n == 0) {
                throw new SQLException("No id1/id2=" + id);
            }

            double[] d0a = new double[4];
            //String okness = "";
            d0a[0] = (sum_left_12 + sum_left_21) / n;    // avg when on left
            d0a[1] = (sum_right_12 + sum_right_21) / n;  // avg when on right
            if (n_ok == 0) {
                d0a[2] = -1.0;
                //okness += "none ok";
                //unpopular++;
            } else {
                d0a[2] = sum_ok / n_ok;                      // avg when good
            }

            if (n_nok == 0) {
                d0a[3] = -1.0;
                //okness += "all ok";
            } else {
                d0a[3] = sum_nok / n_nok;                    // avg when bad
            }

            //if (okness.length() > 0) {
            //    pout(id + ": " + okness + " total unpopular: " + unpopular);
            //}

            return d0a;

        } catch (SQLException sqe) {
            log.error("getD0SumsLR: " + sqe);
            throw sqe;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }
*/
}
