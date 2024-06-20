package org.phobrain.db.pairs;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  Pairs/cmdline - build v or h pairs table
 **                  for db import (postgres):
 **         pr.pairs_[vh]_dump.sql_body
 **         pr.pairs_[vh]_dump.sql_tail
 **
 **     These tables are used for comparing any 2 pics;
 **     pr.pairtop_ tables are faster but have only top pairs.
 */

import org.phobrain.util.Stdio;
import org.phobrain.util.HashCount;
import org.phobrain.util.ListHolder;
import org.phobrain.util.ConfigUtil;
import org.phobrain.util.ColorUtil;
import org.phobrain.util.KwdUtil;
import org.phobrain.util.MiscUtil;
import org.phobrain.util.PairsBin;
import org.phobrain.util.SymPairsBin;

import org.phobrain.db.record.Picture;
import org.phobrain.db.record.Keywords;
import org.phobrain.db.record.PicPicKwd;
import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.PictureDao;
import org.phobrain.db.dao.PairDao;
import org.phobrain.db.dao.KeywordsDao;
import org.phobrain.db.dao.PicPicKwdDao;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverManager;

import java.io.File;
import java.io.PrintStream;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Date;

public class Pairs extends Stdio {

    final static boolean INSERT = true;

    // output files; IMAGE_DESC_DIR from cmd, inserted in front of others
    static String IMAGE_DESC_DIR = "unset";

    static String PAIR_OUT_V_BODY = "pr.pairs_v_dump.sql_body";
    static String PAIR_OUT_V_TAIL = "pr.pairs_v_dump.sql_tail";

    static String PAIR_OUT_H_BODY = "pr.pairs_h_dump.sql_body";
    static String PAIR_OUT_H_TAIL = "pr.pairs_h_dump.sql_tail";

    static String HISTO_PAIRTOP_OUT_V = "../histogram/pairs_uniq_v";
    static String HISTO_PAIRTOP_OUT_H = "../histogram/pairs_uniq_h";

    static String AB_CTR_RADII = "center_radii_AB";
    static String AB_CTR_ANG = "center_ang_AB";
    static String RGB_CTR_RADII = "center_radii_RGB";

    private static List<String> ids = null;
    private static List<BigPicture> pic_list = new ArrayList<>();

    private static void usage(String msg) {

        System.err.println(
            "Usage: [prog] <v|h> <home> -all|<archive1 archive2 ..> \n" +
                   "\t [<col_type col_name col_dir1] [col_dir2]> ..");

        if (msg != null) {
            System.err.println("\t" + msg);
        }
        err("That's it.");
    }

    private static PrintStream pairOut = null;  // pr.pairs_[vh]_dump.sql
    private static PrintStream pairtopOut = null; // ../histogram/pairs_uniq_[vh]

    private static long start_time = System.currentTimeMillis();
    private static long write_time = 0;

    static boolean debugPairs = false;

    private static class Col {

        String type;
        String name;

        int ncols;

        Col(String type, String name) {

            this.type = type;
            this.name = name;
            ncols = 1;
            /*
            if ("posneg".equals(type)) {
                ncols = 2; // 1 pos with 1 dir, 1 neg having 1+ dirs
            } else if (!"avg".equals(type)) {
                usage("Unexpected column type: " + type + " for name " + name);
            }
            */
        }

        List<File> dirs = new ArrayList<>();

        private Boolean isV(String path) {

            String[] elts = path.split("/");
            String end = elts[elts.length-1];

            if (path.contains("_v/")  ||  path.contains("/v_")) {
                return true;
            }
            if (end.startsWith("pp")  &&  end.endsWith("v")) {
                return true;
            }
            if (path.contains("_h/")  ||  path.contains("/h_")) {
                return false;
            }
            if (end.startsWith("pp")  &&  end.endsWith("h")) {
                return false;
            }
            usage("Pairs.Col.isV: Path must contain 'pp*[vh]', '_[vh]b/' or '_[vh]/': " + path);
            return null; //for compiler
        }

        void addPath(String path) {

            if (vertical != isV(path)) {
                usage("Inconsistent v/h paths: " + name);
            }

            File f = new File(path);

            if (!f.exists()) {
                usage("Path does not exist: " + path);
            }
            if (!f.isDirectory()) { // if a single file, 'avg' passes thru
                usage("Path not a directory: " + path);
            }

            dirs.add(f);

            /*
            if (!"posneg".equals(type)  &&  dirs.size() > ncols) {
                usage("Too many paths for type " + type + ": needs " + ncols +
                       ": col name " + name);
            }
            */
        }

        PairsBin pairsBin = null;

        // pairs expects an averaged .pb PairsBin file in
        //      the named dir. enforcing only one .pb since
        //      n^2 calc might as well be right.

        void initPairsBin() {

            // pre-checked to be a dir

            File all_dir = dirs.get(0);

            String[] list = all_dir.list();
            String dotPB = null;
            for (String fname : list) {
                if (fname.endsWith(".pb")) {
                    if (dotPB != null) {
                        err(">1 .pb file in " + all_dir);
                    }
                    dotPB = fname;
                }
            }
            if (dotPB == null) {
                err("No .pb file in " + all_dir.getPath());
            }

            dotPB = all_dir.getPath() + "/" + dotPB;

            pairsBin = PairsBin.load(dotPB, ids.size());
        }

        long getmillis = 0;

        void getPairVals(int o1, int o2, double[] result) {

            long t0 = System.currentTimeMillis();

            pairsBin.getPairVals(o1, o2, result);

            getmillis += (System.currentTimeMillis()-t0);

            if (debugPairs) {
                String id1 = pic_list.get(o1).p.id;
                String id2 = pic_list.get(o2).p.id;
                pout("vals for " + o1 + " " + id1 + " " + o2 + " " + id2 + "  " + result[0] + " " + result[1]);
                if (o2 > 5) err("... debugPairs outie");
            }
        }

        List<String> db_cols = new ArrayList<>();
        List<String> db_col_defs = new ArrayList<>();

        void makeDbCols() {

            if ("avg".equals(type)) {

                db_col_defs.add("a_" + name + "12    bigint");
                db_col_defs.add("a_" + name + "21    bigint");
                db_cols.add("a_" + name + "12");
                db_cols.add("a_" + name + "21");

            } else if ("neg".equals(type)) {

                db_col_defs.add("n_" + name + "12    bigint");
                db_col_defs.add("n_" + name + "21    bigint");
                db_cols.add("n_" + name + "12");
                db_cols.add("n_" + name + "21");

            } else if ("pos".equals(type)) {

                db_col_defs.add("p_" + name + "12    bigint");
                db_col_defs.add("p_" + name + "21    bigint");
                db_cols.add("p_" + name + "12");
                db_cols.add("p_" + name + "21");

            } else {
                err("db cols: unk type: " + type);
            }
        }
    }

    static List<Col> cols = new ArrayList<>();

    private static final Set<String> TYPES = new HashSet<>();
    static {
        TYPES.add("avg");
        TYPES.add("neg");
        TYPES.add("pos");
    };

    static Boolean vertical = null;

    public static void main(String[] args) {

        if (args.length < 3) {
            usage("Expected >= 3 args, got: " + Arrays.toString(args));
        }

        if ("v".equals(args[0])) {
            vertical = true;
        } else if ("h".equals(args[0])) {
            vertical = false;
        } else {
            usage("Expected <v|h>, got: " + args[0]);
        }

        IMAGE_DESC_DIR = args[1];
        if (! new File(IMAGE_DESC_DIR).isDirectory()) {
            usage("Not a dir: " + IMAGE_DESC_DIR);
        }

        Connection conn = null;

        try {

            //Class.forName("com.mysql.jdbc.Driver").newInstance();
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection("jdbc:postgresql:pr",
                                               "pr", "@@pr");
            conn.setAutoCommit(false);

        } catch (Exception e) {

            e.printStackTrace();
            err("Exiting");

        }

        List<Integer> archives = new ArrayList<>();

        int narg = 2;

        if ("-all".equals(args[narg])) {

            narg++;

            try {

                archives.addAll(PictureDao.getArchives(conn));

            } catch (Exception e) {

                e.printStackTrace();
                err("Exiting");

            }

        } else {

            // following ints are archives, til not-int treated as column type

            for (; narg<args.length; narg++) {
                try {
                    archives.add(Integer.parseInt(args[narg]));
                } catch (NumberFormatException nfe) {
                    // better be a TYPE
                    break;
                }
            }
        }

        if (archives.size() == 0) {
            usage("No archive list");
        }
        Collections.sort(archives);

        pout("Archives: " + archives.size());
        pout("\t" + Arrays.toString(archives.toArray()));

        int real_cols = 0;
        int real_dirs = 0;
        Set<String> colset = new HashSet<>();

        if (narg >= args.length-1) {

            pout("(No ML columns defined)");

        } else {

            while (narg < args.length) {

                if (narg + 1 > args.length-1) {
                    usage("No name for <type> <name> at arg " + narg + ": " +
                          args[narg]);
                }
                Col col = new Col(args[narg], args[narg+1]);

                if (!colset.add(col.name)) {
                    err("Duplicate column: " + col.name);
                }
                narg += 2;
                if (narg + col.ncols > args.length) {
                    usage("Need " + col.ncols +
                          " dirs for type=" + col.type +
                          " (name=" + col.name + ")");
                }
                pout("narg/args " + narg + "/" + args.length);
                while (narg < args.length  &&  !TYPES.contains(args[narg])) {
                    pout("narg/path " + narg + "/" + args[narg]);
                    col.addPath(args[narg++]);
                }

                cols.add(col);
                real_cols += col.ncols;
                real_dirs += col.dirs.size();
            }

            pout("Pairs: orient: " + vertical +
                    " ML columns: logical: " + cols.size() +
                    " actual: " + real_cols +
                    " dirs: " + real_dirs);
        }

        try {
pout("gooo archives null=" + (archives==null) + " vertical " + vertical);
            loadFromDB(conn, archives, vertical);

            //calcConstants();

            // pairs/pairtop tbl
            pushDBMeta(conn, vertical);

            conn.close();

        } catch (Throwable t) {
            t.printStackTrace();
            err("It has come to this: " + t);
        }
    }

    private static class BigPicture implements Comparable {

        BigPicture() {
        }

        int order;

        Picture p;
        Map<String, Keywords> coderKwds; // directly from dao
        Map<String, Double> coderKwdConstants = new HashMap<>();
        Map<String, Set<String>> coderKwdHash = new HashMap<>();

        @Override
        public int compareTo(Object o) {
            BigPicture bp = (BigPicture) o;
            if (p.archive < bp.p.archive) return -1;
            if (p.archive > bp.p.archive) return 1;
            if (p.sequence < bp.p.sequence) return -1;
            if (p.sequence > bp.p.sequence) return 1;

            // make 123 come before 123a

            if (p.id.length() < bp.p.id.length()) return -1;
            if (p.id.length() > bp.p.id.length()) return 1;

            // make 123a come before 123b

            return p.id.compareTo(bp.p.id);

        }
    }

    private static Map<BigPicture, String> picMap = new TreeMap<>();

    private static Map<String, BigPicture> pic_idx = new HashMap<>();
    private static Map<String, Map<String, Keywords>> kwds_idx = new HashMap<>();
    //private static Set<String> kwdCoders = new HashSet<>();
    private static String[] kwdCoders = { "m_nogeom", "m_geom" };

    private static HashCount counts = null;

    private static void loadFromRealImgOrient(Connection conn,
                                                List<Integer> archives,
                                                boolean vertical)
            throws Exception {

    }

    private static void loadFromDB(Connection conn, List<Integer> archives,
                                                    boolean vertical)
            throws Exception {

        pout("Loading the pr.picture table");
        //      .. and keywords table someday again?

        int a[] = new int[archives.size()];
        for (int i=0; i<archives.size(); i++) {
            a[i] = archives.get(i);
        }

        pout("Loading archives: " + Arrays.toString(a) + "\n");

        Set<Picture> set = new TreeSet<>();
        set.addAll(PictureDao.getPicturesByArchives(conn, a, vertical));
        List<Picture> pics = new ArrayList<>();
        pics.addAll(set);
        pout("Got pics: " + pics.size() + ":");
        for (int i=0;i<5;i++) pout("\t" + pics.get(i).id);
        pout("\t...");
        for (int i=pics.size()-5;i<pics.size();i++) pout("\t" + pics.get(i).id);
        pout("---");

        loadPics(conn, pics, true);
    }

    private static void loadFromDB(Connection conn, String fname)
            throws Exception {

        pout("Loading ids from file: " + fname);

        ids = ConfigUtil.loadViewIds(fname);

        List<Picture> pics = new ArrayList<>();
        for (String id : ids) {
            Picture p = PictureDao.getPictureById(conn, id);
            if (p == null) {
                throw new RuntimeException("No pic for " + id +
                                           " from " + fname);
            }
            pics.add(p);
        }
        loadPics(conn, pics, true);
    }

    private static void loadPics(Connection conn, List<Picture> pics,
                                 boolean getKwds)
            throws Exception {

        if (getKwds) {
            pout("SKIPPING KWDS since not in use");
            getKwds = false;
        }

        Picture p0 = pics.get(0);
        Picture pN = pics.get(pics.size()-1);

        for (Picture p : pics) {

            BigPicture bp = new BigPicture();
            bp.p = p;

            pic_list.add(bp);

            picMap.put(bp, p.id); // p.id not used
            pic_idx.put(p.id, bp); // legacy convenience

            if (getKwds) {
                // get all keywords
                bp.coderKwds = kwds_idx.get(p.id);
                if (bp.coderKwds == null) {
                    bp.coderKwds = KeywordsDao.getKeywords(conn, p.id);
                    kwds_idx.put(p.id, bp.coderKwds);
/*
                    for (Map.Entry pair : bp.coderKwds.entrySet()) {
                        String key = (String)pair.getKey();
                        if ("l".equals(key)  ||  "b".equals(key)) {
                            continue;
                        }
                        kwdCoders.add((String)pair.getKey() + "_nogeom");
                        kwdCoders.add((String)pair.getKey() + "_geom");
                    }
*/
                }
            }
        }
        pout("Got pics/kwds");

/*
        // populate pic_list, ids in canonical order

        for (Map.Entry pair : picMap.entrySet()) {
            BigPicture bp = (BigPicture) pair.getKey();
            pic_list.add(bp);
        }
*/

        ids = new ArrayList<String>();

        for (int i=0; i<pic_list.size(); i++) {
            BigPicture bp = pic_list.get(i);
            bp.order = i;
            ids.add(bp.p.id);
        }

        if (ids.size() != pic_list.size()) {
            err("N(ids)=" + ids.size() + " N(pic_list)=" + pic_list.size());
        }

        pout("Total Pics: " + pics.size() +
                " ids: " + ids.size() +
                " " + pic_list.get(0).p.id +
                " .. " + pic_list.get(pic_list.size()-1).p.id);

        if (getKwds) {

            // weights per coder are calced over all pics,
            //   not just the selected ones

            counts = KeywordsDao.getCoderCounts(conn);
            pout("HashCount size " + counts.size());
            pout("Loaded");
        }
    }

/*
    private static void calcConstants() {

        pout("Calc const");
        // calc the per-pic per-coder kwd constants

        // populate coderKwdHash and calc pic's kwd const
        //  for later kwd/kwd calc, using only kwds w/ >1 pic

        for (BigPicture bp : pic_list) {

            for (Map.Entry pair : bp.coderKwds.entrySet()) {

                Keywords k = (Keywords) pair.getValue();

if ("l".equals(k.coder)) continue;
if ("b".equals(k.coder)) continue;

                int tot_geom = 0;
                int tot_nogeom = 0;
                double denom5_geom = 0.0d;
                double denom5_nogeom = 0.0d;
                Set<String> hset_geom = new HashSet<>();
                Set<String> hset_nogeom = new HashSet<>();

                for (String kwd : k.keywords.split(" ")) {
                    int ct = counts.getCount(kwd);
                    if (ct < 1) {
                        err("ct/k " + ct + " " + kwd);
                    }
                    if (ct == 1) {
                        continue;  // TODO maybe also if 1 for this run?
                    }

                    if (KwdUtil.stripGeom(kwd) == null) {

                        // geom kwds

                        tot_geom++;
                        hset_geom.add(kwd);
                        denom5_geom += KwdUtil.kwdScale(5, ct);

                    } else {

                        // geom stripped

                        tot_nogeom++;
                        hset_nogeom.add(kwd);
                        denom5_nogeom += KwdUtil.kwdScale(5, ct);
                    }
                }
//pout("SIzes: " + hset.size() + " " + hset_nogeom.size());

                bp.coderKwdHash.put(k.coder + "_geom", hset_geom);
                bp.coderKwdHash.put(k.coder + "_nogeom", hset_nogeom);
                double kwdConstant_geom = 0.0d;
                double kwdConstant_nogeom = 0.0d;
                if (tot_geom > 0) {
                    kwdConstant_geom = 1.0d / denom5_geom;
                }
                if (tot_nogeom > 0) {
                    kwdConstant_nogeom = 1.0 / denom5_nogeom;
                }
                bp.coderKwdConstants.put(k.coder + "_geom", kwdConstant_geom);
                bp.coderKwdConstants.put(k.coder + "_nogeom", kwdConstant_nogeom);
            } // coder kwds

        } // BigPicture

    }
*/

    private static double[] avgRGB = { 0.0, 0.0, 0.0 };
    private static double[] avgAB = { 0.0, 0.0 };

    private static void calcAvgColors(boolean vertical) throws Exception {

        for (int i=0; i<pic_list.size(); i++) {
            BigPicture bp = pic_list.get(i);
            avgRGB[0] += bp.p.r;
            avgRGB[1] += bp.p.g;
            avgRGB[2] += bp.p.b;

            avgAB[0] += bp.p.aa;
            avgAB[1] += bp.p.bb;
        }
        for (int i=0; i<3; i++) {
            avgRGB[i] /= pic_list.size();
        }
        avgAB[0] /= pic_list.size();
        avgAB[1] /= pic_list.size();

        try {
            String routname = IMAGE_DESC_DIR + "/" + AB_CTR_RADII +
                                (vertical ? "_v" : "_h");
            String routangname = IMAGE_DESC_DIR + "/" + AB_CTR_ANG +
                                (vertical ? "_v" : "_h");
            String rout2name = IMAGE_DESC_DIR + "/" + RGB_CTR_RADII +
                                (vertical ? "_v" : "_h");

            pout("Opening angle-related files (used by update):" +
                    "\n\t" + routname +
                    "\n\t" + routangname +
                    "\n\t" + rout2name);

            PrintStream rout = new PrintStream(routname);
            PrintStream routang = new PrintStream(routangname);
            PrintStream rout2 = new PrintStream(rout2name);

            for (int i=0; i<pic_list.size(); i++) {
                BigPicture bp = pic_list.get(i);
                if (i<pic_list.size()-1) {
                    BigPicture bp2 = pic_list.get(i+1);
                    if (bp2.p.id.equals(bp.p.id)) {
                        continue;
                    }
                }
                double aac = bp.p.aa - avgAB[0];
                double bbc = bp.p.bb - avgAB[1];
                double len = Math.sqrt(aac * aac + bbc * bbc);
                int iLen = (int) (10000.0 * len);
                rout.println(bp.p.id + " " + iLen);
                double ang = Math.toDegrees(Math.atan2(aac, bbc));
                int iAng = (int)Math.round(ang);
                routang.println(bp.p.id + " " + iAng);

                double rc = bp.p.r - avgRGB[0];
                double gc = bp.p.g - avgRGB[1];
                double bc = bp.p.b - avgRGB[2];
                double len2 = Math.sqrt(rc * rc + gc * gc + bc * bc);
                int iLen2 = (int) (1000.0 * len2);
                rout2.println(bp.p.id + " " + iLen2);
            }
            rout.close();
            routang.close();
            rout2.close();
            pout("Angle/distance files written");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printDumpHeader(PrintStream out, String tbl_name, int procs)
            throws Exception {

        out.println("--\n-- Postgres dump file created by phobrain\n--");
        out.println("SET statement_timeout = 0;");
        out.println("SET lock_timeout = 0;");
        out.println("SET client_encoding = 'UTF8';");
        out.println("SET standard_conforming_strings = on;");
        out.println("SET check_function_bodies = false;");
        out.println("SET client_min_messages = warning;");
        out.println("SET search_path = pr, pg_catalog;");
        out.println("SET default_tablespace = '';");
        out.println("SET default_with_oids = false;");
        out.println("-- Name: " + tbl_name +
                            "; Type: TABLE; Schema: pr; Owner: pr;");


        out.println("\\echo 'IGNORE possible Error: [relation xxx does not exist] " +
                    "on (TRUNCATE TABLE " + tbl_name + "'");

        //  ERROR:  relation "pr.pairs_h" does not exist
        out.println("TRUNCATE TABLE " + tbl_name + ";");

        out.println("DROP TABLE IF EXISTS " + tbl_name + ";");

        out.println("CREATE TABLE " + tbl_name + " (");
        out.println("    id1 character varying(10),");
        out.println("    id2 character varying(10),");
        // out.println("    kwd boolean,");
        out.println("    angle integer,");  // AB difference angle
        out.println("    ab_angle integer,"); // AB angle w/ avg_pos
        out.print(  "    rgb_angle integer"); // RGB angle w/ avg_pos

        // continue 'out' while building COPY stmt

        StringBuilder copy = new StringBuilder();
        if (procs == 1) {
            copy.append("-- rows ordered for checking, run slow / single-proc\n");
        } else {
            copy.append("-- rows not ordered since generated in parallel\n")
                .append("-- use procs=1 to check output. procs=")
                .append(procs)
                .append("\n");
        }

        copy.append("COPY " + tbl_name + " ( id1, id2, " +    // kwd,
                             "angle, ab_angle, rgb_angle");

        for (int i=0; i<cols.size(); i++) {

            Col col = cols.get(i);

            for (int j=0; j<col.db_cols.size(); j++) {

                String db_col_def = col.db_col_defs.get(j);
                String db_col = col.db_cols.get(j);

                out.print(",\n    " + db_col_def);

                copy.append(",").append(db_col);
            }
        }
        copy.append(" ) FROM stdin;");

        out.println("\n);");
        out.println("ALTER TABLE " + tbl_name + " OWNER TO pr;");

        out.println("\\timing on");

        out.println(copy.toString());
    }

    private static void printDumpTail(PrintStream out, String tbl_name)
            throws Exception {

        // end the data - SKIP for separate .sql_body, .sql_tail files
        //out.println("\\.");

        //out.println("ALTER TABLE " + tbl_name + " SET (parallel_workers = 8);");

        String ix_name = tbl_name.replace("pr.", "");

        out.println("CREATE INDEX " + ix_name + "_1_idx ON " +
                                      tbl_name + " USING btree (id1);");
        out.println("CREATE INDEX " + ix_name + "_2_idx ON " +
                                      tbl_name + " USING btree (id2);");

        if (cols.size() > 0) {

            pout("=== Adding d0 indexes");
            out.println("CREATE INDEX " + ix_name + "_d012_idx ON " +
                                          tbl_name + " USING btree (a_d012);");
            out.println("CREATE INDEX " + ix_name + "_d021_idx ON " +
                                          tbl_name + " USING btree (a_d021);");
        }

        out.println("REVOKE ALL ON TABLE " + tbl_name + " FROM PUBLIC;");
        out.println("REVOKE ALL ON TABLE " + tbl_name + " FROM pr;");
        out.println("GRANT  ALL ON TABLE " + tbl_name + " TO pr;");
    }

    private static String pair_fname_body = null;
    private static String pair_fname_tail = null;

    // top selections go in ../pairtop/tmp_uniq_[vh]
    private static String pairtop_fname = null;

    private static SymPairsBin spb_rgb_b0 = null;
    private static SymPairsBin spb_E94_b1 = null;
    private static SymPairsBin spb_E2k_b2 = null;

    //private static List<SymPairsBin> topSel_extra = new ArrayList<>();

    private static int lineNum = 0;

    private static void progress(int lineNum, long totLines, long t0) {

        double done = 100.0 * (double) lineNum / totLines;

        long t1 = System.currentTimeMillis();
        long soFar = t1 - t0;
        double tPerLine = (double)soFar / lineNum;
        double remainder = totLines - lineNum;
        long finish = t1 + (long)(remainder * tPerLine);

        String s = "\rdone: " + ((int)done) + "%%" +
                                "  expect: " + new Date(finish) +
                                "                       ";
        System.err.printf(s);
    }

    private static void pushDBMeta(Connection conn, boolean vertical)
            throws Exception {

        if (vertical) {

            pair_fname_body = PAIR_OUT_V_BODY;
            pair_fname_tail = PAIR_OUT_V_TAIL;

            pairtop_fname = HISTO_PAIRTOP_OUT_V;

        } else {

            pair_fname_body = PAIR_OUT_H_BODY;
            pair_fname_tail = PAIR_OUT_H_TAIL;

            pairtop_fname = HISTO_PAIRTOP_OUT_H;
        }

        if (new File(pair_fname_body).exists()) {
            err("Exists: " + pair_fname_body);
        }
        if (new File(pairtop_fname).exists()) {
            err("Exists: " + pairtop_fname);
        }

        pout("Calculating " +
            (vertical ? "v" : "h") +
            " for pics: " + pic_list.size());

        //long t0 = System.currentTimeMillis();

        calcAvgColors(vertical); // overwrites distance files

        int nViews = PictureDao.getMaxArchive(conn) + 1; // TODO be real

        //BlockingQueue<Integer> pQ = new LinkedBlockingQueue<Integer>(2);
        //ExecutorService pWorkers = null;

        for (Col col: cols) {
            col.makeDbCols();
            col.initPairsBin();
        }

        int procs = MiscUtil.getProcs();
        //procs=1;

        pairOut = new PrintStream(pair_fname_body);
        printDumpHeader(pairOut, "pr.pairs_" + (vertical ? "v" : "h"), procs);

        if (pairtop_fname == null) err("null pairtop_fname");
        pout("Opening " + pairtop_fname);
        pairtopOut = new PrintStream(pairtop_fname);

        pairtopOut.println("# uniq for histogram pairtop");
        pairtopOut.println("#  - generated by proj/pairs/");

        spb_rgb_b0 = new SymPairsBin(ids);
        spb_E94_b1 = new SymPairsBin(ids);
        spb_E2k_b2 = new SymPairsBin(ids);

                        // pairtopOut, "b0", 200, false); // false==topDown
                        // TopSelector(pairtopOut, "b1", 200, false); // false==topDown
                        // TopSelector(pairtopOut, "b2", 200, false); // false==topDown

        int npics = pic_list.size();
        long lpics = npics;
        long totLines = ((lpics * lpics) - lpics) / 2;

        pout("Pics: " + npics + " Pair lines: " + totLines + " Procs: " + procs);

        final long MOD = totLines / 100;
        final long t0 = System.currentTimeMillis();

        // pairs are iterated like a symmetric matrix,
        //      but AB,BA are collected at once (asym
        //      is only in ML), and stored in the
        //      AB row of the table.

        if (procs == 1) {

            for (int i=0; i<npics-1; i++) {

                BigPicture bp1 = pic_list.get(i);

/*
                pout("SEQ: " + bp1.p.archive + ":" + bp1.p.sequence +
                               " " +
                               (System.currentTimeMillis()-t1) + " " +
                               ((System.currentTimeMillis()-t0)/60000));
*/
                // extra checks in single-thread

                if (vertical != bp1.p.vertical) {
                    err("Shouldn't have loaded " + i + " " + bp1.p.id +
                        " vertical=" + bp1.p.vertical);
                }

                double[] b0_row = spb_rgb_b0.getRow(i);
                double[] b1_row = spb_E94_b1.getRow(i);
                double[] b2_row = spb_E2k_b2.getRow(i);

                for (int j=i+1; j<npics; j++) {

                    BigPicture bp2 = pic_list.get(j);

                    pairOut.print(handlePair(bp1, bp2, b0_row, b1_row, b2_row));

                    lineNum++;

                    if (lineNum % MOD == 0) {
                        progress(lineNum, totLines, t0);
                    }
                }
            }

        } else {

            // forgo ordering for parallel speedup

            BlockingQueue<String> pQ = new LinkedBlockingQueue<String>(procs * 20);

            Thread[] threads = new Thread[procs];

            int perproc = (int) (npics / (procs-1));

            int start = 0;

            for (int i=1; i<procs; i++) {

                // local vars used by threads need to be final
                final int proc = i;
                final BlockingQueue<String> tQ = pQ;
                final int start_pic = start;
                final int end_pic = (i==procs-1 ? npics-1 : start + perproc);

                start = end_pic + 1;

                //pout("thread / start/end " + i + " / " + start_pic + "/" + end_pic);

                threads[i] = new Thread(new Runnable() {

                    public void run() {

                        try {
                            StringBuilder sb = new StringBuilder();

                            for (int ipic=start_pic; ipic<end_pic; ipic++) {
                                //pout("proc, ipic " + proc + ", " + ipic);

                                BigPicture bp1 = pic_list.get(ipic);

                                double[] b0_row = spb_rgb_b0.getRow(ipic);
                                double[] b1_row = spb_E94_b1.getRow(ipic);
                                double[] b2_row = spb_E2k_b2.getRow(ipic);

                                for (int j=ipic+1; j<npics; j++) {

                                    BigPicture bp2 = pic_list.get(j);

                                    sb.append(handlePair(bp1, bp2, b0_row, b1_row, b2_row));
                                }

                                // sb.length()      real
                                // 256 * 1024^2     5m27.525s
                                // 500 * 1024^2     5m19.479s
                                // 1024^3           5m43.372s

                                if (sb.length() > 500 * 1024 * 1024) {
                                    tQ.put(sb.toString());
                                    sb.setLength(0);
                                }
                            }
                            if (sb.length() > 0) {
                                tQ.put(sb.toString());
                                sb.setLength(0);
                            }
                            tQ.put("");

                        } catch (Exception e) {
                            e.printStackTrace();
                            err("Pairs: " + e);
                        }
                    }
                });
                threads[i].start();
            }

            // start printing thread

            final BlockingQueue<String> tQ = pQ;
            final int calcers = procs - 1;
            threads[0] = new Thread(new Runnable() {

                public void run() {

                    try {
                        int ended = 0;
                        for (int k=0; k<ids.size()-1; k++) {
                            String s = tQ.take();
                            if (s.length() == 0) {
                                if (++ended == calcers) {
                                    break;
                                }
                                continue;
                            }
                            pairOut.print(s);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        err("Pairs: " + e);
                    }
                }
            });
            threads[0].start();

            for (Thread t : threads) {
                t.join();
            }
        }

        pout("DONE pairs");

        String flips = ColorUtil.flipSum(ids.size());
        if (flips != null) {
            pout(flips);
            // All w/ reset: ColorUtil.printFlips(int nIds)
        }


        if (pairOut != null) {
            pairOut.close();
            pout("Written: " + pair_fname_body);

            //pout("Writing: " + pair_fname_tail);
            pairOut = new PrintStream(pair_fname_tail);
            printDumpTail(pairOut, "pr.pairs_" + (vertical ? "v" : "h"));
            pairOut.close();
            pout("Written: " + pair_fname_tail);
        }

        if (pairtopOut != null) {

            spb_rgb_b0.writeTopPairs(pairtopOut, MiscUtil.getProcs(),
                            ids, "b0", 50, 50);

            spb_E94_b1.writeTopPairs(pairtopOut, MiscUtil.getProcs(),
                            ids, "b1", 50, 50);

            spb_E2k_b2.writeTopPairs(pairtopOut, MiscUtil.getProcs(),
                            ids, "b2", 50, 50);

            pairtopOut.close();
            pout("Written: " + pairtop_fname);
        }
        pout("done - see 0README about ../update/");
    }

    private static void handleKwds(BigPicture bp1, BigPicture bp2)
            throws Exception {

        // kwds for coders for the pair of pictures

        boolean hasKwd = false;
        int matchNum = 0;

        for (Map.Entry map_pair : bp1.coderKwdConstants.entrySet()) {

            String coder = (String) map_pair.getKey();
            Double kwdConst1 = (Double) map_pair.getValue();
            Double kwdConst2 = bp2.coderKwdConstants.get(coder);

            if (kwdConst2 != null) {

                // both pics coded by same coder

                // kwd match search vals from h1 in h2
                // mild optimization - loop on smaller set

                Set<String> kwdHash1 = bp1.coderKwdHash.get(coder);
                Set<String> kwdHash2 = bp2.coderKwdHash.get(coder);

//pout("hash sizes " + kwdHash1.size() + " " + kwdHash2.size());
                if (kwdHash1.size() == 0  || kwdHash2.size() == 0) {
                    // nogeom with only geom kwds or only unique kwd

                    // if (!coder.contains("nogeom")) {
                    //     throw new RuntimeException("empty hash, no nogeom" +
                    //               " coder " + coder + " " + id1 + " " + id2);
                    // }

                    //pout("Skip empty hash: " + coder);
                    continue; // next kwdCoder
                }

                Set<String> h1, h2;
                if (kwdHash1.size() > kwdHash2.size()) {
                    h1 = kwdHash2;
                    h2 = kwdHash1;
                } else {
                    h1 = kwdHash1;
                    h2 = kwdHash2;
                }

                double kwdMatch5 = 0.0;
                int matchCt = 0;

                for (String k : h1) {
                    if (h2.contains(k)) {
                        kwdMatch5 += KwdUtil.kwdScale(5, counts.getCount(k));
//pout("Match " + k + " " + counts.getCount(k) + " " + kwdMatch5);
                        matchCt++;
                    }
                }
                double ga = (double) matchCt /
                                        Math.sqrt(h1.size() * h2.size());
                int num = (int) (1000.0 * ga);
                boolean km = kwdMatch5 > 0.0;
                // nogeom==being conservative in color table
                if (coder.contains("nogeom")) {
                    // HACK - comparing counts in different schemes
                    //        will pick all over nogeom
                    // Not an issue as long as only using scheme 'm'
                    if (num > matchNum) {
                        matchNum = num;
                    }
                    if (km) {
                        hasKwd = true;
                    }
                }
                boolean sameScene = false;
                if (bp1.p.archive == bp2.p.archive  &&
                    bp2.p.sceneSequence == bp1.p.sequence) {
                    sameScene = true;
                }
//pout("QQQ " + matchCt + " " + kwdMatch5 + " " + km + " same " + sameScene);

                double SCALE = 1000000000.0d;
                double d_5_1 = SCALE * kwdMatch5 *
                                       bp2.coderKwdConstants.get(coder);
                double d_5_2 = SCALE * kwdMatch5 *
                                       bp1.coderKwdConstants.get(coder);
                // only save kwd if not same scene
                if (km  &&  !sameScene) {

                    // could switch to long for 64-bit
                    if (d_5_1 > Integer.MAX_VALUE  ||
                        d_5_2 > Integer.MAX_VALUE) {

                        pout("-- " + "   " + d_5_1 + " " + d_5_2 + "\n" +
                                       Integer.MAX_VALUE);

                        throw new RuntimeException("OVERFLOW " +
                                           bp1.p.id + "/" + bp2.p.id);
                    }
                    int v1 = (int) Math.round(d_5_1);
                    if (v1 == 0) v1 = 1;
                    int v2 = (int) Math.round(d_5_2);
                    if (v2 == 0) v2 = 1;
                }
            } // kconst != null
        }
    }

    private static String handlePair(BigPicture bp1, BigPicture bp2,
                                double[] b0_row, double[] b1_row, double[] b2_row)
            throws Exception {

        // boolean hasKwd = handleKwds(bp1, bp2);

        String orientation = "b"; // both
        if (bp1.p.vertical  &&  bp2.p.vertical) {
            orientation = "v";
        } else if (!bp1.p.vertical  &&  !bp2.p.vertical) {
            orientation = "h";
        } else {
            err("both");
        }

        // color distance vanilla RGB
        double dr = bp1.p.r - bp2.p.r;
        double dg = bp1.p.g - bp2.p.g;
        double db = bp1.p.b - bp2.p.b;

        double cdRGB = Math.sqrt(dr*dr + dg*dg + db*db);

        // color distance Delta E*  http://www.easyrgb.com/

        double dL = bp1.p.ll - bp2.p.ll;
        double dA = bp1.p.aa - bp2.p.aa;
        double dB = bp1.p.bb - bp2.p.bb;
        double cdEStar = Math.sqrt(dL*dL + dA*dA + dB*dB);

        // AB angle of difference vector
        double dAng = Math.toDegrees(Math.atan2(dB, dA));
        int iAng = (int) dAng;

        // angle w/ respect to center

        double aa1c = bp1.p.aa - avgAB[0];
        double bb1c = bp1.p.bb - avgAB[1];
        double aa2c = bp2.p.aa - avgAB[0];
        double bb2c = bp2.p.bb - avgAB[1];

        double t = aa1c * aa2c + bb1c * bb2c;
        double tl1 = Math.sqrt(aa1c * aa1c + bb1c * bb1c);
        double tl2 = Math.sqrt(aa2c * aa2c + bb2c * bb2c);
        int iAngc = (int)Math.toDegrees(Math.acos(t / (tl1*tl2)));

        // RGB angle from avg/center of space

        double r1c = bp1.p.r - avgRGB[0];
        double g1c = bp1.p.g - avgRGB[1];
        double b1c = bp1.p.b - avgRGB[2];

        double r2c = bp2.p.r - avgRGB[0];
        double g2c = bp2.p.g - avgRGB[1];
        double b2c = bp2.p.b - avgRGB[2];

        double dot = r1c * r2c  +  g1c * g2c  +  b1c * b2c;
        double l1 = Math.sqrt(r1c * r1c + g1c * g1c + b1c * b1c);
        double l2 = Math.sqrt(r2c * r2c + g2c * g2c + b2c * b2c);

        dAng = Math.toDegrees(Math.acos(dot / (l1 * l2)));
        int iAng2 = (int) dAng;

        // color distance Delta E 1994  http://www.easyrgb.com/

        double cdE94 = ColorUtil.cdE94(bp1.p.id, bp1.p.ll, bp1.p.aa, bp1.p.bb,
                                       bp2.p.id, bp2.p.ll, bp2.p.aa, bp2.p.bb);

        double cdE2k = ColorUtil.cdE2k(bp1.p.id, bp1.p.ll, bp1.p.aa, bp1.p.bb,
                                       bp2.p.id, bp2.p.ll, bp2.p.aa, bp2.p.bb);
if(Double.isNaN(cdE2k)) {
err("NAN at " + bp1.p.id + " " + bp2.p.id + "\n" +
"bp1 " + bp1.p.ll + " " + bp1.p.aa + " " + bp1.p.bb + "\n" +
"bp2 " + bp2.p.ll + " " + bp2.p.aa + " " + bp2.p.bb);
}
        // now it gets ugly

        double SCALEC = 1000.0d;
        cdEStar *= SCALEC; cdE94 *= SCALEC; cdE2k *= SCALEC;
        if (cdEStar > Integer.MAX_VALUE
            || cdE94 > Integer.MAX_VALUE
            || cdE2k > Integer.MAX_VALUE) {

            pout("cdEStar " + cdEStar);
            pout("cdE94 " + cdE94);
            pout("cdE2k " + cdE2k);
            throw new RuntimeException("OVERFLOW0");
        }

        // moved top RGB, E94 and E2k to ../pairtop/tmp_[vh]
        // to save size, since NN now performs default all-all rating.

        int rowi = bp1.order;
        int column = bp2.order - bp1.order - 1;

        // TODO - organize by row instead of by pair
        b0_row[column] = cdRGB;
        b1_row[column] = cdE94;
        b2_row[column] = cdE2k;

        // topSel_rgb.add(bp1.p.id, bp2.p.id, (int)cdRGB);
        // topSel_e94.add(bp1.p.id, bp2.p.id, (int)cdE94);
        // topSel_e2k.add(bp1.p.id, bp2.p.id, (int)cdE2k);

        StringBuilder sb = new StringBuilder();
        sb.append(bp1.p.id).append("\t")
          .append(bp2.p.id).append("\t")
          // .append(hasKwd).append("\t")
          .append(iAng).append("\t")
          .append(iAngc).append("\t")
          .append(iAng2).append("\t");

        //pout("DO " + id1 + " " + id2);

        // neural net pair files
        // NEW wiring for one averaged .pb per column

        double[] pairVals = new double[2];

        for (int ii=0; ii<cols.size(); ii++) {

            Col col = cols.get(ii);
            col.getPairVals(bp1.order, bp2.order, pairVals);
            sb.append((int)pairVals[0]).append("\t");
            sb.append((int)pairVals[1]).append("\t");

        }

        sb.deleteCharAt(sb.length()-1);
        sb.append("\n");

        long t0 = System.currentTimeMillis();
        write_time += System.currentTimeMillis() - t0;
        return sb.toString();
    }
/*
                double[] vals = col.agg.readLine();
                if (vals == null) {
                    err("premature end: " + col.agg.id);
                }
                for (int kk=0; kk<vals.length; kk++) {
                    //vals[kk] = (1.0 - vals[kk]) * 1.0e7;
prechecked
                    if (vals[kk] < 0.0f) {
                        if (vals[kk] < -0.00001f) {
                            err("Val < 0.0: " + vals[kk]);
                        } else {
                            vals[kk] = 0.0f;
                        }
                    } else if (vals[kk] > 1.0f) {
                        err("Val > 1.0: " + vals[kk]);
                    } else {
                        double td = (1.0 - (double)vals[kk]) * 1.0e7;
                        if (td > Integer.MAX_VALUE) {
                            err("Val too high for 1e7 factor: " + vals[kk] + "-> " + td +
                                    " column " + col.name);
                        }
                        if (td < 0.0) err("td < 0: " + td);
                        vals[kk] = (float) td;

                        if (vals[kk] < 0.0f) {
                            err("cast error: " + td + " gives float " + vals[kk]);
                        }
                    }
                }
            }
*/

    private static void pullDBList(Connection conn,
                                   int viewNum,
                                   List<Integer> archives,
                                   StringBuilder bad)
            throws Exception {

        //pout("Pulling lists for " +
        //                   (archive == null ? "all" : archive));

        int pics = 0;

        int dirOverlap = 0;

        Map<String, Set<String>> nogeomMap = new HashMap<>();
        int base_ct = 0;
        int skip_dup = 0;

        for (String coder : kwdCoders) {

            pout("Coder: " + coder);

            boolean cache = coder.contains("nogeom");

            //ListHolder ih = new ListHolder();

            int cc = ConfigUtil.kwdCoderCode(coder);

            //lastArch = -1;
            //lastSeq = -1;
            //lastId = -1;

            //String lastId = null;

            for (BigPicture bp : pic_list) {

                if (archives != null  &&  !archives.contains(bp.p.archive)) {
                    continue;
                }
                //if (bp.p.id.equals(lastId)) {
                    //continue;
                //}
                //lastId = bp.p.id;

                //pout("SEQ " + bp.p.sequence);

                // -- kwds in desc order

                ListHolder lh = new ListHolder();

err("live still??");
                List<PicPicKwd> kwd_l =
                       PicPicKwdDao.getPicPicKwdByCoderAndId1(
                                          conn, cc, bp.p.id, "DESC", 0);

                Set<String> picSet;
                if (cache) {
                    picSet = new HashSet<>();
                } else {
                    picSet = nogeomMap.get(bp.p.id);
                    if (picSet == null) {
                        pout("WARN No picSet for " + bp.p.id);
                    }
                }

                int ct = 0;
                for (PicPicKwd ppk : kwd_l) {

                    if (ppk.closeness == 0) {
                        break;
                    }
                    BigPicture bp2 = pic_idx.get(ppk.id2);
                    if (bp2 == null) {
                        // hybrid
                        continue;
                    }
                    if (archives != null  &&
                         !archives.contains(bp2.p.archive)) {
                        continue;
                    }
                    ct++;
                    base_ct++;

                    if (cache) {
                        picSet.add(bp2.p.id);
                    } else if (picSet != null  &&  picSet.contains(bp2.p.id)) {
                        skip_dup++;
                        continue;
                    }
                    lh.id2_l.add(bp2.p.id);
                    lh.value_l.add(ppk.closeness);
                }
                String last_id2 = null;
                if (lh.id2_l.size() == 0) {
                    if (cache) {
                        bad.append(coder).append(": ")
                                         .append(bp.p.id)
                                         .append("\n");
                    }
                } else {
                    pout("REadd lh.map for kwds");
                    //ih.map.put(bp.p.id, lh);
                    last_id2 = lh.id2_l.get(lh.id2_l.size()-1);
                }

                if (cache) {
                    nogeomMap.put(bp.p.id, picSet);
                }

            } // pictures

        } // coder
        pout("Overlaps: " + dirOverlap);
    }
}
