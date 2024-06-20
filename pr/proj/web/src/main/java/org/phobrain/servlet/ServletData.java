package org.phobrain.servlet;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  ServletData - mem cache for db and config, 
 **     used mostly by GetEngine.
 **
 */

import org.phobrain.util.ConfigUtil;
import org.phobrain.util.MiscUtil;
import org.phobrain.util.ListHolder;

import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.PictureDao;
import org.phobrain.db.dao.KeywordsDao;
import org.phobrain.db.dao.PairDao;
import org.phobrain.db.dao.PairTopDao;
import org.phobrain.db.dao.UniquePairDao;
import org.phobrain.db.dao.ApprovalDao;
import org.phobrain.db.record.Picture;
import org.phobrain.db.record.ApprovedPair;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Properties;

import java.util.stream.Collectors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;

import javax.naming.NamingException;

import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServletData {

    private static Logger log = LoggerFactory.getLogger(ServletData.class);

    // calling the constructor here hangs sync on the parallel loads
    // for some reason
    private static ServletData single = null; 

    public static ServletData get() {
        synchronized(ServletData.class) {
            if (single == null) {
                single = new ServletData();
            }
        }
        return single;
    }

    // default archives for views to select from
    static List<Integer> view0Archives;  

    // PAYLOAD - ApprovedPairs - for quick rrandom selection

    static List<ApprovedPair> approvedH; // all H 1's
    static List<ApprovedPair> approvedV; // all V 1's

    // PAYLOAD - Archives, Pictures

    static Map<Integer, int[]> viewArchives = new HashMap<>();

    // for random selection
    static Map<Integer, List<String>> picListsH = new HashMap<>();  // all per view
    static Map<Integer, List<String>> picListsV = new HashMap<>();  // all per view

    static Map<Integer, Set<String>> picSetViewsH = new HashMap<>();
    static Map<Integer, Set<String>> picSetViewsV = new HashMap<>();

    static Set<String> verticals;
    static Set<String> horizontals;
    static Set<String> face;
    static Set<String> people;

    static List<String> getPicList(int viewNum, String orient) {

        if ("v".equals(orient)) {
            return picListsV.get(viewNum);
        }
        return picListsH.get(viewNum);
    }

    static Set<String> getPicSet(int viewNum, String orient) {

        if ("v".equals(orient)) {
            return picSetViewsV.get(viewNum);
        }
        return picSetViewsH.get(viewNum);
    }

    // v and h
    public static int[] loadBadIds(Connection conn, int view, String[] cmd)
            throws SQLException {

        // keep view 0 pure, since others select from it
        if (view < 1  ||  picListsV.get(view) == null) {
            throw new SQLException("loadBadIds: bad view " + view);
        }

        if (cmd.length != 2) {
            throw new SQLException("loadBadIds: len!=2: 'bad <%>'" + 
                                        cmd.length);
        }
        double pct = 0;
        try {
            pct = Double.parseDouble(cmd[1]);
        } catch (NumberFormatException nfe) {
            throw new SQLException("loadBadIds: parsing pct " + nfe);
        }

        // negative limits say search from bottom

        int v_d0lim = -1 * (int) ((pct * picListsV.get(0).size())/ 100);
        int h_d0lim = -1 * (int) ((pct * picListsH.get(0).size())/ 100);

        List<String> picListV = PictureDao.getPicList(conn, view0Archives, 
                                                        true, v_d0lim);
        List<String> picListH = PictureDao.getPicList(conn, view0Archives, 
                                                        false, h_d0lim);

        log.info("loadBadIds view " + view + 
                 ": v/h " + picListV.size() + "/" + picListH.size());

        picListsV.put(view, picListV);
        picListsH.put(view, picListH);
        picSetViewsH.put(view, new HashSet<>(picListH));
        picSetViewsV.put(view, new HashSet<>(picListV));

        return new int[] { picListV.size(), picListH.size() };
    }

    // v and h
    public static int[] loadKwdIds(Connection conn, int view, String[] kwds) 
            throws SQLException {

        // keep view 0 pure, since others select from it
        if (view < 1  ||  picListsV.get(view) == null) {
            throw new SQLException("loadKwdIds: bad view " + view);
        }
log.warn("KWD ID's - NO d0 Cut in case it matters sizes/0: " + 
picSetViewsV.get(0).size() + "/" +
picSetViewsH.get(0).size());

        Set<String> ids = new HashSet<>();

        int start = 0;
        if (kwds[0].equals("kwd")) {
            start++;
        }
        for (int i=start; i<kwds.length; i++) { // 0th is 'kwd'
            ids.addAll(KeywordsDao.getIdsCoderKwd(conn, "m", kwds[i]));
        }

        List<String> picListV = new ArrayList<>();
        List<String> picListH = new ArrayList<>();
        for (String id : ids) {
            if (verticals.contains(id)) {
                if (picSetViewsV.get(0).contains(id)) { // no off-road
                    picListV.add(id);
                }
            } else {
                if (picSetViewsH.get(0).contains(id)) { // no off-road
                    picListH.add(id);
                }
            }
        }
        log.info("loadKwdIds view " + view + 
                 ": ids: " + ids.size() + 
                 ": v/h " + picListV.size() + "/" + picListH.size());

        picListsV.put(view,  picListV);
        picListsH.put(view,  picListH);
        picSetViewsH.put(view, new HashSet<>(picListH));
        picSetViewsV.put(view, new HashSet<>(picListV));

        return new int[] { picListV.size(), picListH.size() };
    }

    // private static int INTRO_PREF_IMAGES = 100;

    private static List<String> intersectList(Set<String> set,
                                              List<String> list) {
        List<String> ret = new ArrayList<>();
        for (String id : list) {
            if (set.contains(id)) {
                ret.add(id);
            }
        }
        return ret;
    }

/*
    // PAYLOAD - Pairs

    static List<String[]> topPairsV = null;
    static List<String[]> topPairsH = null;

    static class pWorker implements Runnable {

        private final BlockingQueue<String> pQ;
        private final CountDownLatch latch;

        private final String metaFileDir;

        public pWorker(BlockingQueue<String> pQ, CountDownLatch latch,
                       String metaFileDir) {
            this.pQ = pQ;
            this.latch = latch;

            this.metaFileDir = metaFileDir;
        }

        @Override
        public void run() {

            String lastMeta = "none";

            while(true) {

                try {
                    String meta = pQ.take();
                    if ("".equals(meta)) {
                        log.info("pWorker done: " + lastMeta);
                        break;
                    }
                    lastMeta = meta;
                    handle(meta);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            latch.countDown();
        }

        private void handle(String meta) {
            pWorkIt(metaFileDir, meta);
        }

        static void pWorkIt(String metaFileDir, String meta) {

            log.info("pWorkIt: " + meta);

            List<String[]> topPairs = null;

            if (!meta.startsWith("db")) {
                String path = meta;
                log.info("Loading map: " + path); 
                File metadump = new File(path);
                if (!metadump.exists()) { // impossible unless moved just now
                    log.error("CONFIG: meta file DOES NOT EXIST: " + path);
                    System.exit(1); // exceptions are eaten
                }

                try {
                    InputStream file = new FileInputStream(metadump);
                    InputStream buffer = new BufferedInputStream(file);
                    ObjectInput input = new ObjectInputStream (buffer);

                    log.info("reading " + path);
                    topPairs = (List<String[]>) input.readObject();
                    log.info("read " + path);

                    input.close(); buffer.close(); file.close();
                } catch (Exception e) {
                    log.error("Reading " + path, e);
                    throw new RuntimeException(e);
                }
            } else {

                String orient = (meta.endsWith("V") ? "v" : "h");

                String path = metaFileDir + "/tpp" + meta;

                Connection conn = null;

                try {
                    conn = DaoBase.getConn();

                    log.info("loading " + meta);
                    topPairs = PairDao.getTopPairs(conn, orient, 5000);
                    log.info("loaded " + meta + ": " + topPairs.size());
                    conn.close();
                    conn = null;

                    File out = new File(path);
                    if (out.exists()) {
                        throw new RuntimeException("Exists: " + path);
                    }
                    OutputStream ofile = new FileOutputStream(out);
                    OutputStream obuffer = new BufferedOutputStream(ofile);
                    ObjectOutput output = new ObjectOutputStream(obuffer);

                    log.info("write " + path);
                    output.writeObject( topPairs );
                    log.info("write done " + path);

                    output.close(); obuffer.close(); ofile.close();
                } catch (Exception e) {
                    log.error("DB+write " + path, e);
                    throw new RuntimeException(e);
                } finally {
                    if (conn != null) {
                        try {
                            conn.close();
                        } catch (Exception ignore) {}
                    }
                }
            }
            if (meta.endsWith("V")) {
                topPairsV = topPairs;
            } else {
                topPairsH = topPairs;
            }
            //log.info("Loaded coderview " + coderView + 
            //                " size " + ih.map.size());
        }
    }
*/

    private static void checkPicsAvailable(Connection conn, String im_subdir) 
                throws SQLException {

        log.info("\n\n\t*** Checking pics in images.dir on disk: " + 
                                    im_subdir + "\n\n");

        int db_prob = 0;
        int file_prob = 0;

        StringBuilder h_prob = new StringBuilder();

        List<String> picListH = picListsH.get(0);
        for (String id : picListH) {
            Picture pic = PictureDao.getPictureById(conn, id);
            if (pic == null) {
                    h_prob.append(id).append("/db ");
                    db_prob++;
            } else {
                String path = im_subdir + 
                              pic.archive + "/" +
                              pic.fileName;
                if (!(new File(path).exists())) {
                    h_prob.append(id).append("-").append(path).append(" ");
                    file_prob++;
                }
            }
        }

        StringBuilder v_prob = new StringBuilder();
        List<String> picListV = picListsV.get(0);
        for (String id : picListV) {
            Picture pic = PictureDao.getPictureById(conn, id);
            if (pic == null) {
                v_prob.append(id).append("/db ");
                db_prob++;
            } else {
                String path = im_subdir +
                              pic.archive + "/" +
                              pic.fileName;
                if (!(new File(path).exists())) {
                    v_prob.append(id).append("-").append(path).append(" ");
                    file_prob++;
                }
            }
        }
        if (db_prob + file_prob == 0) {
            log.info("Pic check: all ok: " + im_subdir);
        } else {
            log.error("PIC CHECK/MISSING: db prob: " + db_prob + 
                                    "  file prob: " + file_prob + 
                                    "\nH: " + h_prob +
                                    "\nV: " + v_prob);
        }
    }

    private static List<Integer> viewNums = new ArrayList<>();
    private static List<String> viewL = new ArrayList<>();
    private static List<String> viewNames = new ArrayList<>();

    private static void loadViews(Connection conn) throws SQLException {

        long tt1 = System.currentTimeMillis();

        log.info("LOADING VIEWS");

        int viewNum = 0;  // viewNum 0 is special

        // TODO - scan properties for lists of props w/ prefix

        String viewName = ConfigUtil.runtimeProperty("viewname" + viewNum);
        if (viewName == null) {
            viewName = "All";
        } else {
            log.info("loadViews: local viewname0: " + viewName);
        }

        String viewDef = ConfigUtil.runtimeProperty("view" + viewNum);
        if (viewDef != null) {
            if ("dummy".equals(viewDef)) {
                throw new RuntimeException("loadViews: view0 cannot be 'dummy'");
            }
            log.info("\nCONF LOCAL DEFAULT VIEW0 " + viewNum + ": [" + viewDef + "]");
        } else {
            viewDef = "all";
            log.info("\nCONF DEFAULT VIEW " + viewNum + ": [" + viewDef + "]");
        }

        viewNums.add(viewNum);
        viewNames.add(viewName);
        viewL.add(viewDef);

        viewNum++;

        // views don't have to be sequential,
        //      but until we scan properties
        //      for prefix, let's set a silent limit
        //      and hope for the best

        for (; viewNum < 20; viewNum++) {

            viewName = ConfigUtil.runtimeProperty("viewname" + viewNum);
            viewDef = ConfigUtil.runtimeProperty("view" + viewNum);

            if (viewDef == null) {

                // take a breath and move on

                if (viewName != null) {
                    throw new RuntimeException("loadViews: no view" + viewNum +
                             " definition for named view: " + viewName);
                }
                continue;
            }

            if (viewName == null) {
                viewName = "view" + Integer.toString(viewNum);
            }

            log.info("\nCONF VIEW " + viewNum + ": [" + viewName + ": " + viewDef + "]");

            viewNums.add(viewNum);
            viewNames.add(viewName);
            viewL.add(viewDef);

        } // arbitrary-max viewNum loop

        if (viewL.size() != viewNames.size()) {
            throw new RuntimeException("loadViews: names/defn mismatch: " +
                            viewNames.size() + "/" + viewL.size());
        }
        if (viewL.size() != viewNums.size()) {
            throw new RuntimeException("loadViews: names/nums mismatch: " +
                            viewNums.size() + "/" + viewL.size());
        }

        int nviews = viewL.size(); 

        verticals = PictureDao.getPicsBool(conn, "vertical", true);
        horizontals = PictureDao.getPicsBool(conn, "vertical", false);

        face = PictureDao.getPicsBool(conn, "face", true);
        people = PictureDao.getPicsBool(conn, "people", true);

        log.info("Init: nviews: " + nviews + 
                    " Verticals: " + verticals.size() +
                    " Face: " + face.size() +
                    " People: " + people.size());

        // V 10K H 17K
        Integer nD0CutH = null; //12224; //3.8e10;
        Integer nD0CutV = null; //8000; //4e10;

        // TODO - all indexing of picLists
        //  ?     relying on [archive ids 1..n], [view ids n+1..]

        log.info("Starting load-views thread.");

        for (int i=0; i<nviews; i++) {

            viewNum = viewNums.get(i);
            viewName = viewNames.get(i);
            viewDef = viewL.get(i);

            String ss[] = viewDef.split(" ");

            List<Integer> archives = null;

            if ("dummy".equals(viewDef)) {

                // this is what the loop fills in

                log.info("Placeholder view: " + viewNum);

                picSetViewsH.put(viewNum, new HashSet<>());
                picSetViewsV.put(viewNum, new HashSet<>());
                picListsH.put(viewNum, new ArrayList<>());
                picListsV.put(viewNum, new ArrayList<>());

                continue;

            } else if ("all".equals(viewDef)) {

                archives = PictureDao.getArchives(conn).stream().collect(Collectors.toList());

            } else if ("arch".equals(ss[0])) {

                archives = MiscUtil.intList(
                                          viewDef.substring("arch ".length()));

            } else if ("kwd".equals(ss[0])) {

                log.warn("Keywords limited as of arch11");
                loadKwdIds(conn, viewNum, ss);
                continue; // ?

            } else if ("bad".equals(ss[0])) {

                loadBadIds(conn, viewNum, ss);
                continue; // ?

            } else {
                throw new RuntimeException("Unexpected view type: " + i + ": " + viewDef);
            }

            if (viewNum == 0) {
                view0Archives = archives;
            } else {
                viewArchives.put(viewNum, archives.stream().mapToInt(ii->ii).toArray());
            }
            log.info("View " + i + " viewnum " + viewNum + " [" + viewDef + "] => " + 
                        Arrays.toString(archives.toArray()));

            // d0 is based on user pair training, so optional and warning is too much?

            List<String> picListH = PictureDao.getPicList(conn, archives, false, nD0CutH);
            if (picListH.size() == 0) {
                log.warn("View " + i + ": Relaxing d0 constraint on H");
                picListH = PictureDao.getPicList( conn, archives, false, null);
            }

            List<String> picListV = PictureDao.getPicList(conn, archives, true, nD0CutV);
            if (picListV.size() == 0) {
                log.warn("View " + i + " viewnum " + viewNum + ": Relaxing d0 constraint on V");
                picListV = PictureDao.getPicList( conn, archives, true, null);
            }

            if (viewNum == 0) {
                log.info("MASTER picLists V/H " + 
                                             picListV.size() + "/" + 
                                             picListH.size());
            }

            picListsH.put(viewNum, picListH);
            picListsV.put(viewNum, picListV);

            picSetViewsH.put(viewNum, new HashSet<>(picListH));
            picSetViewsV.put(viewNum, new HashSet<>(picListV));

        }

        log.info("INIT_T_ loadViews: " + 
                            (System.currentTimeMillis()-tt1)); // 557 millis
    }

    public static int[] getViewArchives(int viewNum) {

        return viewArchives.get(viewNum);

    }


    private static class TagGroup {
        String tagBase;
        List<String> tags = new ArrayList<>();
        TagGroup(String tagBase) {
            this.tagBase = tagBase;
        }
    }

    // pairs_[vh] nn columns

    private static List<List<String>> pairs_v_nn_cols = null;
    private static List<List<String>> pairs_h_nn_cols = null;

    public static List<List<String>> getPairsNNCols(boolean vertical) {
        return vertical ? pairs_v_nn_cols : pairs_h_nn_cols;
    }
    public static List<String> getPairsNNCols(boolean vertical, String prefix) {

        List<List<String>> tbl = getPairsNNCols(vertical);

        if ("p_".equals(prefix)) {
            return tbl.get(0);
        } else if ("n_".equals(prefix)) {
            return tbl.get(1);
        } else if ("a_".equals(prefix)) {
            return tbl.get(2);
        } else {
            log.info("No pairs_" + (vertical ? "v" : "h") + 
                     " column nn prefix: " + prefix + " Expected: p_, n_, or a_");
            return null;
        }
    }

    private static void makeTagGroups(Map<String, TagGroup> tagGroups,
                                        List<String> tags) {

        for (String tag : tags) {
            String[] fields = tag.split("\\|");
            if (fields.length == 1) {
                TagGroup tg = tagGroups.get(fields[0]);
                if (tg != null) {
                    log.error("Duplicate singleton tag: " + tag);
                    continue;
                }
                tg = new TagGroup(tag);
                tg.tags.add(tag + "|1"); // singleton
                tagGroups.put(tag, tg);
                //log.info("PUT 1x tag " + tag);
            } else {
                //log.info("F len " + fields.length);
                String split_tag = fields[0];
                TagGroup tg = tagGroups.get(split_tag);
                if (tg == null) {
                    tg = new TagGroup(split_tag);
                    tagGroups.put(split_tag, tg);
                    //log.info("PUT Nx tag " + split_tag + " from " + tag);
                }
                tg.tags.add(tag);
            }
        }
    }

    // Init from db: all v or h tags in one list from pairtop_nn_[vh], 
    //  tag groups are build on these lists
    private static List<String> pairtop_nn_v_tags = null;
    private static List<String> pairtop_nn_h_tags = null;

    public static List<String> getPairtopNNTags(boolean vertical) {
        return vertical ? pairtop_nn_v_tags : pairtop_nn_h_tags;
    }

    private static Map<String, TagGroup> pairtop_nn_v_tag_groups = new HashMap<>();
    private static Map<String, TagGroup> pairtop_nn_h_tag_groups = new HashMap<>();

    private static List<String> indexedImagenetVectors = new ArrayList<>();
    private static List<String> unindexedImagenetVectors = new ArrayList<>();

    private static void initNNConfig(Connection lconn) throws SQLException {

        long tt1 = System.currentTimeMillis();
        
        log.info("NN Pic Config..");

        String s = ConfigUtil.runtimeProperty("indexed.imagenet.vectors");
        if (s != null) {
            s = s.trim();
            String[] ss = s.split(",");
            for (String t : ss) {
                indexedImagenetVectors.add(t);
            }
        }
        s = ConfigUtil.runtimeProperty("unindexed.imagenet.vectors");
        if (s != null) {
            s = s.trim();
            String[] ss = s.split(",");
            for (String t : ss) {
                unindexedImagenetVectors.add(t);
            }
        }

        log.info("NN Pair Config..");
        
        if (PairDao.hasD0("v")) {
            pairs_v_nn_cols = PairDao.getPairNNCols(lconn, true);
        }
        if (PairDao.hasD0("h")) {
            pairs_h_nn_cols = PairDao.getPairNNCols(lconn, false);
        }

        int vtot = 0;
        int htot = 0;
        if (pairs_v_nn_cols != null) {

            for (int i=0; i<2; i++) { // neg==0, avg==1

                int sz = pairs_v_nn_cols.get(i).size();

                vtot += sz;

                if (sz == 0) {
                    log.warn("No pairs_v_nn_cols." + (i==0?"neg":"avg"));
                    //System.exit(1);
                }
            }
        }
        if (pairs_h_nn_cols != null) {
            for (int i=0; i<2; i++) { // neg==0, avg==1

                int sz = pairs_h_nn_cols.get(i).size();
                htot += sz;
                if (sz == 0) {
                    log.warn("No pairs_h_nn_cols." + (i==0?"neg":"avg"));
                }
            }
        }
        //boolean fatal = false;
        if (pairs_v_nn_cols == null  ||
            pairs_v_nn_cols.get(1).size() == 0) {

            log.warn("no avg nn pairs_v cols");
            //fatal = true;
        } else {
            log.info("pairs_v_nn avg-type cols: " + 
                            Arrays.toString(pairs_v_nn_cols.toArray()));
        }
        if (pairs_h_nn_cols == null  ||
            pairs_h_nn_cols.get(1).size() == 0) {

            log.warn("no avg nn pairs_h cols");
            //fatal = true;
        } else {
            log.info("pairs_h_nn avg-type cols: " + 
                            Arrays.toString(pairs_h_nn_cols.toArray()));
        }
        //if (fatal) {
        //    System.exit(1);
        //}

        if (PairTopDao.hasTable("nn", "v")) {
            pairtop_nn_v_tags = PairTopDao.getPairtopNNTags(lconn, true);
            log.info("pairtop_nn_v: " + Arrays.toString(pairtop_nn_v_tags.toArray()));
            makeTagGroups(pairtop_nn_v_tag_groups, pairtop_nn_v_tags);
        } else {
            log.warn("No pairtop_nn_v");
        }
        if (PairTopDao.hasTable("nn", "h")) {
            pairtop_nn_h_tags = PairTopDao.getPairtopNNTags(lconn, false);
            log.info("pairtop_nn_h: " + Arrays.toString(pairtop_nn_h_tags.toArray()));
            makeTagGroups(pairtop_nn_h_tag_groups, pairtop_nn_h_tags);
        } else {
            log.warn("No pairtop_nn_h");
        }

        // Sigma opts:
        //  view.html (Phob: Search Mode: AI) 
        //  curate.html 

        initSigmas(); 

        log.info("INIT NN config: " + (System.currentTimeMillis()-tt1) + " ms");
    }

    public static List<String> getPairtopNNTags(String tagBase,
                                            boolean vertical, 
                                            boolean matchPrefix) {

        List<String> ret = new ArrayList<String>();

        if (matchPrefix) {
            List<String> l = getPairtopNNTags(vertical);
            for (String s : l) {
                if (s.startsWith(tagBase)) {
                    ret.add(s);
                }
            }
            return ret;
        }

        Map<String, TagGroup> tagGroups = vertical ? pairtop_nn_v_tag_groups 
                                                    : pairtop_nn_h_tag_groups;
        TagGroup tg = tagGroups.get(tagBase);
        if (tg == null) {
            log.error("No tag: " + tagBase);
            return null;
        }
        return tg.tags;
    }

    /**
     **  Button responses, Sigma1..5 in view.html Phob->Search Mode: AI
     **  Each String is <tbl> <flags|col[s]>
     **/

    private static String[] vSigmas = new String[5];
    private static String[] hSigmas = new String[5];

    private static String[] getSigmas(boolean vertical) {
        return vertical ? vSigmas : hSigmas;
    }

    public static String getSigma(boolean vertical, int sigma) {
        String[] sigmas = getSigmas(vertical);
        return sigmas[sigma-1];
    }

    public static Map<String, TagGroup> getSigmaTagGroups(boolean vertical) {
        return vertical ? pairtop_nn_v_tag_groups : pairtop_nn_h_tag_groups;
    }

    private static void initSigmas() {

        log.info("initSigmas()");

        // using size=2 arrays to iterate over v, h

        boolean[] v = { true, false };
        String[] o = { "v", "h" };
        Object[] a_pairs_cols = { pairs_v_nn_cols, pairs_h_nn_cols };
        String[][] a_sigmas = { vSigmas, hSigmas };


        for (int i=0; i<2; i++) {  // v, h

            boolean vertical = v[i];
            String orient = o[i];
            List<List<String>> pairs_nn_cols = (List<List<String>>)
                                                    a_pairs_cols[i];
            String[] sigmas = a_sigmas[i];

            List<String> pairtop_nn_tag_list = getPairtopNNTags(v[i]);

            //log.info("pairtopNNTags: " + Arrays.toString(
            //                                pairtop_nn_tag_list.toArray()));

            Map<String, TagGroup> tagGroups = getSigmaTagGroups(v[i]);

            // Sigma1..5 in view.html Phob->Search Mode: AI option

            for (int j=1; j<6; j++) {

                log.info("Sigma " + j);

                String key = "sigma" + j + orient;
                String def = ConfigUtil.runtimeProperty(key);
                if (def == null) {
                    log.error("No config: " + key);
                    continue;
                }
                log.info("Sigma def: " + def);

                String[] fields = def.split("\\s+");
                if (fields.length < 2) {
                    log.error("Short config: " + key + ": " + def);
                    continue;
                }
                
                String expect = "kwd: expected one of {p_xxx pt_xxx d_xxx}: ";

                String kwd = fields[0];
                String[] type = kwd.split("_");
                if (type.length != 2) {
                    log.error(expect + kwd);
                    System.exit(1);
                }
                if (!"p".equals(type[0])  &&  !"pt".equals(type[0])  && !"d".equals(type[0])) {
                    log.error(expect + kwd);
                    System.exit(1);
                }

                if ("p".equals(type[0])) {

                    // pairs_[vh]

                    if ("avg".equals(type[1])) {

                        if (fields.length != 2) {
                            log.error("FATAL/args kwd: p_avg <a_col>: " + def);
                            System.exit(1);
                        }
                        String a_col = fields[1];

                        if (!"any".equals(a_col)  &&
                            !pairs_nn_cols.get(1).contains(fields[1])) {  // avg

                            log.error("FATAL: kwd: p_avg <a_col>: " +
                                        "No such col in pairs tbl: " + a_col +
                                        "\np_avg: must be 'any' or one of: " + 
                                          Arrays.toString(pairs_nn_cols.get(1)
                                                                .toArray())
                                          );
                            System.exit(1);
                        }
                        sigmas[j-1] = "p_avg " + a_col;

                    } else if ("negpos".equals(type[1])) {

                        if (fields.length != 3) {
                            log.error("FATAL: kwd: " +
                                      " p_negpos <n_col> <p_col>: " + def);
                            System.exit(1);
                        }
                        String n_col = fields[1].toLowerCase();
                        String p_col = fields[2].toLowerCase();

                        if (!"any".equals(n_col)  &&
                            !pairs_nn_cols.get(0).contains(n_col)) {

                            log.error("FATAL: kwd: p_negpos <n_col> <a_col>: " +
                                          "not 'any' or n_col: " + n_col +
                                          "\nn_xxx cols " +
                                          Arrays.toString(pairs_nn_cols.get(0)
                                                                .toArray())
                                          );
                            System.exit(1);
                        }
                        if (!"any".equals(p_col)  &&
                            !pairs_nn_cols.get(1).contains(p_col)) {

                            log.error("FATAL: kwd: p_negpos <n_col> <p_col>: " +
                                          "not 'any' or db a_col: " + 
                                          p_col);
                            System.exit(1);
                        }

                        sigmas[j-1] = "p_np " + n_col + " " + p_col; // :-)

                    } else {
                        log.error("FATAL: kwd p_xxx:" +
                                            " expected p_avg or p_negpos: " +
                                            kwd);
                        System.exit(1);
                    }


                } else if ("pt".equals(type[0])) {
                    
                    // pairtop_nn_[vh]

                    if (fields.length < 2) {
                        log.error("Short config: " + key + ": " + def);
                        continue;
                    }

                    if ("match".equals(type[1])) {

                        StringBuilder sb = new StringBuilder();
                        if ("fname".equals(fields[1])) {
                            // leave it all to chance
                            for (int k=1; k<fields.length; k++) {
                                String tag = fields[k];
                                sb.append(tag).append(" ");
                            }
                        } else {
                            // formerly gave a damn
                            for (int k=1; k<fields.length; k++) {

                                String tag = fields[k];

                                if (!pairtop_nn_tag_list.contains(tag)) {
                                    log.error(key + " match: no such " + 
                                              orient + " pairtop tag: " + tag);
                                    log.error("ptnnt: " + Arrays.toString(
                                            pairtop_nn_tag_list.toArray()));
                                    continue;
                                }
                                sb.append(tag).append(" ");
                            }
                        }
                        if (sb.length() == 0) {
                            log.error("No tags: " + def);
                            continue;
                        }

                        sb.deleteCharAt(sb.length()-1);

                        sigmas[j-1] = "pt_match " + sb;

                    } else if ("group".equals(type[1])) {

                        StringBuilder sb = new StringBuilder();

                        for (int k=1; k<fields.length; k++) {

                            String grp = fields[k];

                            TagGroup tg = tagGroups.get(grp);
                            if (tg == null) {
                                log.error("No such group tag: " + 
                                        key + ": " + def + " [" + grp + "]");
                                continue;
                            }
                            for (String s : tg.tags) {
                                sb.append(s).append(" ");
                            }
                        }
                        if (sb.length() == 0) {
                            log.error("No tags: " + key + ": " + def);
                            continue;
                        }

                        sb.deleteCharAt(sb.length()-1);

                        sigmas[j-1] = "pt_group " + sb;

                    } else {
                        log.error("Type [" + fields[0] + 
                                "] not in pt_[match|group]: " + 
                                key + ": " + def);
                        continue;
                    }
                } else if ("d".equals(type[0])) {

                    log.info("d + " + type[1]); // not used 1st version
log.error("d????");
System.exit(1);
                    TagGroup tg = tagGroups.get("");

                } else {
                    log.error(expect + kwd);
                    System.exit(1);
                }
                if (sigmas[j-1] == null) {
                    log.error("No sigma " + orient + "." + j + ": " + def);
                } else {
                    log.info("Sigma " + key + ": " + orient + "." + j + ": " + 
                                                    sigmas[j-1]);
                }
log.info("Sigma 1..5 done");
            }
log.info("Sigma v/h done");
        }
    }

    private static boolean init = false;

    private ServletData() {
        if (init) {
            throw new RuntimeException("ServletData(): already ran init()");
        }
        init = true;

        //System.out.println("ServletData constructor");
        log.info("ServletData constructor");
        Connection conn = null;
        try {

            String trim = ConfigUtil.runtimeProperty("trimdb");
            boolean trimDB = (trim != null  && "true".equals(trim));
            if (trimDB) {
                DaoBase.useTrimDB();
            }

            conn = DaoBase.getConn();

            // test for optional pair tables
            //   pairs_[vh]
            PairDao.testTables(conn);
            //   pairtop_[col|nn]_[vh]
            PairTopDao.testTables(conn);


// --- TODO - delete?

            log.info("Loading approved pairs in threads");
            long t1 = System.currentTimeMillis();

            final Connection conn_static = conn;
            Thread th1 = new Thread(new Runnable() {
                public void run() {
                    try {
                        approvedV = ApprovalDao.getAllApprovedPairs(
                                                    conn_static, 1, 
                                                     "v", null,
                                                     true, // d0
                                                     null, null, 
                                                     null);
                    } catch (SQLException sqe) {
                        log.error("approvedV: " + sqe);
                        System.exit(1);
                    }
                }
            });
            th1.start();
            Thread th2 = new Thread(new Runnable() {
                public void run() {
                    try {
                        approvedH = ApprovalDao.getAllApprovedPairs(
                                                    conn_static, 1, 
                                                     "h", null,
                                                     true, // d0
                                                     null, null, 
                                                     null);
                    } catch (SQLException sqe) {
                        log.error("approvedH: " + sqe);
                        System.exit(1);
                    }
                }
            });
            th2.start();

            //log.info("INIT Loading pairs: " + (System.currentTimeMillis()-t1));

            //INTRO_PREF_IMAGES = Integer.parseInt(
            //                         ConfigUtil.runtimeProperty("intro.pref.images"));


            //final Connection vconn = conn; not much diff either way
            Thread viewThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        loadViews(conn_static);
                    } catch (SQLException sqe) {
                        sqe.printStackTrace();
                        System.exit(1);
                    }
                }
            });
            viewThread.start();

            // start NN config

            //final Connection lconn = conn;
            Thread nnThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        initNNConfig(conn_static);
                    } catch (Exception e) {
                        log.error("FATAL: Exception: " + e, e);
                        log.error("\nEXITING on purpose, you, you..\n" +
                                    "Again, this is why: " + e);
                        System.err.println("ServletData: " +
                                        "EXITED HERE from initNNConfig thread");
                        System.exit(1);
                    }
                }
            });
            nnThread.start();

            //log.info("NN thread started");

            // wait for burps

            System.out.println("ServletData PARALLEL load wait..");
            log.info("ServletData PARALLEL load wait..");
            try {
                //latch.await();
                //log.info("Latch achieved: Join app pr loads");
                th1.join();
                th2.join();
                viewThread.join();
                nnThread.join();
                log.info("Joined app pr loads");
            } catch (InterruptedException ie) {
                log.error("Interrupted waiting: " + ie);
                throw new RuntimeException(ie);
            }
            System.out.println("wait done");

            log.info("Loaded in " + (System.currentTimeMillis() - t1));
/*
            log.info("topPairs: v, h: " + topPairsV.size() + ", " +
                                          topPairsH.size());
*/

            // summarize 

            StringBuilder sb = new StringBuilder("\n\n------ CONFIG\n\n");

            sb.append("-- views:\n\n");

            for (int i=0; i<viewL.size(); i++) {

                if ("dummy".equals(viewL.get(i))) {
                    continue;
                }

                int viewNum = viewNums.get(i);

                sb.append(viewNum).append(": ")
                  .append(viewNames.get(i)).append(": [ ")
                  .append(viewL.get(i)).append(" ]\n\t");

                int hsize = 0;
                int vsize = 0;
                List<String> pics = picListsH.get(viewNum);
                if (pics == null) {
                    log.warn("no picListsH for viewNum " + viewNum);
                } else {
                    hsize = pics.size();
                }
                pics = picListsV.get(viewNum);
                if (pics == null) {
                    log.warn("no picListsV for viewNum " + viewNum);
                } else {
                    vsize = pics.size();
                }

                sb.append("\tSize ").append(hsize+vsize)
                      .append(" V ").append(vsize)
                      .append(" H ").append(hsize)
                      .append("\n");

                /*
                sb.append(" First ").append(picListsFirsts[i].size());
                sb.append(" Best ").append(picListsBest[i].size()).append("\n");
                */
            }
            sb.append("\n");

            sb.append("Vert/Horiz: ").append(verticals.size()).append("/").append(horizontals.size())
              //.append("Face: ").append(face.size()).append("\n")
              //.append("People: ").append(people.size())
              .append("\n\n");

            int pv = approvedV.size();
            int ph = approvedH.size();
            sb.append("Pairs: ").append(pv + ph)
              .append(" V: ").append(pv)
              .append(" H: ").append(ph)
              .append("  ").append(PairDao.tablesAvailable())
              .append("\n\n");

            sb.append("pairtop_nn tags (V/H): ")
              .append(pairtop_nn_v_tags == null ? 0 : pairtop_nn_v_tags.size())
              .append("/")
              .append(pairtop_nn_h_tags == null ? 0 : pairtop_nn_h_tags.size())
              .append("  groups: ")
              .append(pairtop_nn_v_tag_groups == null ? 0 : pairtop_nn_v_tag_groups.size())
              .append("/")
              .append(pairtop_nn_h_tag_groups == null ? 0 : pairtop_nn_h_tag_groups.size())
              .append("\n\n");

            sb.append("Sigmas:\n\n");
            for (int i=0; i<5; i++) {
              sb.append(i+1).append(" v\t").append(vSigmas[i]).append('\n');
            }
            sb.append('\n');
            for (int i=0; i<5; i++) {
              sb.append(i+1).append(" h\t").append(hSigmas[i]).append('\n');
            }
            sb.append('\n');
/*
            sb.append("Pairs/unp/v: ").append(verticalPairsUnpaired.size())
              .append(" /h: ").append(horizPairsUnpaired.size())
              .append("\n");
*/

/*
            sb.append("-- kwd maps\n");
            Map<String, IndexHolder> m = 
                                       new TreeMap<String, IndexHolder>(views);
            Set<Map.Entry<String, IndexHolder>> es = m.entrySet();
            for (Map.Entry pair : es) {
                IndexHolder ih = (IndexHolder) pair.getValue();
                int ct = 0;
                Set<Map.Entry<String, ListHolder>> es2 = ih.map.entrySet();
                for (Map.Entry rec : es2) {
                    ListHolder lh = (ListHolder) rec.getValue();
                    ct += lh.size();
                }
                int avg = 0;
                if (ih.map.size() > 0) {
                    avg = ct / ih.map.size();
                }
                sb.append(pair.getKey()).append("\t").append(ih.map.size())
                  .append(" avg ").append(avg).append("\n");
            }
*/
            sb.append(ServletUtil.getAdminConfig());
            sb.append("-------------------------------------\n\n");

            log.info(sb.toString());

            System.out.println("ServletData/sysout " + sb);

            String images_dir = ConfigUtil.runtimeProperty("images.dir") + "/";
            String checkImages = ConfigUtil.runtimeProperty("check.images");
            if ("true".equals(checkImages)) {
                log.info("check.images=true => Image check");
                checkPicsAvailable(conn, images_dir);
            } else {
                log.info("\n\n\t *** SKIPPING image file check: " +
                                "check.images=false or null\n\n");
            }

/*
log.info("D0p sum v");
for (String id : picListsV[0]) {
PairDao.setD0pSum(conn, "v", id);
}
log.info("D0p sum h");
for (String id : picListsH[0]) {
PairDao.setD0pSum(conn, "h", id);
}
conn.commit();
*/
        } catch (NamingException ne) {
            log.error("init: Naming", ne);
        } catch (SQLException sqe) {
            log.error("init: DB: " + sqe, sqe);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignore) {}
            }
        }
        log.info("Init OK");
        System.out.println("ServletData constructor done");
    }

    public static ListHolder theHumanity(ListHolder lh, boolean doFace) {

        // doFace ignored for now; face first hard coded

        // face, people, TODO: [animal, vegetable, mineral]

        int start = lh.size();

        if (start == 0) {
            log.warn("theHumanity: empty list");
            return lh;
        }

        ListHolder lht = new ListHolder();
        ListHolder lhtp = new ListHolder();

        log.info("theHumanity face");
        for (int i=0; i<lh.size(); i++) {
            String id = lh.id2_l.get(i);
            if (face.contains(id)) {
                lht.id2_l.add(id);
                lht.value_l.add(lh.value_l.get(i));
            } else if (people.contains(id)) {
                lhtp.id2_l.add(id);
                lhtp.value_l.add(lh.value_l.get(i));
            } // TODO - animals, vegetables
        }
        log.info("FFFFFFFACE " + start + "-> (" + 
                lht.size() + " + " + lhtp.size() + ")");

        if (lhtp.size() > 0) {
            lht.id2_l.addAll(lhtp.id2_l);
            lht.value_l.addAll(lhtp.value_l);
        }

        return lht;
    }
}
