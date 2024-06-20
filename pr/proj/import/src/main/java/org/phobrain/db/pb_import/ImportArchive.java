package org.phobrain.db.pb_import;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  ImportArchive/cmdline - import an archive into Phobrain's database.
 **    archive: a numbered group of pics with basenum[-subnum] id's,
 **             such that basenum is in 1..9999, and subnums are crops
 **             and color edits.
 */

import org.phobrain.util.Stdio;
import org.phobrain.util.MiscUtil;
import org.phobrain.util.FileRec; // for jpgs, has "ID id;'
import org.phobrain.util.ID; // for 0kwd, version in 0001=1
import org.phobrain.util.KwdUtil;
import org.phobrain.util.ConfigUtil;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.StringJoiner;

import java.util.stream.Stream;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverManager;

import org.phobrain.util.HashCount;

import org.phobrain.db.record.Picture;
//import org.phobrain.db.record.Picture.PictureCompare;
import org.phobrain.db.record.Keywords;
import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.PictureDao;
import org.phobrain.db.dao.KeywordsDao;

public class ImportArchive extends Stdio {

    static boolean INSERT_PIC = true;
    static boolean INSERT_KWD = true;

    final static boolean KEYWORDS_REQD = false;

    private static void usage(String msg) {
        System.err.println(
         "Usage: [prog] list <outfile> <images_dir> <arch1> <arch2> ...\n" +
         "       [prog] all|kwd <images_dir> <image_desc_dir> <archive_num>");

        if (msg != null) {
            System.err.println("\t" + msg);
        }
        System.exit(1);
    }

    private static String parseTag(String s) throws Exception {
        int start = -1;
        int end = -1;
        for (int i=0; i<s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                start = i;
                break;
            }
        }
        if (start == -1) {
            throw new Exception("parseTag: Expected a number: " + s);
        }
        for (int i=start+1; i<s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                end = i;
                break;
            }
        }
        if (end == -1) {
            return null;
        }
        if (s.charAt(end) == '_') {
            return null;
        }
        start = end;
        end = -1;
        for (int i=start+1; i<s.length(); i++) {
            if (s.charAt(i) == '_') {
                end = i;
                break;
            }
        }
        if (end == -1) {
            end = s.length();
        }
        return s.substring(start, end);
    }


    // for iterating in order
    private static Set<Picture> fs_pics = new TreeSet<>(); //new PictureCompare());

    // for applying keywords by id
    private static Map<String, Picture> idPics = new TreeMap<>();
    private static Set<Integer> seqSet = new HashSet<>();
    private static StringBuilder nodims = new StringBuilder();

    private static void loadFS(File imageDir, int archiveCode) {
        String path = imageDir.toString();
        if (!path.endsWith("/")) path += "/";
        pout("Scanning " + path);

        try {
            String[] files = imageDir.list();
            pout("Files: " + files.length);
            Arrays.sort(files);

            for (String file : files) {

                if (file.startsWith(".")  ||
                    (!file.endsWith(".jpg")  &&  !file.endsWith(".JPG"))) {
                    pout("Ignoring file: " + file);
                    continue;
                }

                FileRec fr = new FileRec(path + file, true);

                //pout("P: "  + file + "] " + fr.id.id + " " + fr.id.seq);

                Picture p = idPics.get(fr.id.id);
                if (p != null) {
                    err("Dupe id: [" + fr.id.id + "]: " + file);
                }
                p = new Picture();
                idPics.put(fr.id.id, p);
                p.xid = -1;  // HACK
                p.id = fr.id.id;
                p.props = new HashMap<>();
                p.fileName = fr.fname;
                p.archive = archiveCode;
                p.sequence = fr.id.seq;
                p.seq2 = fr.id.seq2;
                p.variationTag = fr.id.tag;
                p.pref = true; // just in case

                // legacy fields for Bill's archive
                p.variationType = null;

                p.sceneType = null;
                p.outdoors = false;
                p.lighting = null;
                p.angle = null;
                p.place = null;

                fs_pics.add(p);
            }
        } catch (Exception e) {
            e.printStackTrace();
            err(e.toString());
        }
        pout("Total FS: " + fs_pics.size());
        if (nodims.length() > 0) {
            pout("Skipped on nodims: " + nodims);
        }
    }

    //private static int ouch = 0;

    private static Map<String, String[]> colorsHash = new HashMap<>();
    //private static Map<String, float[]> histosHash = new HashMap<>();

    private static void getColorInfo(Picture p) {

        String[] colors = colorsHash.get(p.fileName);

        if (colors == null) {
            throw new RuntimeException("No color for " + p.fileName);
        }
        if (colors.length != 13) {
            throw new RuntimeException(
                    "Bad width+height+area+density+rgb+lab+radius+contrast " +
                    " for file " + p.fileName + ": [" +
                    Arrays.toString(colors) + "]");
        }

        p.width = Integer.parseInt(colors[0]);
        p.height = Integer.parseInt(colors[1]);
        p.vertical = p.height > p.width;

        // absolute as of 2022_06 - before was per-archive in densitarch.sh
        p.density = (int)(100.0 * Double.parseDouble(colors[3]));

        p.r = Integer.parseInt(colors[4]);
        p.g = Integer.parseInt(colors[5]);
        p.b = Integer.parseInt(colors[6]);
        p.rgbRadius = Integer.parseInt(colors[7]);
        p.ll = Integer.parseInt(colors[8]);
        p.aa = Integer.parseInt(colors[9]);
        p.bb = Integer.parseInt(colors[10]);
        p.labRadius = Integer.parseInt(colors[11]);
        p.labContrast = Integer.parseInt(colors[12]);
    }

/*
    private static void getHistosInfo(Picture p) {

        float[] all = histosHash.get(p.fileName);

        if (all == null) {
            throw new RuntimeException("No hashed ml_concat histo for " + p.fileName);
        }

        if (all.length != 1984) {
            throw new RuntimeException(
                    "Bad grey128+sat128+rgb12_3 = 1984 histo " +
                    " for file " + p.fileName + ": [" +
                    Arrays.toString(all) + "]");
        }

        p.ml_hists = all;
    }
*/

    // hashes map filenames to data
    private static void loadHashFromFile(File file, int picIx,
                                    Map<String, String[]> map) {
        pout("Loading map from " + file + " fname field=" + picIx);
        boolean first = true;
        try {
            BufferedReader in = new BufferedReader(new FileReader(file));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                if (picIx == 1  &&  line.startsWith("FileName")) {
                    continue;
                }
                line = line.trim();
                String[] fields = line.split("\\s+");
                if (first) {
                    pout("First non-comment line: " + line +
                                        " fields=" + fields.length);
                    first = false;
                }

                String fname = fields[picIx];

                String[] payload = new String[fields.length-picIx-1];
                int payload_ix = 0;
                for (int i=picIx+1; i<fields.length; i++) {
                    payload[payload_ix++] = fields[i];
                }

                map.put(fname, payload);
            }
            in.close();
        } catch (Exception e) {
            System.err.println("Reading " + file + ": " + e);
            e.printStackTrace();
            System.exit(1);
        }
        pout("Map size: " + map.size());
    }

    private static void loadHashToStringFromDir(File dir, Map<String, String[]> map) {

        String dirname = dir.getPath() + "/";
        String[] files = dir.list();

        pout("Loading map from " + dirname + " files: " + files.length);

        for (String fname : files) {
            if (!fname.endsWith(".jpg")) {
                pout("skipping non-jpg: " + fname);
                continue;
            }
            try {
                String path = dirname + fname;
                BufferedReader in = new BufferedReader(new FileReader(path));
                String line = in.readLine();
                in.close();
                if (line == null) {
                    err("Nada on " + path);
                }
                line = line.trim();
                String[] payload = line.split("\\s+");

                map.put(fname, payload);
            } catch (Exception e) {
                System.err.println("Reading " + fname + ": " + e);
                e.printStackTrace();
                System.exit(1);
            }
        }
        pout("Map size: " + map.size());
    }

    private static void loadHashToFloatFromDir(File dir, Map<String, float[]> map) {

        String dirname = dir.getPath() + "/";
        String[] files = dir.list();

        pout("loadHashToFloatFromDir: Loading float[] map from " + dirname + " files: " + files.length);

        int ct = 0;

        for (String fname : files) {

            if (!fname.endsWith(".hist_bin")) {
                pout("skipping non-.hist_bin: " + fname);
                continue;
            }

            ct++;
            try {
                String path = dirname + fname;
                FileRec fr = new FileRec(path, true); // expectArchSeq=true
                fr.readBinHistogram(); // as Object(s)
                if (fr.histogram.length != 1984) {
                    err("Unexpected ml_concat size: " + fr.histogram.length +
                            ": file: " + ct +
                            ": " + path);
                }
                float[] vals = new float[1984];
                for (int i=0; i<vals.length; i++) {
                    vals[i] = (float) fr.histogram[i];
                }
//err("put " + fname);
                map.put(fname.replace("hist_bin", "jpg"), vals);

            } catch (Exception e) {
                System.err.println("Reading " + fname + ": " + e);
                e.printStackTrace();
                System.exit(1);
            }
        }
        pout("Map size: " + map.size());
    }

    private static double kwdScale(int method, int count) {
        switch (method) {
            case 5:
                return 1.0d /
                    ((double) count * (double) count * (double) count *
                     (double) count * (double) count);
            case 1:
                return 1.0d / (double) count;
            case 2:
                return 1.0d / ((double) count * (double) count);
            case 3:
                return 1.0d / ((double) count * (double) count * (double) count);
            case 4:
                return 1.0d / ((double) count * (double) count *
                               (double) count * (double) count);
            default:
                throw new RuntimeException("Bad keywordScale: " + method);
        }
    }

    private static void popTables() {

        try {
            //Class.forName("com.mysql.jdbc.Driver").newInstance();
            Class.forName("org.postgresql.Driver");
            Connection conn = DriverManager.getConnection("jdbc:postgresql:pr",
                                                      "pr", "@@pr");
            conn.setAutoCommit(false);

            pushDB(conn);

            conn.close();
        } catch (Exception e ) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void pushDB(Connection conn)
            throws Exception {

        // populate the db picture and keywords tables
        // go in key=seq order

        int records = 0;

        StringBuilder warn = new StringBuilder();
        int warn_ct = 0;
        StringBuilder error = new StringBuilder();

        List<Picture> checkSeq = new ArrayList<>();

        for (Picture p : fs_pics) {
            //pout("KEY " + pair.getKey());

            if (KEYWORDS_REQD  &&  p.xid == -1) {
                if (!seqSet.contains(p.sequence)) {
                    checkSeq.add(p);
                } else {
                    warn.append(p.fileName);
                    warn_ct++;
                    for (Picture pp : fs_pics) {
                        if (pp == p) {
                            continue;
                        }
                        if (pp.sequence == p.sequence) {
                            warn.append("=>").append(pp.fileName);
                            if (pp.vertical != p.vertical) {
                                warn.append(" orient mismatch");
                            }
                        }
                    }
                    //if (warn_ct > 1 &&  warn_ct % 4 == 0) {
                        warn.append("\n\t");
                    //} else {
                        //warn.append("  ");
                    //}
                }
                continue;
            }

            seqSet.add(p.sequence);

            if (INSERT_PIC) {

                //pout("INSERT: " + p.fileName);
                long xid = PictureDao.insertPicture(conn, p);

            }

            records++;
        }
        if (warn.length() > 0) {
            pout("Redundant files: no kwd, but other w/ seq ok: " + warn_ct +
                    "\n\t" + warn.toString());
        }
        if (nodims.length() > 0) {
            pout("Skipped file for no dims: " + nodims);
        }

        for (Picture p : checkSeq) {
            if (!seqSet.contains(p.sequence)) {
                error.append(p.fileName).append(" ");
            }
        }

        if (error.length() > 0) {
            err("File(s) w/ seq having no 0kwd entry: " + error.toString());
        }

        if (INSERT_KWD) {
            pout("Inserting kwds: " + kwds_l.size());
            for (Keywords k : kwds_l) {
                KeywordsDao.insertKeywords(conn, k);
            }
        }
        conn.commit();
        pout((INSERT_PIC ? "" : "WOULD HAVE ") +
                           "INSERTED PICS: " + records);

        if (updateVectors == null  ||  updateVectors.size() == 0) {
            pout("(no vector updates)");
            return;
        }

        if (!INSERT_PIC) {
            pout("==> no-insert: skipping " + updateVectors.size() + " updates");
            return;
        }

        pout("==== updating vectors, cmds: " + updateVectors.size());

        List picList = new ArrayList<>(fs_pics);

        for (int i=0; i<updateVectors.size(); i++) {

            String[] args = updateVectors.get(i);

            pout("updateVectors: " + String.join(" ", args));

            PictureDao.updateVectors(args, picList);

        }


    }

    private static List<String[]> updateVectors = new ArrayList<>();

    private static void loadUpdateVectors(String imgDescPath) {

        File picvecs = ConfigUtil.compileConfigDir("picture_vectors");

        if (picvecs == null  ||  !picvecs.exists()) {
            pout("(No picture vectors configured)");
            return;
        }
        int linect = 0;
        try {
            BufferedReader in = new BufferedReader(new FileReader(picvecs));
            String line;

            while ((line = in.readLine()) != null) {

                linect++;

                line = line.trim().replaceAll(" +", " ");

                if (line.length() == 0  ||  line.startsWith("#")) {
                    continue;
                }

                String[] args = line.split(" ");

                if ("pairvecs".equals(args[0])) {
                    // TODO could check if vectors need regeneration,
                    // if not, use and specify archives
                    pout("Import - skipping pairvecs for now, done by regeneration/update");
                    continue;
                }
                PictureDao.checkVectorArgs(args, line);

                updateVectors.add(args);
            }

        } catch (Exception e) {
            e.printStackTrace();
            err("Reading " + picvecs + ": " + e);
        }
        pout("Loaded " + updateVectors.size() + " vector cmds");
    }

    public static void main(String[] args) {

        if (args.length < 3) {
            usage("arg count");
        }

        if ("list".equals(args[0])) {

            // pre-inserts/db - build real_image_orient
            //      for a list of archives

            String outfile = args[1];
            String images_dir = args[2];

            int[] archives = new int[args.length-3];

            try {
                for (int i=3; i<args.length; i++) {
                    archives[i-3] = Integer.parseInt(args[i]);
                }
            } catch (NumberFormatException nfe) {
                err("Expected integer archive numbers");
            }

            MiscUtil.listImageFiles(outfile, images_dir, archives);

            return;
        }

        // check args for actual import

        if (args.length != 4) {
            usage("arg count: expected 4 got " + Arrays.toString(args));
        }

        // 0: option

        String option = args[0];

        if ("all".equals(option)) {
            INSERT_PIC = true;
            INSERT_KWD = true;
        } else if ("kwd".equals(option)) {
            // not in use 2024_01 - revivable w/ image classification
            INSERT_KWD = true;
            INSERT_PIC = false;
        } else {
            usage("bad option: " + option);
        }

        // images dir, image_desc dir

        File imagesDir = MiscUtil.checkDir(args[1]);
        File imgDescPath = MiscUtil.checkDir(args[2]);

        // images/<archive>, image_desc/arch<archive>

        String archDirName = args[3];
        int archiveCode = -1;
        try {
            archiveCode = Integer.parseInt(archDirName);
        } catch (NumberFormatException nfe) {
            usage("Parsing archive_num [" + archDirName + "]: " + nfe);
        }

        File imageDir = MiscUtil.checkDir(imagesDir.getPath() + "/" + archDirName);
        File colorDir = MiscUtil.checkDir(imgDescPath.getPath() + "/0_color/" + archDirName);
        //File histoDir = MiscUtil.checkDir(imgDescPath + "/2_hist/pics/ml_concat/" + archDirName);

        loadUpdateVectors(imgDescPath.getPath());

        // load data

        loadHashToStringFromDir(colorDir, colorsHash);
        //loadHashToFloatFromDir(histoDir, histosHash);

        loadFS(imageDir, archiveCode);

        pout("Images/metadata loaded, getting color/density data");

        Picture p2 = null;
	    try {
            for (Picture p : fs_pics) {
                p2 = p; // for excep
                getColorInfo(p);
                //getHistosInfo(p);
            }
	    } catch (Exception e) {
	        e.printStackTrace();
	        err("Ouch: fs_pic: " + p2.id);
	    }

        popTables();

        pout("tables pop'd.. images are there");

    }

    private static List<Keywords> kwds_l = new LinkedList<>();

    private static void handleKwds(String line, Picture p,
                                   ID id, String coder)
            throws Exception {

        line = line.substring(2);
        for (int i=0; i<line.length(); i++) {
            char c = line.charAt(i);
            if (c != ' '  &&
                c != '_'  &&
                c != '?'  &&
                c != '+'  &&
                c != '@'  &&
                    !Character.isAlphabetic(c)  &&
                    !Character.isDigit(c)) {
                throw new Exception("picLine 3: illegal char ["+c+"]");
            }
        }
        if (line.contains("blur")) {
            p.blur = true;
        }
        if (line.contains("sign")) {
            p.sign = true;
        }
        if (line.contains("number")) {
            p.number = true;
        }

        String[] words = line.split("\\s+");
        Set<String> hs = new HashSet<>();
        for (String word : words) {
            word = word.trim();
            if (word.length() == 0) {
                throw new Exception("picLine 1or3: empty word");
            }
            List<String> pw = KwdUtil.munge(word);
            for (String w : pw) {

                // screen for warm blood-ish
                if (KwdUtil.warmish(w)) {
                    p.people = true;
                }
                hs.add(w);
                if (KwdUtil.facish(word)) {
                    p.face = true;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String k : hs) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(k);
        }

        Keywords k = new Keywords();
        k.id = id.id;
        k.type = "pic";
        k.coder = coder;
        k.keywords = sb.toString().trim();
        kwds_l.add(k);
    }

    private static void loadKwds(int archiveCode, File kwdFile, boolean first) {

        // first file has extra data
        first = false; // try doing without - legacy

        int lineN = 1;
        String line = null;
        Picture p = null;
        ID id = null;
        int picN = 0;
        int picLine = -1;

        StringBuilder comments = new StringBuilder();

        HashCount lighting = new HashCount();
        HashCount place = new HashCount();

        int addedEquals = 0;
        int standout2 = 0;

        try {
            pout("Reading Keywords: " + kwdFile);
            BufferedReader in = new BufferedReader(new FileReader(kwdFile));

            // first line is special

            line = in.readLine();
            if (line == null) {
                throw new Exception("Empty file: " +
                                     kwdFile.getAbsolutePath());
            }
            int t = 0;
            while (!Character.isAlphabetic(line.charAt(t))) t++;
            line = line.substring(t);


            String tag = "PhobrainKwd archive=";
            if (!line.startsWith(tag)) {
                pout("[" + line + "] [" + Character.getType(line.charAt(0)) + "]");
                pout("[" + tag + "]");
                throw new Exception("Bad tag, expected '" + tag + "': " + line);
            }

            int ix = line.indexOf(' ', tag.length());
            if (ix == -1  ||  ix == tag.length()) {
                throw new Exception("Bad tag, expected archive=x coder=y: " + line);
            }

            String archive2 = line.substring(tag.length(), ix);
            if (!archive2.equals(Integer.toString(archiveCode))) {
                throw new Exception("Wrong archive, expected " + archiveCode +
                                    ": " + line);
            }

            String tag2 = "coder=";
            String coder = line.substring(ix+1);
            if (!coder.startsWith(tag2)) {
                throw new Exception("Bad tag, expected coder: " + line);
            }
            coder = coder.substring(tag2.length());
            // TODO - make sure coder is in dbase and happy

            // the other lines are special too

            while ((line = in.readLine()) != null) {
                line = line.trim();
                lineN++;
                if ("--QUIT".equals(line)) {
                    pout("GOT --QUIT");
                    break;
                }
                if (line.startsWith("#")) {
                    if (p != null) {
                        throw new Exception("Wrapped pics");
                    }

                    // TODO - handle more file names
                    if (!line.startsWith("#img")  &&
                        !line.startsWith("#IMG")) {
                        throw new Exception("Bad '#' line");
                    }
                    id = new ID(archiveCode, line.substring(1));

                    // id is <archive>/stripped(fnamBody),
                    // with seq, [seq2] scanned from the scanned fnameBody.

                    p = idPics.get(id.id);
                    if (p == null) {
                        throw new Exception("No file w/ 0kwd id: " + id.id +
                                            " [" + line + "]");
                    }
                    p.xid = -99; // HACK

                    comments.setLength(0);

                    picLine = 1;
                } else if (line.length() == 0) {
                    if (p != null) {
                        picN++;
                        String com = comments.toString();
                        if (com.length() > 0) {
                            p.comments = com;
                        }
                        p = null;
                    }
                } else if (line.startsWith("--")) {
                    // comment
                } else if (p == null) {
                    throw new Exception("No image");
                } else {
                    // got a line to parse
                    switch (picLine) {
                        case 1:
                            if (first) {
                                String[] ss = line.split(",");
                                if (ss.length != 3) {
                                    throw new Exception(
                                          "picLine 1: need 3 fields");
                                }

                                String s = ss[0].trim();
                                if (s.equals("outdoors")) {
                                    p.outdoors = true;
                                } else if (!s.equals("indoors")) {
                                    throw new Exception(
                                           "picLine 1: bad 1st field");
                                }

                                s = ss[1].trim();
                                lighting.add(s);
                                p.lighting = s;
                                s = ss[2].trim();
                                s = s.substring(0, s.length()-1);
                                if (!s.equals("up")  &&
                                    !s.equals("down")  &&
                                    !s.equals("level")) {
                                    throw new Exception(
                                           "picLine 1: bad 3rd field");
                                }
                                p.angle = s;

                                picLine++;
                                break;
                            }

                            if (line.startsWith("% ")) {
                                handleKwds(line, p, id, coder);

                                picLine = 4;
                                break;
                            } else if (line.startsWith("$")) {
                                throw new Exception("picLine 1: bad start '$'");
                            }

                            picLine++;

                            // not first: go into next case

                        case 2:

                            if (line.length() == 0) {
                                throw new Exception("picLine 2: empty");
                            }

                            p.place = line;
                            place.add(line);

                            picLine = 3;
                            break;

                        case 3:

                            if (!line.startsWith("% ")) {
                                throw new Exception("picLine 3: no '% '");
                            }
                            handleKwds(line, p, id, coder);

                            picLine = 4;
                            break;

                        case 4:
                            if (line.startsWith("% ")) {
                                throw new Exception("picLine 4: dupe '% '");
                            }
                            // not enforcing order on &/@/+/=

                            if (line.startsWith("%S")) { // ignore for now
                            } else if (line.startsWith("+")) {
                                if (!line.equals("+")) {
                                    throw new Exception("+: line too long");
                                }
                                p.props.put("standout2", "");
                                standout2++;
                            } else {
                                // starting comments
                                comments.append(line);
                                picLine = 5;
                            }
                            break;

                        case 5:

                            String c = line.substring(0, 1);
                            if ("%&@".contains(c)) {
                                throw new Exception(
                                     "Unexpected in comments: " + c);
                            }
                            comments.append(line);
                            break;

                        default:
                            throw new Exception("Unexpected picLine: " +
                                                picLine);
                    }
                }
            }
            pout("LINES: " + lineN);
            pout("PICS:  " + picN);
            pout("standout2:  " + standout2);

        } catch (Exception e) {
            e.printStackTrace();
            err("LINE " + lineN + ":\n[" + line + "]");
        }
    }
}
