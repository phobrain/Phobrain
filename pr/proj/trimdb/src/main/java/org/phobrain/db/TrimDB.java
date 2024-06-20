package org.phobrain.db;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  TrimDB/cmdline - make *_dump.sql scripts for loading a
 **    fewer-pics version of the database.
 */

import org.phobrain.util.Stdio;
import org.phobrain.util.ConfigUtil;
import org.phobrain.util.MapUtil;
import org.phobrain.util.ColorUtil;
import org.phobrain.util.KwdUtil;

import org.phobrain.db.record.Picture;
import org.phobrain.db.record.Keywords;
import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.PictureDao;
import org.phobrain.db.dao.PairDao;
import org.phobrain.db.dao.KeywordsDao;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.PreparedStatement;

public class TrimDB extends Stdio {

/*
    // output files

    static String PICTURE = "trim_picture_dump.sql";
    static String KEYWORDS = "trim_keywords_dump.sql";

    static String PAIR_OUT_V = "trim_pairs_v_dump.sql";
    static String PAIR_OUT_H = "trim_pairs_h_dump.sql";

    static String PAIRTOP_OUT_COL_V = "trim_pairtop_col_v.sql";
    static String PAIRTOP_OUT_COL_H = "trim_pairtop_col_h.sql";

    static String PAIRTOP_OUT_NN_V = "trim_pairtop_nn_v.sql";
    static String PAIRTOP_OUT_NN_H = "trim_pairtop_nn_h.sql";
*/

    private static void usage(String msg) {
        System.err.println( "Usage: [prog] <v_ct> <h_ct>\n" +
                            "\t[prog] redo <table>");
        if (msg != null) {
            System.err.println("\t" + msg);
        }
        System.exit(1);
    }

    // file with list of pics in $PHOBRAIN_CONFIG/

    private final static String EXCLUDES_FNAME = "trimdb_excludes";

    private static List<String> loadExcludes() throws Exception {

        File f = ConfigUtil.compileConfigDir(EXCLUDES_FNAME);

        if (!f.exists()) {
            pout("\n\nNo " + f + " file - do you want to export any photo?");
            pout("Sleeping 5 sec, then going ahead.");
            try { Thread.sleep(5000); } catch (Exception e) { /* ignore */ }
            pout("Going ahead.\n\n");
            return null;
        } else {
            List<String> result = new ArrayList<>();

            BufferedReader in = new BufferedReader( new FileReader(f));
            while (true) {
                String line = in.readLine();
                if (line == null) {
                    break;
                }
                line = line.trim();
                if (line.startsWith("#")) {
                    continue;
                }
                result.add(line);
                //pout("exclude id " + line);
            }
            in.close();
            return result;
        }
    }

    public static void main(String[] args) {
  
        if (args.length < 2) {
            usage("Expected 2 or 3 args, got: " + Arrays.toString(args));
        }

        // TODO: move archive lists to args
        //   Full: Ellen,Ed since small; plus my latest
        int[] full_archives = { 4, 12, 36, 37, 38 }; 

        //   Select-from: typically all the rest, 
        //      so allow it to default on a timeout?
        int[] sel_archives = { 1, 2, 3, 5, 7, 8, 9, 10, 11, 13, 14, 23, 33, 34, 35 };

        pout("=== This is hard-coded in TrimDB.java - hm:");
        pout("    Using all of these archives: " + Arrays.toString(full_archives));
        pout("    Selecting remainder (based on pairs_[vh].d0) from these archives: " + 
                    Arrays.toString(sel_archives));
        pout("");
        pout("=> Sleeping 10 sec for you to think");
        try { Thread.sleep(10000); } catch (Exception ignore) {}

        List<Integer> getAllList = new ArrayList<>();
        for (int i : full_archives) {
            getAllList.add(i);
        }

        List<Integer> getSomeList = new ArrayList<>();
        for (int i : sel_archives) {
            getSomeList.add(i);
        }

        List<Integer> allArchives = new ArrayList<>();
        allArchives.addAll(getAllList);
        allArchives.addAll(getSomeList);

        Connection conn1 = null;

        try {
            int vlimit = Integer.parseInt(args[0]);
            int hlimit = Integer.parseInt(args[1]);

                //Class.forName("com.mysql.jdbc.Driver").newInstance();
            Class.forName("org.postgresql.Driver");
            conn1 = DriverManager.getConnection( "jdbc:postgresql:pr", 
                                                 "pr", "@@pr");
            conn1.setAutoCommit(true);

            List<String> allV = PictureDao.getPicList(conn1, allArchives,
                                                               true, null);
            List<String> allH = PictureDao.getPicList(conn1, allArchives,
                                                               false, null);

            List<String> fullArchV = PictureDao.getPicList(conn1, getAllList,
                                                               true, null);
            
            List<String> fullArchH = PictureDao.getPicList(conn1, getAllList,
                                                               false, null);

            pout("Full Archives: V: " + fullArchV.size() + "/" + allV.size() +
                 "  H: " + fullArchH.size() + "/" + allH.size());

            List<String> selV = PictureDao.getPicList(conn1, getSomeList, true,
                                                  vlimit - fullArchV.size());
            List<String> selH = PictureDao.getPicList(conn1, getSomeList, false,
                                                  hlimit - fullArchH.size());

            // selV,H likely larger, so add to them
            selV.addAll(fullArchV);
            selH.addAll(fullArchH);

            // delete any $confdir/trimdb_excludes
            List<String> excludes = loadExcludes();

            List<String> removed = new ArrayList<>();
            List<String> not_removed = new ArrayList<>();

            for (String id : excludes) {
                boolean rmvd = false;
                // look up which?
                if (selV.remove(id)) {
                    //pout("Removed " + id + " from V");
                    rmvd = true;
                }
                if (selH.remove(id)) {
                    //pout("Removed " + id + " from H");
                    rmvd = true;
                }
                // for tracking
                if (rmvd) {
                    removed.add(id);
                } else {
                    not_removed.add(id);
                }
            }
            pout("Excludes not present: " + not_removed);
            pout("Excludes removed: " + removed);

            pout("With selections/Totals: V: " + selV.size() + 
                    "/" + allV.size() +
                    "  H: " + selH.size() + "/" + allH.size());

            // picture table
            StringBuilder sbV = new StringBuilder();
            for (String id : selV) {
                sbV.append("'").append(id).append("',");
            }
            sbV.deleteCharAt(sbV.length()-1);

            StringBuilder sbH = new StringBuilder();
            for (String id : selH) {
                sbH.append("'").append(id).append("',");
            }
            sbH.deleteCharAt(sbH.length()-1);

            String allIds = sbV.toString() + "," + sbH.toString();

            String stmt = "DROP TABLE IF EXISTS trim_picture;";
            PreparedStatement ps = conn1.prepareStatement(stmt);

            pout(stmt);
            ps.execute();

            stmt = "CREATE TABLE trim_picture AS SELECT * FROM picture" +
                          " WHERE id IN (" + allIds + ")";
            ps = conn1.prepareStatement(stmt);
            pout("Create trim_picture");
            ps.execute();

            stmt = "DROP TABLE IF EXISTS trim_picture_prop;";
            ps = conn1.prepareStatement(stmt);
            pout(stmt);
            ps.execute();

            stmt = "CREATE TABLE trim_picture_prop AS " +
                   " SELECT * FROM picture_prop;";
            ps = conn1.prepareStatement(stmt);
            pout("Create trim_picture_prop");
            ps.execute();

            stmt = "DROP TABLE IF EXISTS trim_keywords;";
            ps = conn1.prepareStatement(stmt);
            pout(stmt);
            ps.execute();

            stmt = "CREATE TABLE trim_keywords AS SELECT * FROM keywords" +
                          " WHERE id IN (" + allIds + ")";
            ps = conn1.prepareStatement(stmt);
            pout("Create trim_keywords");
            ps.execute();

            pout("trim_picture, trim_picture_prop, trim_keywords created.");
            pout("Run apply.py to update pair table family of tables.");

            /*

            stmt = "DROP TABLE IF EXISTS trim_approved_pair";
            ps = conn1.prepareStatement(stmt);
            pout(stmt);
            ps.execute();

            stmt = "CREATE TABLE trim_approved_pair AS SELECT * " +
                   " FROM approved_pair " +
                          " WHERE id1 IN (" + allIds +
                          ") AND id2 IN (" + allIds + ")";
            ps = conn1.prepareStatement(stmt);
            pout("Create trim_approved_pair");
            ps.execute();
//System.exit(1);
            stmt = "DROP TABLE IF EXISTS trim_pairs_v";
            ps = conn1.prepareStatement(stmt);
            pout(stmt);
            ps.execute();

            String vIds = sbV.toString();

            stmt = "CREATE TABLE trim_pairs_v AS SELECT * FROM pairs_v " +
                          " WHERE id1 IN (" + vIds + 
                          ") AND id2 IN  (" + vIds + ")";
            ps = conn1.prepareStatement(stmt);
            pout("Create trim_pairs_v");
            ps.execute();

            stmt = "DROP TABLE IF EXISTS trim_pairs_h";
            ps = conn1.prepareStatement(stmt);
            pout(stmt);
            ps.execute();

            String hIds = sbH.toString();

            stmt = "CREATE TABLE trim_pairs_h AS SELECT * FROM pairs_h " +
                          " WHERE id1 IN (" + hIds + 
                          ") AND id2 IN (" + hIds + ")";
            ps = conn1.prepareStatement(stmt);
            pout("Create trim_pairs_h");
            ps.execute();

            stmt = "DROP TABLE IF EXISTS trim_pairtop_col_v";
            ps = conn1.prepareStatement(stmt);
            pout(stmt);
            ps.execute();

            stmt = "CREATE TABLE trim_pairtop_col_v AS SELECT * " +
                   " FROM pairtop_col_v " +
                          " WHERE id1 IN (" + vIds + 
                          ") AND id2 IN (" + vIds + ")";
            ps = conn1.prepareStatement(stmt);
            pout("Create trim_pairtop_col_v");
            ps.execute();

            stmt = "DROP TABLE IF EXISTS trim_pairtop_nn_v";
            ps = conn1.prepareStatement(stmt);
            pout(stmt);
            ps.execute();

            stmt = "CREATE TABLE trim_pairtop_nn_v AS SELECT * " +
                   " FROM pairtop_nn_v " +
                          " WHERE id1 IN (" + vIds +
                          ") AND id2 IN (" + vIds + ")";
            ps = conn1.prepareStatement(stmt);
            pout("Create trim_pairtop_nn_v");
            ps.execute();

            stmt = "DROP TABLE IF EXISTS trim_pairtop_col_h";
            ps = conn1.prepareStatement(stmt);
            pout(stmt);
            ps.execute();

            stmt = "CREATE TABLE trim_pairtop_col_h AS SELECT * " +
                   " FROM pairtop_col_h " +
                          " WHERE id1 IN (" + hIds + 
                          ") AND id2 IN (" + hIds + ")";
            ps = conn1.prepareStatement(stmt);
            pout("Create trim_pairtop_col_h");
            ps.execute();

            stmt = "DROP TABLE IF EXISTS trim_pairtop_nn_h";
            ps = conn1.prepareStatement(stmt);
            pout(stmt);
            ps.execute();

            stmt = "CREATE TABLE trim_pairtop_nn_h AS SELECT * " +
                   " FROM pairtop_nn_h " +
                          " WHERE id1 IN (" + hIds +
                          ") AND id2 IN (" + hIds + ")";
            ps = conn1.prepareStatement(stmt);
            pout("Create trim_pairtop_nn_h");
            ps.execute();
            */

//QQQ trim_keywords_dump.sql
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (conn1 != null) { try { conn1.close(); } catch (Exception ig){} }
        }

    }

}
