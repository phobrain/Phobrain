package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  PairtopDao  - pr.pairtop_[col|nn|vec]_[vh]
 **
 **     Inserts are done via table load
 **
 **     Symmetric pairtops have ids in canonical
 **     order, asymmetric ones (nn) have ids in 
 **     screen order.
 **/

import org.phobrain.db.record.Pair;

import org.phobrain.util.Lists;
import org.phobrain.util.ListHolder;
import org.phobrain.util.ID;

import javax.naming.InvalidNameException;

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

public class PairTopDao extends DaoBase {

    private static final Logger log = LoggerFactory.getLogger(
                                                 PairTopDao.class);

    private static Boolean have_col_v = null;
    private static Boolean have_col_h = null;
    private static Boolean have_nn_v = null;
    private static Boolean have_nn_h = null;

    public static synchronized void testTables(Connection conn) {

        have_col_v = haveTable(conn, "pairtop_col_v");
        have_col_h = haveTable(conn, "pairtop_col_h");

        have_nn_v = haveTable(conn, "pairtop_nn_v");
        have_nn_h = haveTable(conn, "pairtop_nn_h");

        log.info("CONFIG testTables: pairtop_col_{v/h}: " + have_col_v + "/" + have_col_h);
        log.info("CONFIG testTables: pairtop_nn_{v/h}: " + have_nn_v + "/" + have_nn_h);
    }

    public static Boolean hasTable(String colnn, String orient) {

        if ("col".equals(colnn)) {
            return "v".equals(orient) ? have_col_v : have_col_h;
        } else if ("nn".equals(colnn)) {
            return "v".equals(orient) ? have_nn_v : have_nn_h;
        } else {
            log.error("Expected 'col' or 'nn': " + colnn);
            System.exit(1);       
        }
        return null;
    }

    private static final String SQL_GET_TAGS_META =
        "SELECT tag, ct FROM XXX ORDER BY tag";
        // + "ORDER BY substring(tag, 1)::int ASC ";

    public static List<String> getPairtopNNTags(Connection conn, boolean vert)
            throws SQLException {

        if ( (vert && !have_nn_v)  ||  (!vert && !have_nn_h) ) {
            return null;
        }

        String tbl = "pairtop_nn_" + (vert ? "v" : "h") + "_tags";

        if (isTrim()) {
            tbl = "public.trim_" + tbl;
        } else {
            tbl = "pr." + tbl;
        }

        String query = SQL_GET_TAGS_META.replace("XXX", tbl);
        log.info("QQQQ " + query);

        ResultSet rs = null;
        try {
            List<String> ret = new ArrayList<>();

            boolean err = false;

            rs = conn.createStatement().executeQuery(query);
            while (rs.next()) {
                String tag = rs.getString(1);
                Integer ct = rs.getInt(2);
                if (ct == null) throw new SQLException("No ct for " + query);
                if (ct == 1) {
                    ret.add(tag);
                } else {
                    for (int i=0; i<ct; i++) {
                        ret.add(tag + "|" + (i+1));
                    }
                }
            }

            return ret;

        } catch (SQLException e) {
            log.info("Caught " + e + "\nOn query: [" + query + "]");
            throw e;
        } finally {
            closeSQL(rs);
        }
    }

    private static final String SQL_GET_PAIRTOP =
        "SELECT id1, id2, val" + 
          " FROM ##pairtop_CCC_00 WHERE tag='TT'" +
            " AND ((id1 = 'ZZ') OR (id2 = 'ZZ')) " +
          " ORDER BY val #";

    public static ListHolder getPairtopSymm(Connection conn, 
                            String type,  // 'col' or 'vec'
                            String orient, String id, String column,
                            boolean invert, String direction, int limit, 
                            Set<String> select) 
            throws SQLException {

        if ("col".equals(type)) {

            if ( ("v".equals(orient) &&  !have_col_v)  ||
                 ( !have_col_h ) ) {

                log.error("No symmetric pair table " + type + "_" + orient);
                return null;
            }

        } else {
            log.error("STILL USING VEC??");
            return null;
        }

        String query = chooseDB(SQL_GET_PAIRTOP)
                                .replaceAll("CCC", type)
                                .replaceAll("00", orient)
                                .replaceAll("TT", column)
                                .replaceAll("#", direction)
                                .replaceAll("ZZ", id);

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
            //log.info("Q [" + query + "]: " + lh.id2_l.size() + " skipped " + skipped);

            if (invert) {
                lh.value_l = Lists.invertDistribution(lh.value_l);
            }
            
            return lh;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    public static ListHolder[] getPairtopLR(Connection conn, 
                            String orient, 
                            List<String> ids, String columns[], // l->l', r->r'
                            boolean invert, String direction, int limit, 
                            Set<String> select) 
            throws SQLException {

        if ( ("v".equals(orient) &&  !have_col_v)  ||
                 ( !have_col_h ) ) {

            log.error("No symmetric pairtop table col_" + orient);
            return null;
        }

        if (select != null  &&  select.size() == 0) {
            select = null;
        }

        ListHolder[] ret = new ListHolder[2];

        for (int i=0; i<2; i++) {

            String query = chooseDB(SQL_GET_PAIRTOP)
                                .replaceAll("CCC", "vec")
                                .replaceAll("00", orient)
                                .replaceAll("#", direction)
                                .replaceAll("TT", columns[i])
                                .replaceAll("ZZ", ids.get(i));

            if (select == null  &&  limit != 0) {
                query += " LIMIT " + limit;
            }

            log.info("QQ [" + query + "]");

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
                    if (id2.equals(ids.get(i))) {
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
                //log.info("Q [" + query + "]: " + lh.id2_l.size() + " skipped " + skipped);

                if (invert) {
                    lh.value_l = Lists.invertDistribution(lh.value_l);
                }
            
                ret[i] = lh;
        
            } finally {
                closeSQL(rs);
                closeSQL(ps);
            }

        }

        return ret;
    }

    private static final String SQL_GET_PAIRTOP_ASYM =
        "SELECT id1, id2, val" + 
          " FROM ##pairtop_nn_00 WHERE tag=? AND XX=?" +
          " ORDER BY val ";

    public static ListHolder getPairtopAsym(Connection conn, 
                           String orient, String id, boolean first, 
                           String tag, // like column in pairs table
                           boolean invert, String direction, int limit,
                           Set<String> select) 
            throws SQLException {

        if ( ("v".equals(orient) &&  !have_nn_v)  ||
             ("h".equals(orient) &&  !have_nn_h ) ) {

            log.error("No table nn_" + orient);
            return null;
        }

        String query = chooseDB(SQL_GET_PAIRTOP_ASYM)
                                .replaceAll("00", orient)
                                .replaceAll("#", direction)
                                .replaceAll("XX", 
                                    (first ? "id1" : "id2"));

        query += direction;

        if (select != null  &&  select.size() == 0) {
            select = null;
        }
        if (select == null  &&  limit != 0) {
            query = query + " LIMIT " + limit;
        }

        log.info("QQ id, tag " + id + "," + tag + "\n[" + query + "]");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(query,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, tag);
            ps.setString(2, id);

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
                if (id.equals(id2)) {
                    skipped++;
                    continue;
                }
                lh.id2_l.add(id2);
                lh.value_l.add(value);
                if (lh.id2_l.size() == limit) {
                    break;
                }
            }
            //log.info("Q [" + query + "]: " + lh.id2_l.size() + " skipped " + skipped);
            log.info("result size: " + lh.id2_l.size() + " skipped " + skipped);

            if (invert) {
                lh.value_l = Lists.invertDistribution(lh.value_l);
            }
            
            return lh;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static String SQL_GET_TAGS_FOR_FNAME =
        "SELECT tag FROM pr.pairtop_nn_00_tags_file " +
            "WHERE fname LIKE '%XX%'";

    public static ListHolder randomPairtopAsymByFileStr(Connection conn, 
                            String orient, String id, boolean first, 
                            String file_strs[], // substr match vs. all .top
                            Set<String> select) 
            throws SQLException {

        if ( ("v".equals(orient) && !have_nn_v)  ||
             ("h".equals(orient) && !have_nn_h ) ) {

            log.error("No table nn_" + orient);
            return null;
        }

        Set<String> tags = new HashSet<>();

        for (String s : file_strs) {
        
            String query = chooseDB(SQL_GET_TAGS_FOR_FNAME)
                                .replaceAll("00", orient)
                                .replaceAll("XX", s);

            log.info("Q/getPairtopAsymFiles: " + query);

            // make list of per-pairtop tags with file name matching string

            PreparedStatement ps = null;
            ResultSet rs = null;
            try {
                ps = conn.prepareStatement(query,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
                rs = ps.executeQuery();

                while (rs.next()) {
                    String tag = rs.getString(1);
                    tags.add(tag);
                }
            } finally {
                closeSQL(rs);
                closeSQL(ps);
            }
        }
        log.info("Got " + tags.size() + " tags(->pairtops)");

        List<String> tagl = new ArrayList<>(tags);

        // pick one at random

        Collections.shuffle(tagl);

        for (String tag : tagl) {

            ListHolder lh = getPairtopAsym(conn, orient, id, first, tag,
                                                false, "ASC", 200,
                                                select);
        
            log.info("TAGTAGTAG " + tag + " Got " + lh.size());

            return lh;
        }
        log.error("Got zilch for " + id);
        return null;
    }
}

