package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  PictureDao  - pr.picture table
 **/

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Random;
import java.util.stream.Stream;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Array;

import com.pgvector.PGvector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.InvalidNameException;

import org.phobrain.util.ConfigUtil;
import org.phobrain.util.Lists;
import org.phobrain.util.ListHolder;
import org.phobrain.util.MapUtil;
import org.phobrain.util.MathUtil;
import org.phobrain.util.MiscUtil;
import org.phobrain.util.MiscUtil.SeenIds;
import org.phobrain.util.ID;
import org.phobrain.util.FileRec;
import org.phobrain.util.PairsBin;

import org.phobrain.db.record.Picture;

public class PictureDao extends DaoBase {

    private static final Logger log = LoggerFactory.getLogger(PictureDao.class);

    private static boolean VEXX_CHEX = false;
    private final static int ENOUGH_OK_CHECKS = 10;

    // --

    private final static String RECORD_FIELDS =
        " xid, create_time, ";

    private final static String PICTURE_FIELDS =
        " id, archive, file_name, sequence, seq2, " +
        " variation_tag, variation_type, " + // unused
        " scene_sequence, scene_type, lighting,"+ // unused
        " angle, place, comments, r, g, b, " + // 16
        " rgb_radius, ll, aa, bb, lab_radius, " +
        " lab_contrast,  width, height, vertical, outdoors, " +
        " people, face, blur, sign, number, " +
        " block_display, block_reason, pref, " +
	    " density, sum_d0, sum_d0_l, sum_d0_r, avg_ok_d0, " +
        " avg_bad_d0, d_ctr_rgb, d_ctr_ab, d_ctr_8d, ang_ab, " +
        " d_ctr_27d, d_ctr_64d," + 
        " vgg16_4, dense_4, mob_5, nnl_7," +
        " vec_l, vec_r";

    private final static String INSERT_PICTURE_FIELDS =
        " id, archive, file_name, sequence, seq2, " +
        " variation_tag, variation_type, " + // unused
        " scene_sequence, scene_type, lighting,"+ // unused
        " angle, place, comments, r, g, b, " + // 16
        " rgb_radius, ll, aa, bb, lab_radius, " +
        " lab_contrast,  width, height, vertical, outdoors, " +
        " people, face, blur, sign, number, " +
        " block_display, block_reason, pref, " +
        " density, sum_d0, d_ctr_rgb, d_ctr_ab, " +
        " d_ctr_8d, ang_ab, d_ctr_27d, d_ctr_64d";

    private final static String SQL_INSERT_PICTURE =
        "INSERT INTO ##picture (" +
         INSERT_PICTURE_FIELDS +
        ") VALUES (" +
        " ?, ?, ?, ?, ?," +
        " ?, ?, ?, ?, ?," +
        " ?, ?, ?, ?, ?," +
        " ?, ?, ?, ?, ?," +
        " ?, ?, ?, ?, ?," +
        " ?, ?, ?, ?, ?," +
        " ?, ?, ?, ?, ?," +
        " ?, ?, ?, ?, ?," +
        //" ?, ?, ?, ?,"    +   // vgg16_4, dense_4, mob_5, nnl_7,
        " ?, ?)";             // vec_l,vec_r: by proj/update

    // WARNING - could cause dupes on parallel inserts of >1 picture per sequence

    private final static String SQL_INSERT_PICTURE_PROP =
        "INSERT INTO ##picture_prop " +
        "( id, p_name, p_value ) SELECT " +
        " ?, ?, ? " +
        "WHERE NOT EXISTS (SELECT FROM ##picture_prop " +
          "WHERE id = ? AND p_name = ? AND p_value = ?)";

    public static long insertPicture(Connection conn, Picture p)
                throws SQLException {

        //log.info("INSERT [" + p.id + "] [" + p.fileName + "] " + p.archive + " " + p.sequence);
        PreparedStatement ps = null;
        ResultSet rs = null;
        String stmt = chooseDB(SQL_INSERT_PICTURE);

        try {
            ps = conn.prepareStatement(stmt, Statement.RETURN_GENERATED_KEYS);
            //log.info("INSERT " + p.id + " " + p.archive + "/" + p.fileName);
            ps.setString(1,    p.id);
            ps.setInt(2,       p.archive);
            ps.setString(3,    p.fileName);
            ps.setInt(4,       p.sequence);
            ps.setInt(5,       p.seq2);
            ps.setString(6,    p.variationTag);
            ps.setString(7,    p.variationType);
            ps.setInt(8,       p.sceneSequence);
            ps.setString(9,    p.sceneType);
            ps.setString(10,   p.lighting); // day night flash..
            ps.setString(11,   p.angle); // up level down
            ps.setString(12,   p.place); // mishmash
            ps.setString(13,   p.comments);
            ps.setInt(14,      p.r);
            ps.setInt(15,      p.g);
            ps.setInt(16,      p.b);
            ps.setInt(17,      p.rgbRadius);
            ps.setInt(18,      p.ll);
            ps.setInt(19,      p.aa);
            ps.setInt(20,      p.bb);
            ps.setInt(21,      p.labRadius);
            ps.setInt(22,      p.labContrast);
            ps.setInt(23,      p.width);
            ps.setInt(24,      p.height);
            ps.setBoolean(25,  p.vertical);
            ps.setBoolean(26,  p.outdoors);
            ps.setBoolean(27,  p.people);
            ps.setBoolean(28,  p.face);
            ps.setBoolean(29,  p.blur);
            ps.setBoolean(30,  p.sign);
            ps.setBoolean(31,  p.number);
            ps.setBoolean(32,  p.blockDisplay);
            ps.setString(33,   p.blockReason);
            ps.setBoolean(34,  p.pref);
            ps.setInt(35,      p.density);
            ps.setLong(36,     p.d0Sum);
            ps.setInt(37,      p.dCtrRGB);
            ps.setInt(38,      p.dCtrAB);
            ps.setInt(39,      p.dCtr8D);
            ps.setInt(40,      p.angAB);
            ps.setInt(41,      p.dCtr27D);
            ps.setInt(42,      p.dCtr64D);

            // d0*, vec_l, vec_r set by proj/update/run/d0/vec
            // pgvectors same[?]

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Insert returned 0");
            }
            ResultSet generatedKeys = ps.getGeneratedKeys();
            if (generatedKeys.next()) {
                p.xid = generatedKeys.getLong(1);
            } else {
                throw new SQLException(
                              "Insert picture failed, no ID obtained.");
            }
            closeSQL(generatedKeys);

            if (p.props == null  ||  p.props.size() == 0) {
                return p.xid;
            }

            // sort thru props

            closeSQL(rs);
            closeSQL(ps);
            stmt = chooseDB(SQL_INSERT_PICTURE_PROP);
            ps = conn.prepareStatement(stmt);
            ps.setString(1, p.id);
            ps.setString(4, p.id);
            rows = 0;
            for (Map.Entry pair : p.props.entrySet()) {
                ps.setString(2, (String) pair.getKey());
                ps.setString(3, (String) pair.getValue());
                ps.setString(5, (String) pair.getKey());
                ps.setString(6, (String) pair.getValue());
                rows += ps.executeUpdate();
            }
            //log.info("Inserted " + rows + " props");

            return p.xid;

        } catch (SQLException sqe) {
            log.error("Stmt: " + stmt);
            throw sqe;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_GET_ALL_IN_ORDER =
        "SELECT DISTINCT archive, sequence, length(id) as idlen, id FROM ##picture " +
        " XX " +
        " ORDER BY archive, sequence, idlen, id";

    public static List<String> getIdsInOrder(Connection conn, Boolean vertical)
            throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;

        String query = chooseDB(
                            SQL_GET_ALL_IN_ORDER.replace("XX",
                                    (vertical == null ? "" :
                                     " WHERE vertical IS " +
                                            Boolean.toString(vertical))));

        log.info("Q: " + query);

        try {
            ps = conn.prepareStatement(query);
            rs = ps.executeQuery();

            List<String> pics = new ArrayList<>();
            while (rs.next()) {
                pics.add(rs.getString(4));
            }
            return pics;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    public static List<Picture> getPicturesInOrder(Connection conn, Boolean vertical)
            throws SQLException {

        List<String> ids = getIdsInOrder(conn, vertical);

        List<Picture> pl = new ArrayList<>();

        for (String id : ids) {
            pl.add(getPictureById(conn, id));
        }
        return pl;
    }

    private static final String SQL_UPDATE_D0_SUM =
        "UPDATE ##picture SET sum_d0 = ?, sum_d0_l = ?, sum_d0_r = ?," +
        " avg_ok_d0 = ?, avg_bad_d0 = ?" +
        " WHERE id = ?";

    private final static boolean LOG_PROGRESS = false;

    public static void updateD0Sum(Connection conn,
                                    boolean vertical,
                                    String pbFname,
                                    List<String> unpopular)
                throws SQLException {

        double d0p[] = new double[4];

        PreparedStatement ps = null;
        try {

            List<String> pics = getIdsInOrder(conn, vertical);
            log.info("Update sum_d0: " + ( vertical ? "v's: " : "h's: ") +
                                        pics.size());

            PairsBin pairsBin = PairsBin.load(pbFname, pics.size());

            if (pairsBin.getNPics() != pics.size()) {
                throw new SQLException("PictureDao.updateD0Sum: size mismatch: " +
                    pairsBin.getNPics() + ", " + pics.size());
            }

            ps = conn.prepareStatement(chooseDB(SQL_UPDATE_D0_SUM));

            String id1 = pics.get(0);
            String prev_arch = id1.substring(0, id1.indexOf("/"));
            if (LOG_PROGRESS) {
                pout("\n== archive " + prev_arch);
            }

            int ct = 0;
            int badct = 0;

            for (int ipic=0; ipic<pics.size(); ipic++) {

                String id = pics.get(ipic);

                //pout("-- " + id);
                //System.exit(1);
                // [l,r,avg_ok,avg_bad]

                if (LOG_PROGRESS) {
                    String arch = id.substring(0, id.indexOf("/"));
                    if (!arch.equals(prev_arch)) {
                        pout("\n== archive " + arch);
                        prev_arch = arch;
                        ct = 0;
                        badct = 0;
                    }
                }

                pairsBin.getD0SumsLR(ipic, id, d0p);
                if (d0p[2] == -1.0) {
                    unpopular.add(id);
                    badct++;
                }

                //log.info("XXX " + id + " " + d);
                ps.setLong(1, (long)(d0p[0]+d0p[1]));
                ps.setLong(2, (long)d0p[0]);
                ps.setLong(3, (long)d0p[1]);
                ps.setLong(4, (long)d0p[2]);
                ps.setLong(5, (long)d0p[3]);
                ps.setString(6, id);

                ps.executeUpdate();

                ct++;

                if (LOG_PROGRESS) {
                    if (ct % 20 == 0) {
                        if (badct > 2) System.out.print("*");
                        else if (badct > 1) System.out.print("'");
                        else if (badct > 0) System.out.print(".");
                        else System.out.print(" ");
                        badct = 0;
                        if (ct % 1200 == 0) {
                            System.out.println();
                        }
                    }
                }
            }

        } catch (SQLException sqe) {
            throw sqe;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeSQL(ps);
        }
    }


    private static final String SQL_UPDATE_RADII =
        "UPDATE ##picture SET " +
        " ang_ab = ?," +
        " d_ctr_ab = ?," +
        " d_ctr_rgb = ?," +
        " d_ctr_8d = ?," +
        " d_ctr_27d = ?," +
        " d_ctr_64d = ?" +
        " WHERE id = ?";

    public static void updateAngRadii(Connection conn, Picture p)
                throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_UPDATE_RADII));
            ps.setInt(1,       p.angAB);
            ps.setInt(2,       p.dCtrAB);
            ps.setInt(3,       p.dCtrRGB);
            ps.setInt(4,       p.dCtr8D);
            ps.setInt(5,       p.dCtr27D);
            ps.setInt(6,       p.dCtr64D);
            ps.setString(7,    p.id);

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Update returned 0");
            }
        } catch (SQLException sqe) {
            log.error("Update " + p.id +
                    ": angAB=" + p.angAB + ", dCtrAB="+ p.dCtrAB +
                    ": " + sqe);
        } finally {
            closeSQL(ps);
        }
    }


    private static final String SQL_GET_BOOL =
        "SELECT DISTINCT id FROM ##picture WHERE ";

    public static Set<String> getPicsBool(Connection conn,
                                            String column, boolean value)
            throws SQLException {

        final String query = SQL_GET_BOOL + column + (value ? "" : " is false");

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {

            ps = conn.prepareStatement(chooseDB(query));
            rs = ps.executeQuery();

            Set<String> picid = new HashSet<>();
            while (rs.next()) {
                picid.add(rs.getString(1));
            }
            return picid;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    public static List<String> getPicList(Connection conn,
                                          List<Integer> archives,
                                          Boolean vertical)
            throws SQLException {

        return getPicList(conn, archives, vertical, null);
    }

    public static List<String> getPicList(Connection conn,
                                          List<Integer> archives,
                                          Boolean vertical,
                                          Integer nD0Cut)
            throws SQLException {

        if (nD0Cut != null) {
            log.warn("nD0Cut " + nD0Cut);
        }

        int[] a = new int[archives.size()];
        for (int i=0; i<archives.size(); i++) {
            a[i] = archives.get(i);
        }

        List<Picture> l = getPicturesByArchives(conn, a, vertical, nD0Cut);

        Set<String> tmp = new HashSet<>();
        for (Picture p : l) {
            tmp.add(p.id);
        }
        log.info("picList size " + l.size() + " unique ids " + tmp.size());
        List<String> picid = new ArrayList<>();
        for (String id : tmp) {
            picid.add(id);
        }
        return picid;
    }

    private static final String SQL_GET_UNSEEN_PICID_LIST =
        "SELECT DISTINCT id FROM ##picture p " +
        " WHERE NOT EXISTS (SELECT 1 FROM showing " +
        "  WHERE archive = p.archive AND id == p.id) " +
        " ORDER BY archive ASC, id ASC";

    public static HashSet<String> getUnseenNoPerson(Connection conn)
            throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(chooseDB(SQL_GET_UNSEEN_PICID_LIST));
            rs = ps.executeQuery();

            HashSet<String> ids = new HashSet<>();
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
            return ids;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }


    private static Picture picFromRS(ResultSet rs) throws SQLException {

        Picture p = new Picture();

        p.xid = rs.getLong(1);
        p.createTime = rs.getTimestamp(2);
        p.id = rs.getString(3);
        p.archive = rs.getInt(4);
        p.fileName = rs.getString(5);

        p.sequence = rs.getInt(6);
        p.seq2 = rs.getInt(7);
        p.variationTag = rs.getString(8);
        p.variationType = rs.getString(9);
        p.sceneSequence = rs.getInt(10);

        p.sceneType = rs.getString(11);
        p.lighting = rs.getString(12);
        p.angle = rs.getString(13);
        p.place = rs.getString(14);
        p.comments = rs.getString(15);

        p.r = rs.getInt(16);
        p.g = rs.getInt(17);
        p.b = rs.getInt(18);
        p.rgbRadius = rs.getInt(19);
        p.ll = rs.getInt(20);

        p.aa = rs.getInt(21);
        p.bb = rs.getInt(22);
        p.labRadius = rs.getInt(23);
        p.labContrast = rs.getInt(24);
        p.width = rs.getInt(25);

        p.height = rs.getInt(26);
        p.vertical = rs.getBoolean(27);
        p.outdoors = rs.getBoolean(28);
        p.people = rs.getBoolean(29);
        p.face = rs.getBoolean(30);

        p.blur = rs.getBoolean(31);
        p.sign = rs.getBoolean(32);
        p.number = rs.getBoolean(33);
        p.blockDisplay = rs.getBoolean(34);
        p.blockReason = rs.getString(35);

        p.pref = rs.getBoolean(36);
        p.density = rs.getInt(37);
        p.d0Sum = rs.getLong(38);
        p.d0Sum_l = rs.getInt(39);
        p.d0Sum_r = rs.getInt(40);

        p.d0Sum_avg_ok = rs.getInt(41);
        p.d0Sum_avg_bad = rs.getInt(42);
        p.dCtrRGB = rs.getInt(43);
        p.dCtrAB = rs.getInt(44);
        p.dCtr8D = rs.getInt(45);

        p.angAB = rs.getInt(46);
        p.dCtr27D = rs.getInt(47);
        p.dCtr64D = rs.getInt(48);

        // short indexed pgvectors used for projection:
        //      vgg16_4, dense_4, mob_5, nnl_7,

        // vgg16_4
        PGvector vec = (PGvector) rs.getObject(49);
        if (vec == null) {
            log.warn("PictureDao.picFromRS(" + p.id + "): no vgg16_4 array");
        } else {
            p.vgg16_4 = vec.toArray();
        }

        // dense_4
        vec = (PGvector) rs.getObject(50);
        if (vec == null) {
            log.warn("PictureDao.picFromRS(" + p.id + "): no dense_4 array");
        } else {
            p.dense_4 = vec.toArray();
        }

        // mob_5
        vec = (PGvector) rs.getObject(51);
        if (vec == null) {
            log.warn("PictureDao.picFromRS(" + p.id + "): no mob_5 array");
        } else {
            p.mob_5 = vec.toArray();
        }

        // nnl_7
        vec = (PGvector) rs.getObject(52);
        if (vec == null) {
            log.warn("PictureDao.picFromRS(" + p.id + "): no nnl_7 array");
        } else {
            p.nnl_7 = vec.toArray();
        }

        Array a = rs.getArray(53);
        if (a == null) {
            // not used w/ pgvector log.warn("PictureDao.picFromRS(" + p.id + "): no l/r vec array");
        } else {
            Double[] d = (Double[]) a.getArray();
            p.vec_l = Stream.of(d).mapToDouble(Double::doubleValue).toArray();
            // rs.getArray(50)!!
            a = rs.getArray(54);
            if (a == null) {
                log.warn("PictureDao.picFromRS(" + p.id + "): no vec_r vec array");
            } else {
                d = (Double[]) a.getArray();
                p.vec_r = Stream.of(d).mapToDouble(Double::doubleValue).toArray();
            }
        }

        return p;
    }

    private static final String SQL_PICTURES_BY_ARCHIVE =
        "SELECT " +
         RECORD_FIELDS +
         PICTURE_FIELDS +
        " FROM ##picture " +
        "WHERE archive IN (@) %% ORDER BY archive ASC, id ASC";

    private static final String SQL_PICTURES_BY_ARCHIVE_ORDER_D0 =
        "SELECT " +
         RECORD_FIELDS +
         PICTURE_FIELDS +
        " FROM ##picture " +
        "WHERE archive IN (@) %% " +
        "ORDER BY sum_d0 __, archive ASC, id ASC";

    public static List<Picture> getPicturesByArchives(Connection conn,
                          int[] archives, Boolean vertical)
                          throws SQLException {
        return getPicturesByArchives(conn, archives, vertical, null);
    }

    public static List<Picture> getPicturesByArchives(Connection conn,
                          int[] archives, Boolean vertical, Integer nD0Cut)
                          throws SQLException {

        log.info("getPicturesByArchives vertical " + vertical +
                        " archives=" + Arrays.toString(archives) +
                        " nD0Cut " + nD0Cut);

        StringBuilder sb = new StringBuilder();
        //if (archives.length == 1  &&  archives[0] == 0) {
        //}
        for (int i : archives) {  // we are trusting souls
            sb.append("'").append(i).append("',");
        }
        sb.deleteCharAt(sb.length()-1);
        String query;
        if (nD0Cut == null) {
            query = chooseDB(SQL_PICTURES_BY_ARCHIVE)
                             .replace("@", sb.toString());
        } else if (nD0Cut > 0) {
            query = chooseDB(SQL_PICTURES_BY_ARCHIVE_ORDER_D0)
                             .replace("__", "DESC")
                             .replace("@", sb.toString())
                    + " LIMIT " + nD0Cut;
        } else {
            query = chooseDB(SQL_PICTURES_BY_ARCHIVE_ORDER_D0)
                             .replace("__", "ASC")
                             .replace("@", sb.toString())
                    + " LIMIT " + -1 * nD0Cut;
        }
        if (vertical == null) {
            query = query.replace("%%", "");
        } else if (vertical) {
            query = query.replace("%%", "AND vertical");
        } else {
            query = query.replace("%%", "AND NOT vertical");
        }

        log.info("getPicturesByArchives Q " + query);
        // startup-> nohup.out
        //System.err.println("getPicturesByArchives Q " + query);

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
PGvector.addVectorType(conn);
            ps = conn.prepareStatement(query);

            rs = ps.executeQuery();

            List<Picture> l = new ArrayList<>();
            while (rs.next()) {
                l.add(picFromRS(rs));
            }
            log.info("raw size: " + l.size());
            return l;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_PICTURE_BY_ID =
        "SELECT " +
         RECORD_FIELDS +
         PICTURE_FIELDS +
        " FROM ##picture " +
        "WHERE id = ?";

    public static Picture getPictureById(Connection conn, String id)
                          throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_PICTURE_BY_ID));
            ps.setString(1, id);
            rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            Picture p = picFromRS(rs);
            getProps(conn, p);
            return p;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }


    private static final String SQL_PICTURES_BY_ARCHIVE_SEQUENCE =
        "SELECT " +
         RECORD_FIELDS +
         PICTURE_FIELDS +
        " FROM ##picture " +
        "WHERE archive = ? AND sequence = ? ORDER BY sequence ASC";

    public static List<Picture> getPicturesByArchiveSequence(Connection conn,
                          int archive, int sequence)
                          throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(
                      chooseDB(SQL_PICTURES_BY_ARCHIVE_SEQUENCE));
            ps.setInt(1, archive);
            ps.setInt(2, sequence);
            rs = ps.executeQuery();

            List<Picture> pics = new ArrayList<>();

            while (rs.next()) {
                Picture p = picFromRS(rs);
                getProps(conn, p);
                pics.add(p);
            }
            return pics;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_PICTURE_PROPS_BY_ID =
        "SELECT p_name, p_value FROM ##picture_prop WHERE id = ?";

    private static void getProps(Connection conn, Picture p)
                          throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {

            HashMap<String, String> map = new HashMap<>();

            ps = conn.prepareStatement(
                      chooseDB(SQL_PICTURE_PROPS_BY_ID));
            ps.setString(1, p.id);
            rs = ps.executeQuery();
            while (rs.next()) {
                map.put(rs.getString(1), rs.getString(2));
            }
            p.props = map;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }


    // TODO - screen out seen ones
    private static final String SQL_ARCHSEQ_BY_ARCHIVE_SCENE =
        "SELECT id FROM ##picture WHERE archive = ?" +
        " AND scene_sequence = ?" +
        " AND scene_type = 'scene' ORDER BY id ASC";

    public static List<String> getArchSeqForArchiveScene(Connection conn,
                                             int archive, int scene_seq)
                          throws SQLException {

        if (scene_seq == -1) {
            return null;
        }

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_ARCHSEQ_BY_ARCHIVE_SCENE));
            ps.setInt(1, archive);
            ps.setInt(2, scene_seq);
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

    private static final String SQL_PICTURE_BY_XID =
        "SELECT " +
         RECORD_FIELDS +
         PICTURE_FIELDS +
        " FROM ##picture " +
        "WHERE id = ?";

    public static Picture getPictureByXId(Connection conn, long picID)
                          throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_PICTURE_BY_XID));
            ps.setLong(1, picID);
            rs = ps.executeQuery();

            while (rs.next()) {
                return picFromRS(rs);
            }
            log.warn("getPictureByXId: no result: " + picID);
            return null;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_GET_RGB_LIST_BASE =
        "SELECT DISTINCT id, @ FROM ##picture ";

    public static ListHolder getIntList(Connection conn,
                                           String type,
                                           List<String> id_l,
                                           boolean invert)
                          throws SQLException {

        if (id_l == null  ||  id_l.size() == 0) {
            throw new RuntimeException("Need id_l");
        }

        // group queries by archive

        Map<Integer, StringBuilder> archiveQueries = new HashMap<>();
        try {
            for (String id : id_l) {
                ID iid = new ID(id);
                StringBuilder sb = archiveQueries.get(iid.arch);
                if (sb == null) {
                    sb = new StringBuilder();
                    archiveQueries.put(iid.arch, sb);
                    sb.append(chooseDB(SQL_GET_RGB_LIST_BASE).replace("@", type));
                    sb.append("WHERE ARCHIVE = " + iid.arch + " AND id IN (");
                }
                sb.append("'").append(id).append("',");
            }
        } catch (InvalidNameException ine) {
            throw new SQLException("Bad id: " + ine);
        }

        Map<String, Long> map = new HashMap<>();
        PreparedStatement ps = null;
        ResultSet rs = null;
        String query = null;
        try {
            for (Map.Entry pair : archiveQueries.entrySet()) {
                int archive = (Integer) pair.getKey();
                StringBuilder sb = (StringBuilder) pair.getValue();
                // last comma becomes closing parenthesis
                sb.replace(sb.length()-1, sb.length(), ")");
                query = sb.toString();
                //log.info("Q " + query);
                ps = conn.prepareStatement(query);
                rs = ps.executeQuery();

                while (rs.next()) {
                    String id = rs.getString(1);
                    long val = rs.getLong(2);
                    map.put(id, val);
                }
                closeSQL(rs);
                closeSQL(ps);
            }
        } catch (SQLException sqe) {
            log.error("Q: " + query);
            throw sqe;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
        if (map.size() != id_l.size()) {
            log.error("INCOMPLETE RESULT: in_size " + id_l.size() +
                              " out_size " + map.size());
        }
        map = MapUtil.sortByValue(map, !invert); // descending order if !invert

        long max = 0;
        if (invert) {
            for (Map.Entry pair : map.entrySet()) {
                max = (long) pair.getValue();
                // TODO use TreeMap to get last item
                //break;
            }
        }

        ListHolder lh = new ListHolder();
        for (Map.Entry pair : map.entrySet()) {
            lh.id2_l.add((String)pair.getKey());
            Long val = (Long) pair.getValue();
            if (invert) {
                val = max - val;
            }
            lh.value_l.add(val);
        }
        if (invert) {
            Collections.reverse(lh.id2_l);
            Collections.reverse(lh.value_l);
        }

        return lh;
    }

    private static final String SQL_GET_MAX_ARCHIVE =
        "SELECT MAX(archive) FROM ##picture";

    public static int getMaxArchive(Connection conn) throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_GET_MAX_ARCHIVE));
            rs = ps.executeQuery();

            if (!rs.next()) {
                throw new SQLException("getMaxArchive: no result");
            }
            return rs.getInt(1);
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_GET_ARCHIVES =
        "SELECT DISTINCT archive FROM ##picture";

    public static Set<Integer> getArchives(Connection conn)
            throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_GET_ARCHIVES));
            rs = ps.executeQuery();

            Set<Integer> ret = new HashSet<>();
            while (rs.next()) {
                ret.add(rs.getInt(1));
            }
            return ret;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_PICTURES_BY_ARCH_FILENAME =
        "SELECT " +
         RECORD_FIELDS +
         PICTURE_FIELDS +
        " FROM ##picture " +
        "WHERE archive = ? AND file_name = ?";

    public static Picture getPictureByArchiveFilename(Connection conn,
                          int archive, String fileName)
                          throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_PICTURES_BY_ARCH_FILENAME));
            ps.setInt(1, archive);
            ps.setString(2, fileName);
            rs = ps.executeQuery();

            while (rs.next()) {
                return picFromRS(rs);
            }

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
        return null;
    }

    private static final String SQL_PICTURES_BY_ANGLE =
        "SELECT id, d_ctr_ab " +
        " FROM ##picture " +
        "WHERE ang_ab = ? " +
        "ORDER BY d_ctr_ab ASC";

    public static ListHolder getPicsAtAngle(Connection conn, int angle)
            throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(chooseDB(SQL_PICTURES_BY_ANGLE));
            ps.setInt(1, angle);
            rs = ps.executeQuery();
            ListHolder lh = new ListHolder();
            while (rs.next()) {
                lh.id2_l.add(rs.getString(1));
                lh.value_l.add(rs.getLong(2));
            }
            return lh;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static float[] getParr(Connection conn, String id, String column)
            throws SQLException {

        try {
            Integer.parseInt(column);
            throw new SQLException("getParr: column is a number: " + column);
        } catch (NumberFormatException desired) {}

        String query = chooseDB("SELECT " + column + " FROM pr.picture WHERE id = ?");

        PreparedStatement ps = null;
        ResultSet rs = null;
        PGvector vec = null;

        try {

            ps = conn.prepareStatement(query);
            ps.setString(1, id);
            rs = ps.executeQuery();
            while (rs.next()) {
                if (vec != null) {
                    throw new SQLException(
                        "PictureDao.getParr(id,col): multiple results: " +
                        id + ", " + column);
                }
                vec = (PGvector) rs.getObject(1);
            }
            if (vec == null) {
                throw new SQLException(
                    "PictureDao.getParr(id,col): no vector for " +
                    id + ", " + column);
            }

            return vec.toArray();

        } catch (SQLException sqe) {

            log.error("Query: " + query + "  id=" + id);
            throw sqe;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    public static String histo_dim_to_column(int dim) {

        String column = null;

        if (dim == 1984) {
            column = "histo_ml";
        } else if (dim == 256) {
            column = "histo_gss";
        } else if (dim == 1728) {
            column = "histo_rgb";
        }

        return column;
    }

    // CCC column
    // FFF distance function
    // LLL list of id's
    private static final String SQL_ORDER_PICTURES_BY_VECTOR =
        "SELECT id," +
        "   (CCC FFF (SELECT CCC FROM pr.picture WHERE id = ?)) as distance" +
        //   , CCC for debug and slowness
        "   FROM pr.picture" +
        " WHERE id IN LLL " +
        " ORDER BY distance;";

    public static ListHolder orderByVectors(Connection conn,
                                                int screen, String id,
                                                String func,
                                                //String type, int dim,
                                                ListHolder sortLh)
            throws SQLException {

        if (sortLh == null  ||  sortLh.size() == 0) {
            throw new SQLException("orderByVectors: list is null or empty");
        }

        long t0 = System.currentTimeMillis();

        String[] ss = func.split("\\.");
        if (ss.length != 3) {
            throw new SQLException("orderByVectors: Expected func.type.model: " + func);
        }
        func = ss[0];
        String type = ss[1];
        String model = ss[2];
        int dim = modelDimension(type, model);
        if (dim == -1) {
            log.error("dim -1 for type=" + type + ", model=" + model);
        }
        type = modelType(type);
        String tag = func + "." + dim;

        // pgvector func

        String pgvfunc = "<=>"; // cos default

        if ("cosine".equals(func)  ||  "cos".equals(func)) {
            pgvfunc = "<=>";
        } else if ("cartes".equals(func)  ||  "poi".equals(func)) {
            pgvfunc = "<->";
        } else {
            throw new SQLException("orderByVectors: unknown func: " + func);
        }

        String column = null;

        if ("pairnet".equals(type)) {

            // personal pair ml
            switch (screen) {
                case 1:
                    column = "left_" + dim; break;
                case 2:
                    column = "right_" + dim; break;
                default:
                    throw new SQLException("Invalid screen (pairnet): " + screen);
            }

        } else if ("imagenet".equals(type)) {

            switch (dim) {

                case 512: // size of basic imagenet summed blocks
                case 256:
                case 128:
                case  64:
                case  32:
                case  16:
                case   8:
                case   4:
                case   2:
                    column = "vgg16_" + dim;
                    break;

                case 1008:
                case  672:
                case  504:
                case  252:
                case  126:
                case   42:
                case   21:
                case    7:
                case    3:
                    column = "nnl_" + dim;
                    break;

                case 1024: // size of basic imagenet summed blocks
                    column = "dense_1024";
                    break;

                case 1280: // size of basic imagenet summed blocks
                case 40:
                case 10:
                case 5:
                    column = "mob_" + dim;
                    break;
            }

        } else if ("histogram".equals(type)) {

            column = histo_dim_to_column(dim);

        }

        if (column == null) {
            throw new SQLException("Invalid type/dimension: " + type + "/" + dim);
        }

        StringBuilder idList = new StringBuilder();
        idList.append("(");
        for (String s : sortLh.id2_l) {
            idList.append("'").append(s).append("',");
        }
        idList.setCharAt(idList.length()-1, ')');

        String query = SQL_ORDER_PICTURES_BY_VECTOR
                                             .replaceAll("CCC", column)
                                             .replaceAll("FFF", pgvfunc)
                                             .replaceAll("LLL", idList.toString());
        query = chooseDB(query);

        if (LOG) {
            log.info("orderByVectors Q, id " + id + "  " + query);
        }

        ListHolder lh = new ListHolder();

        PreparedStatement ps = null;
        ResultSet rs = null;
        int count = 0; // counting 1 query w/ applied limit

        try {

            //Picture p = getPictureById(conn, id);
            // TODO put vals in Picture
            float[] arr1 = getParr(conn, id, column);

            ps = conn.prepareStatement(query);
            ps.setString(1, id);

            rs = ps.executeQuery();

            while (rs.next()) {

                count++;

                String id2 = rs.getString(1);

                lh.id2_l.add(id2);

                double d = rs.getDouble(2);
/* verified
                PGvector v = (PGvector) rs.getObject(2);
log.info("arr2 " +v);
                float[] arr2 = v.toArray();

                double d = "<->".equals(pgvfunc) ?
                            MathUtil.cartesianDist(arr1, arr2) :
                            MathUtil.cos_sim(arr1, arr2);
*/
                lh.value_l.add((long)(d * 1000000000.0));

            }

        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException(e);
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }

        log.info("orderByVectors time/size: " +
                        (System.currentTimeMillis()-t0) + "  " +
                        lh.size());

        lh.value_l = Lists.invertDistribution(lh.value_l);

        if (VEXX_CHEX) {
            log.info("orderByVectors distances " + tag + "\n" +
                    "VEXX" + screen + "/fixed " +
                    Arrays.toString(lh.value_l.toArray(Long[]::new)));
        }

        log.info("PictureDao.orderByVectors\nVEXX " + screen + " " + tag +
                " t=" + (System.currentTimeMillis()-t0) +
                " range(size)s " + lh.value_l.get(0) + ".." +
                             lh.value_l.get(lh.size()-1) + "(" +
                             lh.size() + ")");

        return lh;
    }

    // CCC column
    // FFF distance function
    private static final String SQL_PICTURES_BY_IMG_VECTOR =
        "SELECT id," +
        "   (CCC FFF (SELECT CCC FROM pr.picture WHERE id = ?)) as distance" +
        // , CCC for debug and slowness
        "   FROM pr.picture" +
        " WHERE id != ? " +
        "   AAA " +
        "   AND vertical IS VVV " +
        " ORDER BY distance;";

    final static boolean LOG = true;
    final static Object sinker = new Object();
    static int checks = 0;
    static int checked_vecs = 0;
    static int errors = 0;

    private static String modelType(String type) {

        if ("1".equals(type)) {
            return "histogram";
        }
        if ("2".equals(type)) {
            return "imagenet";
        }
        if ("3".equals(type)) {
            return "pairnet";
        }
        return null;
    }

    public static int modelDimension(String type, String model) {

        int dim = -1;

        if ("1".equals(type)) {

            // might start with "hist", might want to keep it
            if ("histo_gss".equals(model)) {
                dim = 256;
            } else if ("histo_rgb".equals(model)) {
                dim = 1728;
            } else if ("histo_ml".equals(model)) {
                dim = 1984;
            } else if (model.startsWith("histo_")) {
                log.warn("Unexpected 1.histo_ model: " + model +
                            " parsing for dimension");
            } 

            if (dim == -1) {

                String s = null;
                for (int k=0;k<model.length();k++) {
                    if (Character.isDigit(model.charAt(k))) {
                        s = model.substring(k);
                        break;
                    }
                }
                if (s != null) {

                    try {
                        dim = Integer.parseInt(s);
                        if (dim != 256  &&  dim != 1728  &&  dim != 1984) {
                            log.error("Unexpected dimension: " + dim);
                            dim = 256;// gss
                        }
                    } catch (NumberFormatException nfe) {
                        log.error("Expected integer dimension for type 1 (histogram): " + model, nfe);
                    }
                }
            }
            if (dim == -1) {
                log.error("Bad model type 1: " + model +
                                " using dim 256 for gss");
                dim = 256;
            }

        } else if ("2".equals(type)) {

            try {
                dim = Integer.parseInt(model);
                // take dim on faith, assumed a vgg16 fold
            } catch (NumberFormatException nfe) {
                // normal case
                dim = -1;
            }
            if (dim == -1) {

                // it's not just a number
                //      could be <modelCode>_<nnn>
                String[] ss = model.split("_");
                if (ss.length == 2) {
                    // could just ignore here
                    List<String> modelCodes = new ArrayList<>();
                    modelCodes.add("vgg16");
                    modelCodes.add("dense");
                    modelCodes.add("nnl");
                    modelCodes.add("mob");
                    if (!modelCodes.contains(ss[0])) {
                        log.warn("Unexpected model code: " + ss[0]);
                    }
                    try {
                        dim = Integer.parseInt(ss[1]);
                    } catch (NumberFormatException nfe) {
                        log.error("model: parsing dim, expected mmm_<dim>: " + model);
                        dim = -1;
                    }
                }
                if (dim == -1) {

                    if ("vgg16".equals(model)) {
                        dim = 512;  // default block
                    } else if ("densenet121".equals(model)) {
                        dim = 1024;
                    } else if ("mobilenetv2".equals(model)) {
                        dim = 1280;
                    }
                }
            }
            if (dim == -1) {  // still
                dim = 4;  // vgg16_4
            }
        } else {

            // now it's personal
            type = "pairnet";

            String num = model;
            if (num.startsWith("pair")) {
                num = num.substring("pair".length());
            }
            try {
                dim = Integer.parseInt(num);
                if (dim != 2  &&  dim != 3  &&  dim != 5  && dim != 12) {
                    log.error("Unexpected dimension: " + dim);
                    dim = 3;// middling
                }
            } catch (NumberFormatException nfe) {
                log.error("Expected integer dimension for type 3 (pair vector): " + model, nfe);
                dim = 3;// middling
            }
        }

        return dim;
    }

    /*
    **  matchVector - return null for no unseen in list.
    */
    public static ListHolder matchVector(Connection conn,
                                                String orient,
                                                int screen, String id,
                                                String func, // cos.1.256..
                                                int[] archives,
                                                int limit, 
                                                Set<String> picSet,
                                                SeenIds seen)
            throws SQLException {

//new Exception("matchVector trace").printStackTrace();

        long t0 = System.currentTimeMillis();

        String[] ss = func.split("\\.");
        if (ss.length != 3) {
            throw new SQLException("matchVector: Expected func.type.model: " + func);
        }
        func = ss[0];
        String type = ss[1];
        String model = ss[2];
        int dim = modelDimension(type, model);

        String archStr = "";
        if (archives != null) {
            archStr = " AND archive IN " +
                Arrays.toString(archives);
            archStr = archStr.replace("[", "(")
                             .replace("]", ")");
        }

        // pgvector func

        String pgvfunc = "<=>"; // cos default

        if ("cosine".equals(func)  ||  "cos".equals(func)) {
            pgvfunc = "<=>";
        } else if ("cartes".equals(func)  ||  "poi".equals(func)) {
            pgvfunc = "<->";
        } else {
            throw new SQLException("matchVector: unknown func: " + func);
        }

        String column = null;
        if ("1".equals(type)) {
            column = histo_dim_to_column(dim);
        } else if ("3".equals(type)) {

            if (dim < 13) {
log.info("VEX dim<13: " + dim);
                // personal pair ml
                switch (screen) {
                case 1:
                    column = "left_" + dim; break;
                case 2:
                    column = "right_" + dim; break;
                default:
                    throw new SQLException("Invalid screen: " + screen);
                }
            } else {
                throw new SQLException("Invalid dim for type 3==pairml: " + dim);
            }
        } else {
            column = model;
        }

        String tag = func + "." + column;

        String query = SQL_PICTURES_BY_IMG_VECTOR.replaceAll("VVV",
                                                    "v".equals(orient) ? "true" : "false")
                                             .replaceAll("AAA", archStr)
                                             .replaceAll("CCC", column)
                                             .replaceAll("FFF", pgvfunc);
        query = chooseDB(query);

        if (LOG) {
            log.info("matchVector Q, id " + id + "  " + query);
        }

        ListHolder lh = new ListHolder();

        Set<String> skipped = new HashSet<>();
        Set<String> used = new HashSet<>();

        PreparedStatement ps = null;
        ResultSet rs = null;
        int count = 0; // counting 1 query w/ applied limit

        try {

            //Picture p = getPictureById(conn, id);
            // TODO put vals in Picture
            float[] arr1 = getParr(conn, id, column);


            ps = conn.prepareStatement(query);
            ps.setString(1, id);
            ps.setString(2, id);

            rs = ps.executeQuery();

            while (rs.next()) {

                count++;

                String id2 = rs.getString(1);

                if (picSet != null  &&  !picSet.contains(id2)) {
                    skipped.add(id2);
                    continue;
                }
                if (seen.contains(id2)) {
                    continue;
                }
                if (used.contains(id2)) {
                    continue;
                }
                used.add(id2);
                lh.id2_l.add(id2);

                double d = rs.getDouble(2);
/* checked
                PGvector v = (PGvector) rs.getObject(2);
log.info("arr2 " +v);
                float[] arr2 = v.toArray();

                double d = "<->".equals(pgvfunc) ?
                            MathUtil.cartesianDist(arr1, arr2) :
                            MathUtil.cos_sim(arr1, arr2);
*/
                lh.value_l.add((long)(d * 1000000000.0));

                if (lh.size() >= limit) {
                    break;
                }
            }

        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException(e);
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }

        log.info("matchVector time/list/size//skipped: " +
                        (System.currentTimeMillis()-t0) + "  " +
                        lh.size() + " " +
                        skipped.size());

        if (lh.size() == 0) {
            return null;
        }

        if (VEXX_CHEX) {
            log.info("matchVector distances " + tag + "\n" +
                    "VEXX" + screen + "/orig " +
                    Arrays.toString(lh.value_l.toArray(Long[]::new)));
        }

        lh.value_l = Lists.invertDistribution(lh.value_l);

        if (VEXX_CHEX) {
            log.info("matchVector distances " + tag + "\n" +
                        "VEXX" + screen + "/fixed " +
                        Arrays.toString(lh.value_l.toArray(Long[]::new)));
        }

        log.info("PictureDao.matchVector\nVEXX " + screen + " " + tag +
                " t=" + (System.currentTimeMillis()-t0) +
                " range(size)s " + lh.value_l.get(0) + ".." +
                             lh.value_l.get(lh.size()-1) + "(" +
                             lh.size() + "), " +
                "  examined " + count +
                " skipped " + skipped.size() +
                (VEXX_CHEX && checked_vecs>0 ?
                    " total errors seen: " + errors + " checked " + checked_vecs
                    : ""));

        if (!VEXX_CHEX) {
            return lh;
        }

        int err = 0;
        int frac_err = 0;

        long val = lh.value_l.get(0);
        for (int j=1; j<lh.size(); j++) {
            long v2 = lh.value_l.get(j);
            if (v2 > val) {
                err++;
                long diff = Math.abs(v2-val);
                double frac = (double) diff / val;
                if (frac > 0.1) {
                    frac_err++;
                    //throw new SQLException("Bad val at i=" + i + " j=" + j +
                    //        " " + v2 + " not > " + val + " and frac " + frac);
                }
            }
            val = v2;
        }

        synchronized (sinker) {

            checks++;
            errors += err;
            checked_vecs += count;

            if (errors == 0  &&  checks > ENOUGH_OK_CHECKS) {
                log.info("PictureDao.matchVector:\nVEXX: " +
                        "turning off checks at " + checks +
                        " calls, total vecs " + checked_vecs);
                VEXX_CHEX = false;
            }

            if (err > 0) {
                log.warn("PictureDao.matchVector\nVEXX " + screen + " " + tag +
                    "  err " + err + " >0.1 " + frac_err);
            } else if (VEXX_CHEX) {
                log.info("\nVEXX" + screen + "/check ok, cumulative ok calls/distances " +
                            checks + "/" + checked_vecs);
            }
        } // sync

        // VEXX_CHEXed
        return lh;
    }

    private static final String SQL_PICTURES_BY_VECTOR =
        "SELECT id," +
        "   (CCC FFF 'VEC') as distance" +
        // , CCC for debug and slowness
        "   FROM pr.picture" +
        " WHERE " +
        "   AAA " +
        "   vertical IS VVV " +
        " ORDER BY distance;";

    /*
    **  matchVector - use function and vector type/dim,
    **                  return null if no unseen in list.
    */
    public static ListHolder matchVector(Connection conn,
                                                String orient,
                                                float[] vec,
                                                int[] archives,
                                                String func,
                                                String column,
                                                int limit, 
                                                Set<String> picSet,
                                                SeenIds seen)
            throws SQLException {

        long t0 = System.currentTimeMillis();

        String pgvfunc = "<=>"; // cos default
        if ("poi".equals(func)  ||  "cartes".equals(func)) {
            pgvfunc = "<->";
        }

        String vecStr = Arrays.toString(vec);

        String archStr = "";
        if (archives != null) {
            archStr = " AND archive IN " +
                Arrays.toString(archives);
            archStr = archStr.replace("[", "(")
                             .replace("]", ")");
            archStr += " AND ";
        }

        String query = SQL_PICTURES_BY_VECTOR
                            .replaceAll("CCC", column)
                            .replaceAll("FFF", pgvfunc)
                            .replaceAll("VEC", vecStr)
                            .replaceAll("AAA", archStr)
                            .replaceAll("VVV", "v".equals(orient) ? "true" : "false");
        query = chooseDB(query);

log.info("QV " + query);

        Set<String> skipped = new HashSet<>();
        Set<String> used = new HashSet<>();

        PreparedStatement ps = null;
        ResultSet rs = null;
        int count = 0;

        ListHolder lh = new ListHolder();

        try {
            ps = conn.prepareStatement(query);

            rs = ps.executeQuery();

            while (rs.next()) {

                count++;

                String id2 = rs.getString(1);

                if (picSet != null  &&  !picSet.contains(id2)) {
                    skipped.add(id2);
                    continue;
                }
                if (seen.contains(id2)) {
                    continue;
                }

                if (used.contains(id2)) {
                    continue;
                }
                used.add(id2);

                double d = rs.getDouble(2);

                lh.id2_l.add(id2);
                lh.value_l.add((long)(d * 1000000000.0));

                if (lh.size() >= limit) {
                    break;
                }
            }
        } catch (SQLException sqe) {
            log.error("matchVector: " + sqe);
            throw sqe;
        } catch (Exception e) {
            log.error("matchVector: " + e);
            throw new SQLException("matchVector: " + e);
        }

        log.info("matchVector skipped " + skipped.size());

        if (lh.size() == 0) {
            return null;
        }

        // flip

        long max = lh.value_l.get(lh.value_l.size()-1);
        max += 10;
        for (int i=0; i<lh.value_l.size(); i++) {
            lh.value_l.set(i, max - lh.value_l.get(i));
        }
        return lh;
    }


    /*
    **  matchVectors - use per-pic function and vector type/dim.
    **                  return null if no unseen pics in listss.
    */
    public static ListHolder[] matchVectors(Connection conn,
                                                String orient, 
                                                List<String> ids, String[] funcDims,
                                                int[] archives,
                                                int limit, 
                                                Set<String> picSet, SeenIds seen)
            throws SQLException {

        long t0 = System.currentTimeMillis();

        String archStr = "";
        if (archives != null) {
            archStr = " AND archive IN " +
                Arrays.toString(archives);
            archStr = archStr.replace("[", "(")
                             .replace("]", ")");
        }
        String query = SQL_PICTURES_BY_IMG_VECTOR
                            .replaceAll("VVV", "v".equals(orient) ? "true" : "false")
                            .replaceAll("AAA", archStr);
        query = chooseDB(query);

        Set<String> skipped = new HashSet<>();
        Set<String> used = new HashSet<>();

        PreparedStatement ps = null;
        ResultSet rs = null;
        int count = 0; // counting 2 queries w/ applied limit

        ListHolder[] lhs = new ListHolder[ids.size()];

        //Thread[] threads = new Thread[nprocs];

        String q = null;

        try {

            for (int i=0; i<lhs.length; i++) {

                // parent pic
                //   TODO put vals in Picture?
                //      Picture p = getPictureById(conn, id);

                String id = ids.get(i);

                // method

                String[] ss = funcDims[i].split("\\.");
                if (ss.length != 3) {
                    log.error("Bad funcDim/3: " + 
                                ss.length + " for " + funcDims[i]);
                    return null;
                }

                String pgvfunc = "<=>"; // cos default
                if ("poi".equals(ss[0])  ||  "cartes".equals(ss[0])) {
                    pgvfunc = "<->";
                }
                String column = ss[2];

                //  ss[1] in {1,2,3} not used here - seems just debugging

                // get pic vec to match

                float[] arr1 = getParr(conn, id, column);

                q = query.replaceAll("FFF", pgvfunc)
                                .replaceAll("CCC", column);
                if (LOG) {
                    log.info("Q/matchVectors  id " + id + "  " + q);
                }
                ps = conn.prepareStatement(q);
                ps.setString(1, id);
                ps.setString(2, id);

                rs = ps.executeQuery();

                lhs[i] = new ListHolder();
                while (rs.next()) {

                    count++;

                    String id2 = rs.getString(1);

                    if (picSet != null  &&  !picSet.contains(id2)) {
                        skipped.add(id2);
                        continue;
                    }
                    if (seen.contains(id2)) {
                        continue;
                    }
                    if (used.contains(id2)) {
                        continue;
                    }
                    used.add(id2);
                    lhs[i].id2_l.add(id2);

                    double d = rs.getDouble(2);
/* checked
                    PGvector v = (PGvector) rs.getObject(2);
log.info("arr2 " +v);
                    float[] arr2 = v.toArray();

                    double d = "<->".equals(pgvfunc) ?
                            MathUtil.cartesianDist(arr1, arr2) :
                            MathUtil.cos_sim(arr1, arr2);
*/
                    lhs[i].value_l.add((long)(d * 1000000000.0));

                    if (lhs[i].size() > limit) {
                        break;
                    }
                }

                closeSQL(rs);
                closeSQL(ps);
            }

        } catch (SQLException e) {
            log.error("DB: " + e +
                        "\nQ: " + q);
            throw e;
        } catch (Exception e) {
            throw new SQLException(e);
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }

        log.info("time/lists/sizes//skipped: " +
                        (System.currentTimeMillis()-t0) + "  " +
                        lhs[0].size() + "," + lhs[1].size() + " " +
                        skipped.size());

        if (VEXX_CHEX) {
            log.info("distances " + Arrays.toString(funcDims) + "\n" +
            "VEXX1/orig " + Arrays.toString(lhs[0].value_l.toArray(Long[]::new)) + "\n" +
            "VEXX2/orig " + Arrays.toString(lhs[1].value_l.toArray(Long[]::new)));
        }

        for (int i=0; i<lhs.length; i++) {
            if (lhs[i].size() == 0) {
                return null;
            }
        }

        for (int i=0; i<lhs.length; i++) {
            lhs[i].value_l = Lists.invertDistribution(lhs[i].value_l);
        }

        return lhs;
    }

    public static ListHolder[] matchVectors(Connection conn,
                                                String orient, List<String> ids,
                                                String func, String type, int dim,
                                                int[] archives,
                                                int limit, Set<String> picSet, SeenIds seen)
            throws SQLException {

        long t0 = System.currentTimeMillis();

        String tag = func + "." + dim;

        String archStr = "";
        if (archives != null) {
            archStr = " AND archive IN " +
                Arrays.toString(archives);
            archStr = archStr.replace("[", "(")
                             .replace("]", ")");
        }

        // pgvector func

        String pgvfunc = "<=>"; // cos default

        if ("cosine".equals(func)  ||  "cos".equals(func)) {
            pgvfunc = "<=>";
        } else if ("cartes".equals(func)  ||  "poi".equals(func)) {
            pgvfunc = "<->";
        } else {
            throw new SQLException("matchVectors: unknown func: " + func);
        }

        String[] columns = null;

        if ("pairnet".equals(type)) {

            // dim 2, 3, 5, 12
            columns = new String[] { "left_" + dim, "right_" + dim };

        } else if ("imagenet".equals(type)) {

            // imagenet vectors are defined in
            //      phobrain_local/picture_vectors

            // This 'else' is about using dimension
            // as shorthand w/o naming the model,
            // so not all model/dimension pairs are
            // covered.

            // vgg16 folds are the default when dimensions
            //      collide, as with 2

            switch (dim) {

                // VGG16, block size 512

                case   2:  // indexed  [also DenseNet121
                case   4:  // indexed  [also DenseNet121
                case   8:
                case  16:  // indexed
                case  32:  //          [also DenseNet121
                case  64:  // indexed  [also DenseNet121
                case 128:
                case 256:
                case 512:  // indexed

                    columns = new String[] { "vgg16_" + dim, "vgg16_" + dim };
                    break;

                // MobileNetV2, block size 1280

                case    5:  // indexed
                case   10:  // indexed
                case   40:  // not indexed
                case 1280:  // not indexed

                    columns = new String[] { "mob_" + dim, "mob_" + dim };
                    break;

                // NASNetLarge, block size 4032

                case    3:  // indexed
                case    7:  // indexed
                case   21:  // indexed
                case   42:
                case  126:
                case  252:
                case  504:
                case 1008:

                    columns = new String[] { "nnl_" + dim, "nnl_" + dim };
                    break;

                // DenseNet121, block size 1024

                case 1024:
                    columns = new String[] { "dense_" + dim, "dense_1024" + dim };
                    break;

                default: break;
            }

        } else if ("histogram".equals(type)  ||  "color".equals(type)) {

            if (dim == 1984) {
                columns = new String[] { "histo_ml", "histo_ml" };
            } else if (dim == 1728) {
                columns = new String[] { "histo_rgb", "histo_rgb" };
            } else if (dim == 256) {
                columns = new String[] { "histo_gss", "histo_gss" };
            }
        }
        if (columns == null) {
            throw new SQLException("matchVectors: unknown type=" + type + " dimension: " + dim);
        }

        String query = SQL_PICTURES_BY_IMG_VECTOR.replaceAll("VVV",
                                                    "v".equals(orient) ? "true" : "false")
                                             .replaceAll("AAA", archStr)
                                             .replaceAll("FFF", pgvfunc);
        query = chooseDB(query);

        ListHolder[] lhs = new ListHolder[ids.size()];

        Set<String> skipped = new HashSet<>();
        Set<String> used = new HashSet<>();

        PreparedStatement ps = null;
        ResultSet rs = null;
        int count = 0; // counting 2 queries w/ applied limit

        try {

            for (int i=0; i<lhs.length; i++) {

                // parent pic

                String id = ids.get(i);
                //Picture p = getPictureById(conn, id);
                // TODO put vals in Picture
                float[] arr1 = getParr(conn, id, columns[i]);

                String q = query.replaceAll("CCC", columns[i]);
                if (LOG) {
                    log.info("Q/matchVectors  id " + id + "  " + q);
                }

                ps = conn.prepareStatement(q);
                ps.setString(1, id);
                ps.setString(2, id);

                rs = ps.executeQuery();

                lhs[i] = new ListHolder();
                while (rs.next()) {

                    count++;

                    String id2 = rs.getString(1);

                    if (picSet != null  &&  !picSet.contains(id2)) {
                        skipped.add(id2);
                        continue;
                    }
                    if (seen.contains(id2)) {
                        continue;
                    }
                    if (used.contains(id2)) {
                        continue;
                    }
                    used.add(id2);
                    lhs[i].id2_l.add(id2);

                    double d = rs.getDouble(2);
/* checked
                    PGvector v = (PGvector) rs.getObject(2);
log.info("arr2 " +v);
                    float[] arr2 = v.toArray();

                    double d = "<->".equals(pgvfunc) ?
                            MathUtil.cartesianDist(arr1, arr2) :
                            MathUtil.cos_sim(arr1, arr2);
*/
                    lhs[i].value_l.add((long)(d * 1000000000.0));

                    if (lhs[i].size() > limit) {
                        break;
                    }
                }

                closeSQL(rs);
                closeSQL(ps);
            }

        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException(e);
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }

        log.info("time/lists/sizes//skipped: " +
                        (System.currentTimeMillis()-t0) + "  " +
                        lhs[0].size() + "," + lhs[1].size() + " " +
                        skipped.size());

        if (VEXX_CHEX) {
            log.info("distances " + tag + "\n" +
            "VEXX1/orig " + Arrays.toString(lhs[0].value_l.toArray(Long[]::new)) + "\n" +
            "VEXX2/orig " + Arrays.toString(lhs[1].value_l.toArray(Long[]::new)));
        }

        lhs[0].value_l = Lists.invertDistribution(lhs[0].value_l);
        lhs[1].value_l = Lists.invertDistribution(lhs[1].value_l);

        if (VEXX_CHEX) {
            log.info("distances " + tag + "\n" +
            "VEXX1/fixed " + Arrays.toString(lhs[0].value_l.toArray(Long[]::new)) + "\n" +
            "VEXX2/fixed " + Arrays.toString(lhs[1].value_l.toArray(Long[]::new)));
        }

        log.info("PictureDao.matchVectors\nVEXX " + tag +
                " t=" + (System.currentTimeMillis()-t0) +
                " range(size)s " + lhs[0].value_l.get(0) + ".." +
                             lhs[0].value_l.get(lhs[0].size()-1) + "(" +
                             lhs[0].size() + "), " +
                             lhs[1].value_l.get(0) + ".." +
                             lhs[1].value_l.get(lhs[1].size()-1) + "(" +
                             lhs[1].size() + ") " +
                "  examined " + count +
                " skipped " + skipped.size() +
                (VEXX_CHEX && checked_vecs>0 ?
                    " total errors seen: " + errors + " checked " + checked_vecs
                    : ""));

        if (!VEXX_CHEX) {
            return lhs;
        }

        int err = 0;
        int frac_err = 0;
        for (int i=0; i<2; i++) {
            ListHolder lh = lhs[i];
            long val = lh.value_l.get(0);
            for (int j=1; j<lh.size(); j++) {
                long v2 = lh.value_l.get(j);
                if (v2 > val) {
                    err++;
                    long diff = Math.abs(v2-val);
                    double frac = (double) diff / val;
                    if (frac > 0.1) {
                        frac_err++;
                        //throw new SQLException("Bad val at i=" + i + " j=" + j +
                        //        " " + v2 + " not > " + val + " and frac " + frac);
                    }
                }
                val = v2;
            }
        }
        synchronized (sinker) {

            checks++;
            errors += err;
            checked_vecs += count;

            if (errors == 0  &&  checks > ENOUGH_OK_CHECKS) {
                log.info("PictureDao.matchVectors:\nVEXX: " +
                        "turning off checks at " + checks +
                        " calls, total vecs " + checked_vecs);
                VEXX_CHEX = false;
            }
        }

        if (err > 0) {
            log.warn("PictureDao.matchVectors\nVEXX " + tag +
                "  err " + err + " >0.1 " + frac_err);
        } else if (VEXX_CHEX) {
            log.info("\nVEXX/check ok, cumulative ok calls/distances " +
                        checks + "/" + checked_vecs);
        }

        // CHECKed
        return lhs;
    }

    public static double vec_dist(Picture p1, Picture p2)
            throws SQLException {

        try {

            // from old {} vectors
            if (p1.vec_l != null  &&  p2.vec_r != null) {
                return MathUtil.cartesianDist(p1.vec_l, p2.vec_r);
            }

            // general case: vague imagenet

            if (p1.vgg16_4 != null  &&  p2.vgg16_4 != null) {
                return MathUtil.cos_sim(p1.vgg16_4, p2.vgg16_4);
            }

            //  fallback: distance between color centers

            double dr = p1.r - p2.r;
            double dg = p1.g - p2.g;
            double db = p1.b - p2.b;

            return Math.sqrt( dr * dr + dg * dg + db * db);

        } catch (SQLException sqe) {
            throw sqe;
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    public static double vec_dist(Connection conn, String id1, String id2)
            throws SQLException {

        Picture p1 = getPictureById(conn, id1);
        Picture p2 = getPictureById(conn, id2);

        return vec_dist(p1, p2);
    }

    // pgvector

    public static void setVectorColumn(Statement stmt, // conn.stmt reused by caller
                                        String column,
                                        String id,
                                        double[] values,
                                        String[] fields,
                                        int start) {

        StringBuilder sql = new StringBuilder("UPDATE pr.picture SET ")
                                            .append(column).append("='[");

        if (values != null) {

            for (int i=start; i<values.length; i++) {
                sql.append((float)values[i]).append(",");
            }

        } else if (fields != null) {

            for (int i=start; i<fields.length; i++) {
                sql.append(fields[i]).append(",");
            }

        } else {

            err("setVectorColumn(id="+id+"): need values or fields");
        }

        sql.setCharAt(sql.length()-1, ']');
        sql.append("' WHERE id='" + id +"'");

        try {
            //pout("==> " + sql);
            stmt.executeUpdate(sql.toString());
            //err("updated " + stmt.getUpdateCount());
            if (stmt.getUpdateCount() == 0) {
                pout("Warning: for id " + id + " updated 0 rows - likely deleted since vecs were built");
            } else if (stmt.getUpdateCount() != 1) {
                err("Update: " + stmt.getUpdateCount() + " on " + sql);
            }
        } catch (SQLException sqe) {
            new Exception("here, my eyes are here").printStackTrace();
            err("FIXME/SQL: id " + id + " exception " + sqe);
            //pout("Skipping " + id + " exception " + sqe);
        }
    }

    /*
    **  updateVectorColumn()
    **
    */
    private static void updateVectorColumn(Statement stmt,
                                    String column,
                                    int[] folds,
                                    String id,
                                    double[] values,
                                    String[] fields,
                                    int start)
            throws SQLException {

        // expect values or fields
        int nulls = 0;
        if (values == null) nulls++;
        if (fields == null) nulls++;
        if (nulls != 1) {
            throw new SQLException("PictureDao.updateVectorColumn: one of [values, fields] needs 2B null.");
        }
        if (values != null  &&  folds == null) {
            throw new SQLException("PictureDao.updateVectorColumn: values needs folds.");
        }

        StringBuilder sql = new StringBuilder("UPDATE pr.picture SET ");

        if (values != null) {

            if (values.length == 0) {
                err("PictureDao.updateVectorColumn: values array 0-length");
            }

            for (int target : folds) {

                double[] vals = values;
                int len = vals.length;

                int fold = len / target;

                if (fold > 1) {

                    len = vals.length / fold;
                    if (len * fold != vals.length) {
                        err("Fold is bad");
                    }
                    vals = new double[len];
                    System.arraycopy(values, 0, vals, 0, vals.length);
                    for (int i=1; i<fold; i++) {

                        int aj = i * len;
                        for (int j=0; j<len; j++) {
                            vals[j] += values[aj++];
                        }
                    }
                    for (int j=0; j<len; j++) {
                        vals[j] /= (double) fold;
                    }
                }

                sql.append(column + "_" + len).append("='[");

                //pout("values: " + values.length);
                for (int i=start; i<vals.length; i++) {
                    sql.append((float)vals[i]).append(",");
                }
                sql.setCharAt(sql.length()-1, ']');
                sql.append("',");
            }

        } else if (fields != null) {

            if (folds != null) {
                err("fields/folds both not null");
            }

            // just use the numbers

            sql.append(column).append("='[");
            if (fields.length == 0) {
                err("PictureDao.updateVectorColumn: fields array 0-length");
            }
            for (int i=start; i<fields.length; i++) {
                sql.append(fields[i]).append(",");
            }
            sql.setCharAt(sql.length()-1, ']');
            sql.append("',");
        }

        sql.setCharAt(sql.length()-1, ' ');
        sql.append(" WHERE id='" + id +"'");

        try {
            //pout("==> " + sql);
            stmt.executeUpdate(sql.toString());
            //err("updated " + stmt.getUpdateCount());
            if (stmt.getUpdateCount() == 0) {
                pout("Warning: for id " + id + " updated 0 rows - likely deleted since vecs were built");
            } else if (stmt.getUpdateCount() != 1) {
                err("Update: " + stmt.getUpdateCount() + " on " + sql);
            }
        } catch (SQLException sqe) {
            new Exception("HERE is the truth").printStackTrace();
            err("FIXME/SQL: id " + id + " exception " + sqe + "\nSQL: " + sql);
            //pout("Skipping " + id + " exception " + sqe);
        }
    }

    /**
     **  updateHistoVecs - using base_dir + files from db,
     **                 read <base_dir>/<archive_num>/<hist_file>
     **  pgvector: alter table pr.picture add column xxx vector(3);
     **  HACK: if dim==1984, assuming 256, 1728 split
     **         for greyscale+saturation, rgb12.
     **/

    public static void updateHistoVecs(Connection conn, List<Picture> pics,
                                            String column, File base_dir) {

        Statement stmt = null;

        try {

            conn.setAutoCommit(false);

            // read all to make sure in order, while memory lasts

            pout("\nReading " + pics.size() +
                    " histogram files for pr.picture." + column +
                    " in " + base_dir + "/<picture.archive_num>/<picture.fname>");

            List<FileRec> frs = new ArrayList<>();

            for (Picture p : pics) {

                String path = base_dir + "/" + p.archive + "/" + p.fileName;

                FileRec fr = new FileRec(path, true); // true==expectArchSeq
                //pout("FR " + fr);
                fr.readHistogram();

                frs.add(fr);
            }

            int dim = frs.get(0).histogram.length;

            pout("\nUpdating pr.picture (pg)vector " + column + " for " +
                            pics.size() +
                            " pic histograms, dimension/file=" +
                                    frs.get(0).histogram.length + "/" +
                                    frs.get(0));

            stmt = conn.createStatement();

            int count = 0;

            if (dim == 1984) {

                pout("SPECIAL CASE: also split histo into 256,1728");

                double[] gss = new double[256];
                double[] rgb = new double[1728]; // 12^3

                for (FileRec fr : frs) {

                    // copy and normalize

                    System.arraycopy(fr.histogram, 0, gss, 0, 256);
                    System.arraycopy(fr.histogram, 256, rgb, 0, 1728);

                    MathUtil.normalize(fr.histogram);
                    MathUtil.normalize(gss);
                    MathUtil.normalize(rgb);

                    setVectorColumn(stmt, column, fr.id.id, fr.histogram, null, 0);

                    setVectorColumn(stmt, "histo_gss", fr.id.id, gss, null, 0);
                    setVectorColumn(stmt, "histo_rgb", fr.id.id, rgb, null, 0);

                    count++;

                    if (count % 16 == 0) {
                        conn.commit();
                    }
                }
            } else {

                // not tested

                for (FileRec fr : frs) {

                    setVectorColumn(stmt, column, fr.id.id, fr.histogram, null, 0);

                    count++;

                    if (count % 16 == 0) {
                        conn.commit();
                    }
                }
            }
            conn.commit();
            pout("\nupdateHistoVecs: Updated pr.picture (pg)vector " + column +
                            " for " + pics.size() +
                            " pic histograms, dimension=" +
                                    frs.get(0).histogram.length);
                            //(fold > 1 ? " folded to " +
                                        //(frs.get(0).histogram.length /  fold) :
                                        //""));

        } catch (Exception e) {
            e.printStackTrace();
            err("Exiting: " + e);
        } finally {
            DaoBase.closeSQL(stmt);
            DaoBase.closeSQL(conn);
        }

    }

    /**
     **  updateImagenetVecsFromDir - using base_dir + ids from db,
     **                 read <base_dir>/<archive_num:seq> files.
     **  pgvector: alter table pr.picture add column xxx vector(size);
     **
     **     Model       vecs.size
     **     VGG16       25087  => input files are averaged 512-block
     **/

    private static void updateImagenetVecsFromDir(Connection conn,
                                            String column,
                                            int expected_dim, // for checking
                                            int[] folds, // null, [0,..] => native
                                            List<Picture> pics,
                                            File base_dir) {

        long t0 = System.currentTimeMillis();


        Statement stmt = null;

        try {

            conn.setAutoCommit(false);

            pout("PictureDao.updateImagenetVecs: " + column + " pics " + pics.size());

            // read all while memory lasts

            pout("\nReading " + pics.size() +
                    " vec files for pr.picture." + column +
                    " in " + base_dir + "/<picture.archive_num:picture.seq>");

            List<FileRec> frs = new ArrayList<>();

            for (Picture p : pics) {

                String path = base_dir + "/" + p.id.replace("/", ":") + ".hist_bin";

                File f = new File(path);
                if (!f.exists()) {
                    path = base_dir + "/" + p.id.replace("/", ":") + ".txt";
                }
                FileRec fr = new FileRec(path, true); // true==expectArchSeq
                //pout("FR " + fr);
                fr.readHistogram();

                frs.add(fr);
            }

            int dim = frs.get(0).histogram.length;

            if (dim != expected_dim) {
                err("PictureDao.updateImagenetVecs: dim is " + dim +
                                " expected " + expected_dim);
            }

            long t1 = System.currentTimeMillis();

            pout("PictureDao.updateImagenetVecs: loaded " + pics.size() +
                    " vectors in " + ((t1-t0)/1000) + " sec, dimension " + dim);

            if (folds == null) {
                folds = new int[] { dim };
            } else {
                for (int fold : folds) {
                    if (fold > dim) {
                        err("PictureDao.updateImagenetVecs: fold > dim: " +
                                fold + " > " + dim);
                    }
                    if (dim % fold != 0) {
                        err("PictureDao.updateImagenetVecs: fold not integer divide: " +
                                dim + " / " + fold);
                    }
                }
            }

            pout("\nUpdating pr.picture (pg)vector " + column +
                                " folds " + Arrays.toString(folds) +
                                " pics " + pics.size() +
                                " 1st dimension/file=" +
                                    frs.get(0).histogram.length + "/" +
                                    frs.get(0));

            conn.setAutoCommit(false);

            stmt = conn.createStatement();

            int count = 0;

/*
might do this for slices of vector

            if (dim == 1984) {

                pout("split vec into x,y");

                double[] gss = new double[256];
                double[] rgb = new double[1728]; // 12^3

                for (FileRec fr : frs) {

                    // copy and normalize

                    System.arraycopy(fr.histogram, 0, gss, 0, 256);
                    System.arraycopy(fr.histogram, 256, rgb, 0, 1728);

                    MathUtil.normalize(fr.histogram);
                    MathUtil.normalize(gss);
                    MathUtil.normalize(rgb);

                    setVectorColumn(stmt, column, fr.id.id, fr.histogram, null, 0);

                    setVectorColumn(stmt, "histo_gss", fr.id.id, gss, null, 0);
                    setVectorColumn(stmt, "histo_rgb", fr.id.id, rgb, null, 0);

                    count++;

                    if (count % 16 == 0) {
                        conn.commit();
                    }
                }
            }
*/


            for (FileRec fr : frs) {

                updateVectorColumn(stmt, column, folds, fr.id.id, fr.histogram, null, 0);

                count++;

                // TODO - optimize for buffer
                if (count % 16 == 0) {
                    conn.commit();
                }
            }
            conn.commit();

           pout("\nupdateHistoVecs: Updated pr.picture (pg)vector " + column +
                            " for " + pics.size() +
                            " pic histograms, dimension=" +
                                    frs.get(0).histogram.length);

        } catch (Exception e) {
            e.printStackTrace();
            err("Exiting: " + e);
        } finally {
            DaoBase.closeSQL(stmt);
        }
    }

    /**
     **  updatePairVecsFromFiles - adding pair-based (left, right-specific) latent vecs
     **     to table. Versions (pgvector used):
     **         plain: alter table pr.picture add column vec_r double precision array;
     **         pgvector: alter table pr.picture add column xxx vector(3);
     **             size limit: 16,000, indexable 2,000
     **     Column name is based on dimension, so not explicitly-stated at top level,
     **     while checked for consistency when reading. If >1 pair vec files per dimension
     **     were used, we'd need a base column name for each. For now, exiting if tried.
     **/

    private static void updatePairVecsFromFiles(List<File> files, List<Picture> pics, int nprocs) {

        // for lookup
        Map<String, Picture> map = new HashMap<>();
        for (Picture pic : pics) {
            map.put(pic.id, pic);
        }

        pout("updatePairVecsFromFiles: " +  files.size() + " files, " + 
                                            pics.size() +  " pics, " +
                                            nprocs +        " procs");

        Set<Integer> dimTrack = new HashSet<>();

        for (File vecFile : files) {

            pout("PictureDao.updatePairVecsFromFiles: Loading vecs for " + 
                            pics.size() + " pics from " + vecFile);

            int lines = 0;
            String line, line0 = null, lineN = null;
            int nfields = -1;

            BufferedReader in = null;

            int done = 0;

            try {

                in = new BufferedReader(new FileReader(vecFile));

                while ((line = in.readLine()) != null) {

                    lines++;

                    line = line.trim();
                    lineN = line;
                    String[] fields = line.split(" ");

                    if (nfields == -1) {

                        line0 = line;

                        nfields = fields.length;
                        if (nfields < 3) {
                            err("Fields < 3: [" + line + "]");
                        }
                        int dimension = nfields - 2;

                        pout("-- vec size: " + dimension);

                        if (!dimTrack.add(dimension)) {
                            err("(duplicate dimension: " + dimension + ": " +
                                        vecFile);
                        }

                    } else if (nfields != fields.length) {
                        err("Bad line: [" + line + "]");
                    }

                    // scheme where ':' for archive:seq
                    String[] ss = fields[0].split(":");
                    int arch = Integer.parseInt(ss[0]);
                    String id = new ID(arch, ss[1]).id;

                    Picture pic = map.get(id);
                    if (pic == null) {
                        continue;
                    }

                    //String column = "wow";
                    if ("l".equals(fields[1])) {
                        pic.vec_l_fields = fields;
                    } else if ("r".equals(fields[1])) {
                        pic.vec_r_fields = fields;
                    } else {
                        err("Expected l or r: " + fields[1]);
                    }

                    done++;
                }
            } catch (Exception e) {
                err("Reading vecs: " + e);
            } finally {
                if (in != null) {
                    try { in.close(); } catch (Exception ignore) {}
                }
            }

            if (done != 2 * pics.size()) {
                err("Got vecs: " + done + " needed 2x" + pics.size());
            }

            // now update db

            int perproc = pics.size() / nprocs;

            // final for threads
            final String left_column = "left_" + (nfields-2);
            final String right_column = "right_" + (nfields-2);

            Thread[] threads = new Thread[nprocs];

            for (int i=0; i<nprocs; i++) {

                int start = i * perproc;
                int end = (i == nprocs-1) ? pics.size() : start + perproc;

                final List<Picture> myPics = new ArrayList<Picture>();
                for (int j=start; j<end; j++) {
                    myPics.add(pics.get(j));
                }

                threads[i] = new Thread(new Runnable() {

                    public void run() {

                        // per-thread workspace

                        Connection conn = null;
                        Statement stmt = null;

                        try {
                            //Class.forName("com.mysql.jdbc.Driver").newInstance();
                            conn = DriverManager.getConnection("jdbc:postgresql:pr",
                                                        "pr", "@@pr");

                            conn.setAutoCommit(false);

                            stmt = conn.createStatement();

                            for (int k=0; k<myPics.size(); k++) {

                                Picture pic = myPics.get(k);

                                updateVectorColumn(stmt, left_column, null, pic.id,
                                                                  null, pic.vec_l_fields, 2);
                                updateVectorColumn(stmt, right_column, null, pic.id,
                                                                  null, pic.vec_r_fields, 2);

                                if (k % 100 == 0) {
                                    conn.commit();
                                }
                            }
                            conn.commit();

                        } catch (Exception e) {
                            e.printStackTrace();
                            err("Updating: " + e);
                        } finally {
                            closeSQL(stmt);
                            closeSQL(conn);
                        }
                    }
                });
                threads[i].start();
            }
            try {
                for (Thread t : threads) {
                    t.join();
                }
            } catch (InterruptedException ie) {
                pout("Interrupted, used to exit, fingers crossed!: " + ie);
                //System.exit(1);
            }
        }
    }

    /**
     **  updateHistoVecs - using base_dir + files from db,
     **                 read <base_dir>/<archive_num>/<hist_file>
     **  pgvector: alter table pr.picture add column xxx vector(3);
     **  HACK: if dim==1984, assuming 256, 1728 split
     **         for greyscale+saturation, rgb12.
     **/

    static void updateHistoVecs(String column, File base_dir, List<Picture> pics, int nprocs) {

        int perproc = pics.size() / nprocs;

        Thread[] threads = new Thread[nprocs];

        for (int i=0; i<nprocs; i++) {

            int start = i * perproc;
            int end = (i == nprocs-1) ? pics.size() : start + perproc;

            final List<Picture> myPics = new ArrayList<Picture>();
            for (int j=start; j<end; j++) {
                myPics.add(pics.get(j));
            }

            threads[i] = new Thread(new Runnable() {

                public void run() {

                    // per-thread workspace

                    Connection conn = null;

                    try {
                        //Class.forName("com.mysql.jdbc.Driver").newInstance();
                        conn = DriverManager.getConnection("jdbc:postgresql:pr",
                                                      "pr", "@@pr");

                        updateHistoVecs(conn, myPics, column, base_dir);

                    } catch (Exception e) {
                        e.printStackTrace();
                        err("Exiting: " + e);
                    } finally {
                        DaoBase.closeSQL(conn);
                    }
                }
            });
            threads[i].start();
        }
        try {
            for (Thread t : threads) {
                t.join();
            }
        } catch (InterruptedException ie) {
            pout("Interrupted thread joins, used to exit, fingers crossed!: " + ie);
            //System.exit(1);
        }
    }

    /**
     **  updateImagenetVecs - using base_dir + ids from db,
     **                 read <base_dir>/<archive_num:seq> files.
     **  pgvector: alter table pr.picture add column xxx vector(size);
     **
     **     Model       vecs.size
     **     VGG16       25087  -> use averaged 512 block
     **/

    private static void updateImagenetVecsFromDir(String column,
                                                int baseDim, int[] folds, File base_dir,
                                                List<Picture> pics, int nprocs) {

        int perproc = pics.size() / nprocs;

        Thread[] threads = new Thread[nprocs];

        for (int i=0; i<nprocs; i++) {

            int start = i * perproc;
            int end = (i == nprocs-1) ? pics.size() : start + perproc;

            final List<Picture> myPics = new ArrayList<Picture>();
            for (int j=start; j<end; j++) {
                myPics.add(pics.get(j));
            }

            threads[i] = new Thread(new Runnable() {

                public void run() {

                    // per-thread workspace

                    Connection conn = null;

                    try {
                        //Class.forName("com.mysql.jdbc.Driver").newInstance();
                        conn = DriverManager.getConnection("jdbc:postgresql:pr",
                                                      "pr", "@@pr");

                        updateImagenetVecsFromDir(conn, column, baseDim, folds, myPics, base_dir);

                    } catch (Exception e) {
                        e.printStackTrace();
                        err("Exiting: " + e);
                    } finally {
                        DaoBase.closeSQL(conn);
                    }
                }
            });
            threads[i].start();
        }
        try {
            for (Thread t : threads) {
                t.join();
            }
        } catch (InterruptedException ie) {
            pout("Interrupted thread joins, used to exit, fingers crossed!: " + ie);
            //System.exit(1);
        }
    }

    /*
    **  updateVectors - cmdline function,
    **      normally called by proj/import/,
    **      called by proj/update/ for vector dev.
    **
    **      Threaded.
    **
    **  Exit on error.
    */

    public static String updateVectorsUsage() {
        return
         "       [prog] histogram <column> <dir>\n" +
         "       [prog] imgvecs <baseDim> <folds,s,> <column> <dir>\n" +
         "       [prog] pairvecs <file>\n";
    }

    /*
    **  checkVectorArgs() instantiate/check dirs/files
    */
    public static void checkVectorArgs(String[] args, String line) {

        String imgDescPath = ConfigUtil.compileProperty("image.desc.dir");
        if (imgDescPath == null) {
            err("PictureDao.checkVectorArgs: no image.desc.dir");
        }

        if ("histogram".equals(args[0])) {

            if (args.length != 3) {
                err("PictureDao.checkVectorArgs: histogram: expected <column> <dir>: " + line);
            }

            args[2] = imgDescPath + "/" + args[2];
            MiscUtil.checkDir(args[2]);

        } else if ("imgvecs".equals(args[0])) {

            if (args.length != 5) {
                err("PictureDao.checkVectorArgs: imgvecs: expected <baseDim> <folds,s,> <column> <dir>: " + line);
            }

            args[4] = imgDescPath + "/" + args[4];
            MiscUtil.checkDir(args[4]);

        } else if ("pairvecs".equals(args[0])) {

            if (args.length != 2) {
                err("PictureDao.checkVectorArgs pairvecs: expected <vecs_file>: " + line);
            }
            String pairVecsPath = ConfigUtil.compileProperty("pair.vecs.dir");
            if (pairVecsPath == null) {
                err("No pair.vecs.dir in phobrain_local/build.properties for: " + line);
            }
            if (!args[1].endsWith(".vecs")) {
                pout("warning: pairvecs file not ending in .vecs: " + line);
            }

            args[1] = pairVecsPath + "/" + args[1];
            MiscUtil.checkFile(args[1]);

        }
    }

    final static String DRIVER = "org.postgresql.Driver";

    public static void updateVectors(String[] args, List<Picture> pics) {

        pout("PictureDao.updateVectors:");
        pout("    args=[" + String.join(" ", args) + "]");
        pout("    pics: " + pics.size());
        pout("Starting in 5 seconds");
        try { Thread.sleep(5000); } catch (Exception ignore) {}

        long t0 = System.currentTimeMillis();

        try {
            Class.forName(DRIVER);
        } catch (Exception e) {
            err("Getting " + DRIVER + ": " + e);
        }

        int nprocs = MiscUtil.getProcs();

        int perproc = pics.size() / nprocs;

        if (perproc < 5) { // arbitrary
            nprocs = 1;
        }

        if ("histogram".equals(args[0])) {

            String hist_usage =
                       "histogram: expected column and ML .vecs file";

            if (args.length != 3) {
                err(hist_usage);
            }

            String column = args[1];

            File histDir = new File(args[2]);
            if (!histDir.isDirectory()) {
                err(hist_usage + ": " + args[2]);
            }

            updateHistoVecs(column, histDir, pics, nprocs);

        } else if ("imgvecs".equals(args[0])) {

            if (args.length != 5) {
                err("imgvecs: expected 5 args");
            }

            // for checking; VGG16 is 512

            int baseDim = Integer.parseInt(args[1]);

            // folds, 0==none

            String[] ss = args[2].split(",");
            int[] folds = new int[ss.length];
            for (int i=0; i<folds.length; i++) {
                folds[i] = Integer.parseInt(ss[i]);
            }

            String column = args[3];
            String dirname = args[4];

            File dir = new File(dirname);
            if (!dir.isDirectory()) {
                err("imgvecs: not a dir: " + dirname);
            }

            updateImagenetVecsFromDir(column, baseDim, folds, dir, pics, nprocs);

        } else if ("pairvecs".equals(args[0])) {

            // .vecs file(s) w/ 'l'/'r' latent vecs for each pic
            //  size in 2..12 determines column name (2024_01)

            String vecs_usage = "PictureDao.updateVectors: pairvecs: expected ML .vecs file(s): " +
                                Arrays.toString(args);

            if (args.length < 2) {
                err("arg count < 2: " + vecs_usage);
            }

            List<File> files = new ArrayList<>();
            for (int i=1; i<args.length; i++) {
                String fname = args[i];
                if (fname == null) {
                    if (i == args.length-1) {
                        break;
                    }
                    err("Null arg");
                }

                if (!fname.endsWith(".vecs")) {
                    err("Not .vecs: " + fname + "  " + vecs_usage);
                }
                File vecsFile = new File(fname);
                if (!vecsFile.exists()) {
                    err("No file: " + fname + ": " + vecs_usage);
                }

                files.add(vecsFile);
            }

            updatePairVecsFromFiles(files, pics, nprocs);

        } else {

            err("Expected args[0] in histogram,imgvecs,pairvecs: " + args[0]);

        }

        long t1 = System.currentTimeMillis();

        pout("PictureDao.updateVectors." + args[0] +
                ": " + pics.size() + " done in " +
                ((t1-t0)/1000) + " sec by " + nprocs + " procs");

    }
}
