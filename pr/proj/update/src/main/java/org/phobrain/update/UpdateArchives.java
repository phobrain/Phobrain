package org.phobrain.update;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  Update secondary data in Phobrain picture table.
 **  Run d0 after pr.pair_[vh] tables have been loaded.
 **/

import org.phobrain.util.Stdio;
import org.phobrain.util.ID;
import org.phobrain.util.FileRec;
import org.phobrain.util.HashCount;
import org.phobrain.util.MathUtil;
import org.phobrain.util.MiscUtil;

import org.phobrain.db.record.Picture;
import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.PictureDao;
import org.phobrain.db.dao.ApprovalDao;
import org.phobrain.db.dao.UniquePairDao;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.DriverManager;

import javax.naming.InvalidNameException;

public class UpdateArchives extends Stdio {

    private final static boolean UPDATE = true;

    private static int fold = -1;

    private static void usage(String msg) {

        if (msg != null) {
            System.err.println("\t" + msg);
        }

        System.err.println(
         "Usage: [prog] d0 <v|h> <.pairs>\n" +
         "    (pairvecs: [--archives <a1> <a2> ..] pairvecs ..)\n" +
         PictureDao.updateVectorsUsage() + //  normally in import
         "       [prog] 1time <tbl>\n" +
         "       [prog] \\\n" +
            "\tradius_AB_base radius_RGB_base ang_AB_base \\\n" +
            "\tradius8D_v radius8D_h radius27D_v radius27D_h " +
              "radius32KD_v radius32KD_h\n" +
            "First row is from ../pairs/, second from ../histogram/.");
        System.exit(1);
    }


    // Hashes from files with |img01a.jpg ...|
    private static Map<String, String> radiiABHash = new HashMap<>();
    private static Map<String, String> radii27DHash = new HashMap<>();
    private static Map<String, String> radii32DHash = new HashMap<>();
    private static Map<String, String> radiiRGBHash = new HashMap<>();
    private static Map<String, String> radii8DHash = new HashMap<>();
    private static Map<String, String> angABHash = new HashMap<>();

    private static int hashGet(String name, Map<String, String> map, String id)
            throws Exception {
        String radius = map.get(id);
        if (radius == null) {
            err(name + " No radius for " + id);
        }
        radius = radius.trim();
        return Integer.parseInt(radius);
    }

    private static void loadHash(Map<String, String> map, File file) {

        pout("Loading map from " + file);

        int ct = 0;
        try {
            BufferedReader in = new BufferedReader(new FileReader(file));
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                String[] ss = line.split(" ");
                if (ss.length != 2) {
                    err("Expected 2 fields: " + line);
                }
                ct++;
                //int i = line.indexOf(" ");
                //line = line.substring(i+1);
                map.put(ss[0], ss[1]);
            }
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
            err("Reading " + file + ": " + e);
        }
        pout("Map size: " + map.size() + " lines " + ct);

    }

    private static void popTable() {

        try {
            //Class.forName("com.mysql.jdbc.Driver").newInstance();
            Class.forName("org.postgresql.Driver");
            Connection conn = DriverManager.getConnection("jdbc:postgresql:pr",
                                                      "pr", "@@pr");
            conn.setAutoCommit(false);

            updateDB(conn);

            DaoBase.closeSQL(conn);

        } catch (Exception e ) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void updateDB(Connection conn)
            throws Exception {

        int records = 0;

        Picture p = new Picture();

        List<String> ids = PictureDao.getIdsInOrder(conn, true);
        ids.addAll(PictureDao.getIdsInOrder(conn, false));
        pout("Got " + ids.size());
        for (String id : ids) {
            //pout("-- " + id);
            ID iid = new ID(id);

            p.id = iid.id;
            p.archive = iid.arch;
            p.sequence = iid.seq;
            p.seq2 = iid.seq2;

            p.dCtrAB = hashGet("AB", radiiABHash, id);
            p.dCtr27D = hashGet("27D", radii27DHash, id);
            p.dCtr64D = hashGet("32D", radii32DHash, id);
            p.dCtrRGB = hashGet("RGB", radiiRGBHash, id);
            p.dCtr8D = hashGet("8D", radii8DHash, id);
            p.angAB = hashGet("angAB", angABHash, id);

            if (UPDATE) {

                //pout("INSERT: " + p.fileName);
                PictureDao.updateAngRadii(conn, p);

            } else {
                //pout("skip insert: " + p);
            }

            records++;
        }
        conn.commit();
        pout("Done: " + records);
    }


    public static void main(String[] args) {

        // ORIGINAL: imagedir sizes colors densities kwds1 ...

        if (args.length == 0) {
            usage(null);
        }

        Set<Integer> archives = new HashSet<>();

        int argi = 0;

        if ("--archives".equals(args[argi])) {

            if (args.length < 2) {
                usage("Need archive numbers");
            }

            for (argi=1; argi<args.length; argi++) {

                String s = args[argi];
                if (s.startsWith("/")) {
                    break;
                }
                try {

                    int arch = Integer.parseInt(s);

                    if (!archives.add(arch)) {
                        err("Duplicate archive: " + arch);
                    }

                } catch (NumberFormatException nfe) {

                    // normal exit if file not starting in '/'
                    break;

                } catch (Exception e) {
                    err("Parsing archives: " + e);
                }

            }
            pout("Archives: " + Arrays.toString(archives.toArray()));

            String[] newargs = new String[args.length - archives.size()];
            int i = 0;
            for ( ; argi<args.length; argi++) {
                newargs[i++] = args[argi];
            }
            args = newargs;

            pout("Remaining args: " + Arrays.toString(args));
        }

        if (archives.size() > 0  && !"pairvecs".equals(args[0])) {

            // TODO should work w/ d0?
            err("'--archives' option only works with pairvecs");

        }

        if ("histogram".equals(args[0])  ||
            "imgvecs".equals(args[0])  ||
            "pairvecs".equals(args[0])) {

            Connection conn = null;
            List<Picture> pics = null;

            try {
                //Class.forName("com.mysql.jdbc.Driver").newInstance();
                Class.forName("org.postgresql.Driver");
                conn = DriverManager.getConnection("jdbc:postgresql:pr",
                                                      "pr", "@@pr");

                if (archives.size() > 0) {
                    Set<Integer> realArchives = PictureDao.getArchives(conn);
                    for (int arch : archives) {
                        if (!realArchives.contains(arch)) {
                            err("Not a real archive: " + arch);
                        }
                    }
                }
                pout("== updateVectors: getting ALL pictures from db");

                pics = PictureDao.getPicturesInOrder(conn, null);

                //pout("== updateVectors: ALL pictures: " + pics.size());

            } catch (Exception e) {
                err("Getting db connection/pic list: " + e);
            } finally {
                DaoBase.closeSQL(conn);
            }
            if (archives.size() != 0) {

                pout("== updateVectors: using selected archives");

                List<Picture> selected = new ArrayList<>();

                for (Picture p : pics) {

                    if (archives.contains(p.archive)) {
                        selected.add(p);
                    }
                }
                pout("Archive selection: all: " + pics.size() +
                            " selected: " + selected.size());
                pics = selected;
            }

            pout("Updating " + args[0] + ": " + pics.size() + " pics");

            PictureDao.updateVectors(args, pics);

        } else if ("d0".equals(args[0])) {

            if (args.length != 3) {
                usage("d0: expected 3 args");
            }

            // map aggregate pairs_[vh] per picture to pr.picture table.
            //      pairs_[vh] tables won't exist at outset,
            //      they will be created when user's training loop gets traction.

            try {
                updateD0(args[1], args[2]);
            } catch (InvalidNameException ine) {
                err("Bad ID: " + ine);
            }

        } else if (args.length != 9) {
            usage(null);
        }
        if (args.length != 9) {
            // handled above
            System.exit(0);
        }

        // ../pairs/-written, in separate _[vh] files

        File radiiABFile_v = new File(args[0] + "_v");
        if (!radiiABFile_v.isFile()) {
            usage("Not a file: " + radiiABFile_v);
        }
        File radiiABFile_h = new File(args[0] + "_h");
        if (!radiiABFile_h.isFile()) {
            usage("Not a file: " + radiiABFile_h);
        }

        File radiiRGBFile_v = new File(args[1] + "_v");
        if (!radiiRGBFile_v.isFile()) {
            usage("Not a file: " + radiiRGBFile_v);
        }
        File radiiRGBFile_h = new File(args[1] + "_h");
        if (!radiiRGBFile_h.isFile()) {
            usage("Not a file: " + radiiRGBFile_h);
        }

        File angABFile_v = new File(args[2] + "_v");
        if (!angABFile_v.isFile()) {
            usage("Not a file: " + angABFile_v);
        }
        File angABFile_h = new File(args[2] + "_h");
        if (!angABFile_h.isFile()) {
            usage("Not a file: " + angABFile_h);
        }

        // histogram written, in separate [vh]_ files, go figure

        File radii8DFile_v = new File(args[3]);
        if (!radii8DFile_v.isFile()) {
            usage("Not a file: " + radii8DFile_v);
        }
        File radii8DFile_h = new File(args[4]);
        if (!radii8DFile_h.isFile()) {
            usage("Not a file: " + radii8DFile_h);
        }

        File radii27DFile_v = new File(args[5]);
        if (!radii27DFile_v.isFile()) {
            usage("Not a file: " + radii27DFile_v);
        }
        File radii27DFile_h = new File(args[6]);
        if (!radii27DFile_h.isFile()) {
            usage("Not a file: " + radii27DFile_h);
        }

        File radii32DFile_v = new File(args[7]);
        if (!radii32DFile_v.isFile()) {
            usage("Not a file: " + radii32DFile_v);
        }
        File radii32DFile_h = new File(args[8]);
        if (!radii32DFile_h.isFile()) {
            usage("Not a file: " + radii32DFile_h);
        }

        // load data, _[vh] and plain/both

        loadHash(radiiABHash,  radiiABFile_v);
        loadHash(radiiABHash,  radiiABFile_h);

        loadHash(radiiRGBHash, radiiRGBFile_v);
        loadHash(radiiRGBHash, radiiRGBFile_h);

        loadHash(angABHash,    angABFile_v);
        loadHash(angABHash,    angABFile_h);

        loadHash(radii8DHash,  radii8DFile_v);
        loadHash(radii8DHash,  radii8DFile_h);

        loadHash(radii27DHash, radii27DFile_v);
        loadHash(radii27DHash, radii27DFile_h);

        loadHash(radii32DHash, radii32DFile_v);
        loadHash(radii32DHash, radii32DFile_h);

	    //updateD0();

        popTable();

    }

    private static List<String> unpopular_v = new ArrayList<>();
    private static List<String> unpopular_h = new ArrayList<>();

    static void updateD0(String orient, String fname) throws InvalidNameException {

        Connection conn2 = null;

        try {
            conn2 = DriverManager.getConnection(
                                    "jdbc:postgresql:pr", "pr", "@@pr");

            if ("v".equals(orient)) {

                PictureDao.updateD0Sum(conn2, true, fname, unpopular_v);

            } else {

                PictureDao.updateD0Sum(conn2, false, fname, unpopular_h);

            }
        } catch (Exception e) {
            e.printStackTrace();
            err("PictureDao.updateD0SUM: " + e);
        } finally {
            DaoBase.closeSQL(conn2);
        }
    }
/*
    if (t1 != null) {
        pout("\nUnpopular v: " + unpopular_v.size());
        if (unpopular_v.size() > 0) {
            HashCount perArch = new HashCount();
            for (String id : unpopular_v) {
                ID iid = new ID(id);
                perArch.add("" + iid.arch);
            }
            pout("== per archive\n" + perArch.toString(false));
        }
    }

        if (t2 != null) {
            pout("\n\nUnpopular h: " + unpopular_h.size());
            if (unpopular_h.size() > 0) {
                HashCount perArch = new HashCount();
                for (String id : unpopular_h) {
                    ID iid = new ID(id);
                    perArch.add("" + iid.arch);
                }
                pout("== per archive\n" + perArch.toString(false));
            }
        }
        pout("\n\nTotal unpop: " + (unpopular_v.size() + unpopular_h.size()));

    }
*/


    static void updatePairIds(String tbl) {

        Connection conn2 = null;
        try {
            conn2 = DriverManager.getConnection( "jdbc:postgresql:pr",
                                                      "pr", "@@pr");
            if ("approved_pair".equals(tbl)) {
                pout("app pr");
                //ApprovalDao.newIds(conn2);
            } else if ("pair_local".equals(tbl)) {
                pout("pr loc");
                //UniquePairDao.newIds(conn2);
            } else {
                usage("Unknown table: " + tbl);
            }
        } catch (Exception e) {
            pout("newids: " + e);
            e.printStackTrace();
        } finally {
            DaoBase.closeSQL(conn2);
        }
    }

}
