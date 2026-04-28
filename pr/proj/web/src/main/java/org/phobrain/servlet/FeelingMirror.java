package org.phobrain.servlet;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

//  FeelingMirror - Serves photo pairs in response to 'drawing dots' 
//                  as a reading of feelings. 
//
//                  Adapting ConceptMirror's use in view.html for training 
//                  a next generation of user-aware models.
//
//                  Includes code to use the new models so-trained, 
//                  not enabled by default since the Java-CPP interface 
//                  uses unsafe operations. NB: models remain to be
//                  proven, and a simple imagenet vector distance-based
//                  algorithm stands in and gives interesting results.
//
//                      Enabling model predictions:
//                          In this file:
//                              //import org.phobrain.predict.MLPredict;
//                              //predict = new MLPredict();
//                          In web/build.gradle:
//                              //implementation project(':mlpredict')
//                          In proj/settings.gradle:
//                              include "shared", "db", ... "predict" //, "mlpredict"
//
//     TODO - classify objects in pics, use names of objects
//             that any dots highlight for a revival of the
//             original keyword-based system.

import org.phobrain.util.ConfigUtil;
import org.phobrain.util.MathUtil;
import org.phobrain.util.MiscUtil;
import org.phobrain.util.MiscUtil.SeenIds;
import org.phobrain.util.ListHolder;
import org.phobrain.util.HashCount;
import org.phobrain.util.AtomSpec;
import org.phobrain.util.SortDoubleStrings;

import org.phobrain.predict.Predict;
import org.phobrain.predict.SimplePredict;
// for deeplearning4j/nn4j, uncomment MLPredict and use it below
// also add mlpredict in settings.gradle
//import org.phobrain.predict.MLPredict;

import org.phobrain.math.Higuchi;

import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.SessionDao;
import org.phobrain.db.dao.BrowserDao;
import org.phobrain.db.dao.UserDao;
import org.phobrain.db.dao.FeelingPairDao;
import org.phobrain.db.dao.PictureDao;
import org.phobrain.db.dao.PictureMapDao;
import org.phobrain.db.dao.PairDao;
import org.phobrain.db.dao.PairTopDao;

import org.phobrain.db.record.Session;
import org.phobrain.db.record.Browser;
import org.phobrain.db.record.Screen;
import org.phobrain.db.record.User;
import org.phobrain.db.record.DotHistory;
import org.phobrain.db.record.Picture;
import org.phobrain.db.record.PictureResponse;
import org.phobrain.db.record.PictureMap;
import org.phobrain.db.record.HistoryPair;
import org.phobrain.db.record.Pair;
import org.phobrain.db.record.ApprovedPair;
import org.phobrain.db.util.DBUtil;

import java.util.Random;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Vector;
import java.util.Collections;
import java.util.Properties;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.NamingException;

import java.sql.Connection;
import java.sql.Timestamp;
import java.sql.SQLException;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeelingMirror extends MirrorJuice {

    private static final Logger log = LoggerFactory.getLogger(FeelingMirror.class);

    @Override
    boolean checkSeen(Connection conn, long browserID, String id1, String id2)
            throws SQLException {

        return FeelingPairDao.checkSeen(conn, browserID, id1, id2);
    }

    @Override
    Set<String> getSeen(Connection conn, long browserID)
            throws SQLException {

        return FeelingPairDao.getSeen(conn, browserID);
    }

    @Override
    int countPairs(Connection conn, long browserID)
            throws SQLException {
        return FeelingPairDao.countPairs(conn, browserID);
    }
    @Override
    int countPairs(Connection conn, long browserID, String orient)
            throws SQLException {
        return FeelingPairDao.countPairs(conn, browserID, orient);
    }

    @Override
    List<HistoryPair> getLastPairs(Connection conn, long browserID, int n)
            throws SQLException {
        return FeelingPairDao.getLastPairs(conn, browserID, 10);
    }

    @Override
    SeenIds getSeen(Connection conn, Session session)
            throws SQLException {
        return getSeen(conn, session, false);
    }

    private SeenIds getSeen(Connection conn, Session session, boolean force)
            throws SQLException {

        SeenIds ret = browserSeen.get(session.browserID);
        if (ret != null) {
            if (ret.seen != null) {
                if (session.repeatPics  &&  !force) {
                    ret.seen = null;// in case user changed repeat status
                }
            } else {
                if (force  ||  !session.repeatPics) {
                    ret.seen = FeelingPairDao.getSeen(conn, session.browserID);
                    ret.vertical = FeelingPairDao.countPairs(conn,
                                                  session.browserID, "v");
                    ret.horizontal = FeelingPairDao.countPairs(conn,
                                                  session.browserID, "h");
                }
            }
            return ret;
        }

        // TODO - COLLISION network jitter may lead to collision here
        //                  if user clicks a lot and things arrive all at once
        // synchronized (anObject) would be a club

        log.info("new SeenIds browserID " + session.browserID);

        ret = new SeenIds();
        if (force  ||  !session.repeatPics) {
            ret.seen = FeelingPairDao.getSeen(conn, session.browserID);
            ret.vertical = FeelingPairDao.countPairs(conn,
                                                  session.browserID, "v");
            ret.horizontal = FeelingPairDao.countPairs(conn,
                                                  session.browserID, "h");
        }
        browserSeen.put(session.browserID, ret);

        log.info("new SeenIds browserID " + session.browserID +
                    " seen " +
                            (ret.seen == null ? "null" :
                                " size " + ret.seen.size()) +
                    " v: " + ret.vertical + " h: " + ret.horizontal);

        return ret;
    }

    // TODO - could be in MirrorJuice?
    public int getPctSeen(Connection conn, Session session,
                                           int viewNum, String orient)
            throws SQLException {
        SeenIds seenids = getSeen(conn, session);
        if (seenids.seen == null  ||  seenids.seen.size() == 0) {
            return 0;
        }
        Set<String> picSet = ServletData.getPicSet(viewNum, orient);
        if (picSet == null  ||  picSet.size() == 0) {
            return -1;
        }
        return (100 * seenids.seen.size()) / picSet.size();
    }

    @Override
    public void addSeen(Connection conn, long browserID, String orient,
                        String... ids)
            throws SQLException {

        log.info("addSeen " + browserID + " " + Arrays.toString(ids));

        if (ids.length == 0) {
            log.error("NO IDS");
            throw new IllegalArgumentException("NO IDS");
        }
        SeenIds set = browserSeen.get(browserID);
        if (set == null) {
            log.info("SET NULL " + browserID);
            set = new SeenIds();
            browserSeen.put(browserID, set);
        }
        if (set.seen == null) {
            // TODO - roll into 1 query
            set.seen = FeelingPairDao.getSeen(conn, browserID);
            set.vertical = FeelingPairDao.countPairs(conn,
                                                  browserID, "v");
            set.horizontal = FeelingPairDao.countPairs(conn,
                                                  browserID, "h");
        }
        for (String id : ids) {
            set.seen.add(id);
        }
        if ("v".equals(orient)) {
            set.vertical += ids.length;
        } else {
            set.horizontal += ids.length;
        }

        log.info("AddSeen: " + set.seen.size());

        // by the time we are adding is a good time to
        // purge any exclude
        set.exclude.clear();
    }

    private static Map<Long, Integer> browserLastOrderInSession =
                                                        new HashMap<>();
    @Override
    public int getOrderInSession(Connection conn, long browserID)
            throws SQLException {
        Integer order = browserLastOrderInSession.get(browserID);
        if (order == null) {
            int n = FeelingPairDao.countPairs(conn, browserID);
            order = n + 1;
        } else {
            order++;
        }
        browserLastOrderInSession.put(browserID, order);
        return order;
    }
/*
    List<Screen> checkPair(Connection conn, int viewNum, String orient,
                                            Session session,
                                            Set<String> picSet,
                                            ApprovedPair ap,
                                            SeenIds seenIds,
                                            String method)
            throws SQLException {

        if (!picSet.contains(ap.id1)  ||
            !picSet.contains(ap.id2)) {
            return null;
        }
        if (seenIds.contains(ap.id1)  ||
            seenIds.contains(ap.id2)) {
            return null;
        }

        // approved pairs cannot be repeated by a browser id

        if (FeelingPairDao.checkSeen(conn, session.browserID, ap.id1, ap.id2)) {
            return null;
        }

        PictureResponse pr1 = new PictureResponse();
        pr1.p = PictureDao.getPictureById(conn, ap.id1);
        if (pr1.p == null) {
            return null;
        }
        PictureResponse pr2 = new PictureResponse();
        pr2.p = PictureDao.getPictureById(conn, ap.id2);
        if (pr2.p == null) {
            return null;
        }

        pr1.method = method;
        pr2.method = method;
        String id1 = pr1.p.id;
        String id2 = pr2.p.id;
        List<Screen> scl = new ArrayList<>();
        scl.add(new Screen(session.browserID, 1, "v", id1, pr1));
        scl.add(new Screen(session.browserID, 2, "v", id2, pr2));
        return scl;
    }
*/

    @Override
    boolean haveFlow() {
        return predict != null;
    }

    private void setTmpVec(Picture p, String vector_name) {

        if ("nnl_7".equals(vector_name)) {
            p.tmp_vec = p.nnl_7;
/*
        } else if ("vgg16_2".equals(vector_name)) {
            p.tmp_vec = p.vgg16_2;
*/
        } else if ("vgg16_4".equals(vector_name)) {
            p.tmp_vec = p.vgg16_4;
        } else if ("dense_4".equals(vector_name)) {
            p.tmp_vec = p.dense_4;
        } else {
            log.warn("Unknown vector: " + vector_name + " using vgg16_4");
            p.tmp_vec = p.vgg16_4;
        }
    }

    private Map<Integer, double[][]> batches = new HashMap<>();

    /*
     *  modelMatch() - MxN on lhs[2] using flowModel
     */
    @Override
    List<Screen> modelMatch(Connection conn,
                                    UserProfile up, int viewNum, String orient,
                                    Session session, List<String> ids, ListHolder lhs[])
            throws SQLException {

        long t0 = System.currentTimeMillis();

        HistoryPair last = up.last;

        if (last == null) {
            log.info("modelMatch: no prev");
            return null;
        }
        if (last.dotCount < 3) {
            log.info("modelMatch: ndots<3: " + last.dotCount);
            return null;
        }

        // dot histograms from drawing on pics1_1,_2
        DotHistory dh = last.dotHistory;
        int[] hist1 = dh.distHist;
        int[] hist2 = dh.velocityHist;
        int[] hist3 = dh.d3angleHist;
        int[] hist4 = dh.d2angleHist;

        // decide which folded imagenet vector space to use

        String vector_name = ((dh.dots1 + dh.dots2) % 2 == 0 ? "nnl_7" : "vgg16_4");

        // gather the pic reps: the previous pair will be
        //      included with the movement data for every case
        //   leave pics handy for now
        Picture p_1_1 = PictureDao.getPictureById(conn, ids.get(0));
        Picture p_1_2 = PictureDao.getPictureById(conn, ids.get(1));
        setTmpVec(p_1_1, vector_name);
        setTmpVec(p_1_2, vector_name);

        // gather picreps for candidates on left and right

        Map<String, float[]> picreps = new HashMap<>();

        int errors = 0;

        for (String id : lhs[0].id2_l) {
            Picture p = PictureDao.getPictureById(conn, id);
            setTmpVec(p, vector_name);
            if (p.tmp_vec == null) {
                errors++;
                continue;
            }
            picreps.put(id, p.tmp_vec);
        }
        for (String id : lhs[1].id2_l) {

            if (picreps.get(id) != null) continue;

            Picture p = PictureDao.getPictureById(conn, id);
            setTmpVec(p, vector_name);
            if (p.tmp_vec == null) {
                errors++;
                continue;
            }
            picreps.put(id, p.tmp_vec);
        }

        // pack the constants into data:
        //      i.e. all data but the vectors of new pair:
        //          1st pair, histograms, 'db' data are constant

        final int len_fixed = 2 * p_1_1.tmp_vec.length +   // vecs for pair the dots were drawn on
                          hist1.length + hist2.length + hist3.length + hist4.length; // dots drawn

        final int DATA_SZ = len_fixed +
                            2 * p_1_1.tmp_vec.length;  // for the next-pair candidates - variable

        // TODO - train w/ all picvecs after dot histos
        log.info("NB for picvec len " + p_1_1.tmp_vec.length + ", eval picvecs are at " + len_fixed);

        int ROWS = lhs[0].size() * lhs[1].size();

        // the first row gets the fixed data, then copied to MxN

        double[] data1d = new double[DATA_SZ];

        int dix = 0;
        for (int i=0; i<hist1.length; i++) {
            data1d[dix++] = hist1[i];
        }
        for (int i=0; i<hist2.length; i++) {
            data1d[dix++] = hist2[i];
        }
        for (int i=0; i<hist3.length; i++) {
            data1d[dix++] = hist3[i];
        }
        for (int i=0; i<hist4.length; i++) {
            data1d[dix++] = hist4[i];
        }

        for (int i=0; i<p_1_1.tmp_vec.length; i++) {
            data1d[dix++] = p_1_1.tmp_vec[i];
        }
        for (int i=0; i<p_1_2.tmp_vec.length; i++) {
            data1d[dix++] = p_1_2.tmp_vec[i];
        }

        final int fixed_dix = dix;  // beginning of variable data

        // fixed data laid down, now copy
        //      to build the NxM cases for batch

        // lay out the memory for the whole batch

        double[][] batch = batches.get(DATA_SZ);

        if (batch == null) {

            batch = new double[ROWS][];
            for (int i=0; i<batch.length; i++) {
                batch[i] = data1d.clone();
            }
            batches.put(DATA_SZ, batch);

        } else if (batch.length < ROWS) {

            batches.remove(DATA_SZ);

            batch = new double[ROWS][];
            for (int i=0; i<batch.length; i++) {
                batch[i] = data1d.clone();
            }
            batches.put(DATA_SZ, batch);

        } else {

            // batch is ok and now is time to
            // copy instead of cloning
            log.info("Reusing for model");
            for (int i=0; i<batch.length; i++) {
                System.arraycopy(data1d, 0, batch[i], 0, fixed_dix);
            }
        }

        // plug in the NxM vecs for eval pairs

        int row = 0;

        List<SortDoubleStrings> preds = new ArrayList<>();

        for (int i=0; i<lhs[0].size(); i++) {

            // get pic 1's array & reverse byte order

            String lpic = lhs[0].id2_l.get(i);
            float[] larr = picreps.get(lpic);

            // copy pic i's array to |j rows|

            int row_i = row;

            for (int j=0; j<lhs[1].size(); j++) {
                int dix2 = fixed_dix;
                for (int c=0; c<larr.length; c++) {
                    batch[row][dix2++] = larr[c];
                }
                row++;
            }

            // copy in the pic j's vecs

            row = row_i;
            for (int j=0; j<lhs[1].size(); j++) {

                String rpic = lhs[1].id2_l.get(j);
                float[] rarr = picreps.get(rpic);
                int dix2 = fixed_dix + rarr.length;
                for (int c=0; c<rarr.length; c++) {
                    batch[row][dix2++] = rarr[c];
                }
                preds.add(new SortDoubleStrings(new String[] {lpic, rpic}));
                row++;
            }
        }

        double[] mpreds = null;

        try {
            mpreds = predict.predict(vector_name, batch);
        } catch (Exception e) {
            log.error("PREDICT " + e, e);
            return null;
        }
        if (mpreds.length != preds.size()) {

            log.error("model preds " + mpreds.length +
                        " != expected " + preds.size());
        }
        // unsorted log.info("PREDS: " + Arrays.toString(mpreds));

        for (int i=0; i<mpreds.length; i++) {
            preds.get(i).setVal(mpreds[i]);
        }

        Collections.sort(preds);

        if (preds.get(0).value > 1.0) {

            // typical ~35.x with poincare distance in SimplePredict.
            // Conforming somewhat to ML 0..1 for consistency.

            double sub = Math.floor(preds.get(0).value);
            log.info("Scaling off " + sub + " on min " + preds.get(0).value);
            for (SortDoubleStrings sds : preds) {
                sds.value -= sub;
            }
        }

        StringBuilder sb = new StringBuilder("\nSDS\n");
        for (int i=0; i<5; i++) {
            SortDoubleStrings sds = preds.get(i);
            sb.append("  ").append(sds.value).append(" ")
                            .append(sds.strings[0]).append(" ").append(sds.strings[1]).append("\n");
        }
        sb.append(" ...\n");
        for (int i=preds.size()-6; i<preds.size(); i++) {
            SortDoubleStrings sds = preds.get(i);
            sb.append("  ").append(sds.value).append(" ")
                            .append(sds.strings[0]).append(" ").append(sds.strings[1]).append("\n");
        }
        log.info(sb.toString());

        SortDoubleStrings sds0 = preds.get(0);
        SortDoubleStrings sdsN = preds.get(preds.size()-1);

        // choosable max or min while exploring/debugging

        SortDoubleStrings sds;
        String method = Integer.toString(p_1_1.tmp_vec.length);
        if (dh.dots2 < dh.dots1) {
            sds = sds0;
            method += "min";
        } else {
            sds = sdsN;
            method += "max";
        }
        if (lhs[0].size() == lhs[1].size()) {
            method += lhs[0].size() + "|";
        } else {
            method += lhs[0].size() + "x" + lhs[1].size();
        }

        double avg = (sdsN.value + sds0.value) / 2.0;

        log.info("PRED " + method + " spread " + (sdsN.value - sds0.value) + 
                            " avg " + avg + " N=" + preds.size());

        PictureResponse pr1 = new PictureResponse();
        pr1.p = PictureDao.getPictureById(conn, sds.strings[0]);
        PictureResponse pr2 = new PictureResponse();
        pr2.p = PictureDao.getPictureById(conn, sds.strings[1]);

        pr1.method = method;
        pr2.method = method;

        if (false) {
            Picture p1 = PictureDao.getPictureById(conn, sds0.strings[0]);
            Picture p2 = PictureDao.getPictureById(conn, sds0.strings[1]);
            log.info(
                    "\nPRZ min " + p1.archive + "/" + p1.fileName + "  |  " +
                    p2.archive + "/" + p2.fileName + "   " + String.format("%.5f", sds0.value) +
                    " rev " + String.format("%.5f", sds0.value));
            p1 = PictureDao.getPictureById(conn, sdsN.strings[0]);
            p2 = PictureDao.getPictureById(conn, sdsN.strings[1]);
            log.info(
                    "\nPRZ max " + p1.archive + "/" + p1.fileName + "  |  " +
                    p2.archive + "/" + p2.fileName + "   " + String.format("%.5f", sdsN.value) +
                    " rev " + String.format("%.5f", sdsN.value));
            double diff = sdsN.value-sds0.value;
            log.info(
                    "\nPRZ     " + String.format("%.0e", diff) + " / " + preds.size() +
                    " = " + String.format("%.0e", diff/preds.size()) + " per, " +
                    vector_name + " " + (sds == sds0 ? " min" : " max") );
            diff = sdsN.value - sds0.value;
            log.info(
                    "\nPRZ2     " + String.format("%.0e", diff) + " / " + preds.size() +
                    " = " + String.format("%.0e", diff/preds.size()) + " per, " +
                    vector_name + " " + (sds == sds0 ? " min" : " max") );
        }
        List<Screen> scl = new ArrayList<>();

        scl.add(new Screen(session.browserID,
                                   1, "v", sds.strings[0],
                                   pr1));
        scl.add(new Screen(session.browserID,
                                   2, "v", sds.strings[1],
                                   pr2));

        log.info("modelMatch t=" + (System.currentTimeMillis() - t0));

        return scl;

    }

    // assuming one user per install? TODO

    private static FeelingMirror instance = null;

    public static FeelingMirror getMirror() {
        synchronized(FeelingMirror.class) {
            if (instance == null) {
                instance = new FeelingMirror();
            }
        }
        return instance;
    }

    private Predict predict = null;

    private FeelingMirror() {

        super();

        predict = new SimplePredict();
        //predict = new MLPredict();
    }
}
