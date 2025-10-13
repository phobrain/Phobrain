package org.phobrain.servlet;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  FeelingMirror - main/live 'brain' of Phobrain.
 **     Serves photo pairs in response to 'drawing dots' 
 **     as a reading of feelings. 
 **     TODO - classify objects in pics, use names of objects
 **             that any dots highlight for a revival of the
 **             original keyword=based system.
 */

import org.phobrain.util.ConfigUtil;
import org.phobrain.util.MathUtil;
import org.phobrain.util.MiscUtil;
import org.phobrain.util.MiscUtil.SeenIds;
import org.phobrain.util.ListHolder;
import org.phobrain.util.HashCount;
import org.phobrain.util.AtomSpec;
import org.phobrain.util.SortDoubleStrings;

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

import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

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

    private MultiLayerNetwork flowModel_vgg16_4;
    private MultiLayerNetwork flowModel_nnl_7;

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

    public int getPctSeen(Connection conn, Session session,
                                           int viewNum, String orient)
            throws SQLException {
        SeenIds seenids = getSeen(conn, session);
        if (seenids.seen == null  ||  seenids.seen.size() == 0) {
            return 0;
        }
        Set<String> picSet = data.getPicSet(viewNum, orient);
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
        if (flowModel_vgg16_4 == null  &&  flowModel_nnl_7 == null) {
            return false;
        }
        return true;
    }

    private void setTmpVec(Picture p, String vector) {

        if ("nnl_7".equals(vector)) {
            p.tmp_vec = p.nnl_7;
/*
        } else if ("vgg16_2".equals(vector)) {
            p.tmp_vec = p.vgg16_2;
*/
        } else if ("vgg16_4".equals(vector)) {
            p.tmp_vec = p.vgg16_4;
        } else if ("dense_4".equals(vector)) {
            p.tmp_vec = p.dense_4;
        } else {
            log.warn("Unknown vector: " + vector + " using vgg16_4");
            p.tmp_vec = p.vgg16_4;
        }
    }

    final boolean doRev = false;

    private double reverseByteOrder(double value) {

        if (!doRev) return value;
        long longBits = Double.doubleToLongBits(value);
        long reversedBits = Long.reverseBytes(longBits);
        return Double.longBitsToDouble(reversedBits);
    }

    /*
     *  modelMatch() - MxN on lhs[2] using flowModel
     */
    @Override
    List<Screen> modelMatch(Connection conn,
                                    UserProfile up, int viewNum, String orient,
                                    Session session, List<String> ids, ListHolder lhs[])
            throws SQLException {

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

        String vector = ((dh.dots1 + dh.dots2) % 2 == 0 ? "nnl_7" : "vgg16_4");

        // gather the pic reps: previous pair for every case
        //   leave pics handy for now
        Picture p_1_1 = PictureDao.getPictureById(conn, ids.get(0));
        Picture p_1_2 = PictureDao.getPictureById(conn, ids.get(1));
        setTmpVec(p_1_1, vector);
        setTmpVec(p_1_2, vector);

        MultiLayerNetwork flowModel;
        if ("vgg16_4".equals(vector)) {
            flowModel = flowModel_vgg16_4;
        } else if ("nnl_7".equals(vector)) {
            flowModel = flowModel_nnl_7;
        } else {
            flowModel = flowModel_nnl_7;
        }

        // gather picreps for candidates on left and right

        Map<String, float[]> picreps = new HashMap<>();

        int errors = 0;

        for (String id : lhs[0].id2_l) {
            Picture p = PictureDao.getPictureById(conn, id);
            setTmpVec(p, vector);
            if (p.tmp_vec == null) {
                errors++;
                continue;
            }
            picreps.put(id, p.tmp_vec);
        }
        for (String id : lhs[1].id2_l) {

            if (picreps.get(id) != null) continue;

            Picture p = PictureDao.getPictureById(conn, id);
            setTmpVec(p, vector);
            if (p.tmp_vec == null) {
                errors++;
                continue;
            }
            picreps.put(id, p.tmp_vec);
        }

        // pack the constants into data:
        //      i.e. all data but the vectors of new pair
        //          1st pair, histograms, 'db' data are constant

        int DATA_SZ = hist1.length + hist2.length + hist3.length + hist4.length +
                      4 * p_1_1.tmp_vec.length;

        double[] data = new double[DATA_SZ];

        double data2d[][] = new double[1][];
        data2d[0] = data;

        int dix = 0;
        for (int i=0; i<p_1_1.tmp_vec.length; i++) {
            data[dix++] = reverseByteOrder( (double) p_1_1.tmp_vec[i] );
        }
        for (int i=0; i<p_1_2.tmp_vec.length; i++) {
            data[dix++] = reverseByteOrder( (double) p_1_2.tmp_vec[i] );
        }
        for (int i=0; i<hist1.length; i++) {
            data[dix++] = reverseByteOrder( (double) hist1[i] );
        }
        for (int i=0; i<hist2.length; i++) {
            data[dix++] = reverseByteOrder( (double) hist2[i] );
        }
        for (int i=0; i<hist3.length; i++) {
            data[dix++] = reverseByteOrder( (double) hist3[i] );
        }
        for (int i=0; i<hist4.length; i++) {
            data[dix++] = reverseByteOrder( (double) hist4[i] );
        }

        // run the model over the NxM cases

        try {

            List<SortDoubleStrings> preds = new ArrayList<>();

            for (int i=0; i<lhs[0].size(); i++) {

                String lpic = lhs[0].id2_l.get(i);
                float[] larr = picreps.get(lpic);
                int dix2 = dix;
                for (int c=0; c<larr.length; c++) {
                    data[dix2++] = reverseByteOrder( (double) larr[c] );
                }
                for (int j=0; j<lhs[1].size(); j++) {

                    String rpic = lhs[1].id2_l.get(j);
                    float[] rarr = picreps.get(rpic);
                    int dix3 = dix2;
                    for (int c=0; c<rarr.length; c++) {
                        data[dix3++] = reverseByteOrder( (double) rarr[c] );
                    }

                    INDArray aaa = Nd4j.create(data2d);
                    double prediction = flowModel.output(aaa).getDouble(0);

                    preds.add( new SortDoubleStrings(prediction, new String[] {lpic, rpic} ) );

                    //if (true) { log.info("WWWX flow pred " + prediction + " from " + Arrays.toString(data)); }

                }
            }
            Collections.sort(preds);

            SortDoubleStrings sds0 = preds.get(0);
            SortDoubleStrings sdsN = preds.get(preds.size()-1);

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
            log.info("PRED " + method + " spread " + (sdsN.value - sds0.value) + " on " + preds.size());
            log.info("PREDrev " + method + " spread " + (reverseByteOrder(sdsN.value) - reverseByteOrder(sds0.value)) + " on " + preds.size());

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
                    " rev " + String.format("%.5f", reverseByteOrder(sds0.value)));
                p1 = PictureDao.getPictureById(conn, sdsN.strings[0]);
                p2 = PictureDao.getPictureById(conn, sdsN.strings[1]);
                log.info(
                    "\nPRZ max " + p1.archive + "/" + p1.fileName + "  |  " +
                    p2.archive + "/" + p2.fileName + "   " + String.format("%.5f", sdsN.value) +
                    " rev " + String.format("%.5f", reverseByteOrder(sdsN.value)));
                double diff = sdsN.value-sds0.value;
                log.info(
                    "\nPRZ     " + String.format("%.0e", diff) + " / " + preds.size() +
                    " = " + String.format("%.0e", diff/preds.size()) + " per, " +
                    vector + " " + (sds == sds0 ? " min" : " max") );
                diff=reverseByteOrder(sdsN.value) - reverseByteOrder(sds0.value);
                log.info(
                    "\nPRZ2     " + String.format("%.0e", diff) + " / " + preds.size() +
                    " = " + String.format("%.0e", diff/preds.size()) + " per, " +
                    vector + " " + (sds == sds0 ? " min" : " max") );
            }
            List<Screen> scl = new ArrayList<>();

            scl.add(new Screen(session.browserID,
                                   1, "v", sds.strings[0],
                                   pr1));
            scl.add(new Screen(session.browserID,
                                   2, "v", sds.strings[1],
                                   pr2));
            return scl;

        } catch (Exception e) {
            log.error("PREDICT " + e, e);
        }
        return null;
    }

    private static FeelingMirror instance = null;

    public static FeelingMirror getFeelingMirror() {
        synchronized(FeelingMirror.class) {
            if (instance == null) {
                instance = new FeelingMirror();
            }
        }
        return instance;
    }

    private FeelingMirror() {

        super();

        Connection conn = null;
        try {
            // conn = DaoBase.getConn();

            long t1 = System.currentTimeMillis();

// w/ ois
//final String MODEL = "/home/epqe/new/models2/qiktry_val_63_roc_66_dim_7_softsign.keras";
// no db
//final String MODEL = "/home/epqe/new/models_no_db/qiktry_val_64_roc_70_dim_7_softsign.keras";
            final String MODEL_vgg16_4 = "/home/epqe/new/models_no_db/qiktry_val_66_roc_71_dim_4_tanh.keras";
            final String MODEL_nnl_7 = "/home/epqe/new/models_no_db/qiktry_val_66_roc_70_dim_7_tanh.keras";

            log.info("Loading models " + MODEL_vgg16_4 + " " + MODEL_nnl_7);
            flowModel_vgg16_4 = KerasModelImport.
                    importKerasSequentialModelAndWeights(MODEL_vgg16_4, false);
            log.info("Loaded MODEL " + MODEL_vgg16_4 + ":   " + flowModel_vgg16_4.summary());
            flowModel_nnl_7 = KerasModelImport.
                    importKerasSequentialModelAndWeights(MODEL_nnl_7, false);
            log.info("Loaded MODEL " + MODEL_nnl_7 + ":   " + flowModel_nnl_7.summary());

            if (false) {
                BufferedReader in = new BufferedReader(new FileReader("/home/epqe/new/pypreds"));
                String line;
                double[] data = new double[202];
                double data2d[][] = new double[1][];
                data2d[0] = data;
                while ((line = in.readLine()) != null) {

                    if (line.startsWith("#")) {
                        continue;
                    }

                    String ss[] = line.split("\\|");
                    if (ss.length != 2) {
                        log.error("LEN mismatch " + ss.length + " != 2");
                        System.exit(1);
                    }

                    String sss[] = ss[0].split(",");
                    if (sss.length != data.length) {
                        log.error("LEN mismatch " + sss.length + ", " + data.length);
                        System.exit(1);
                    }
                    for (int i=0; i<sss.length; i++) {
                        data[i] = Double.parseDouble(sss[i]);
                    }
                    INDArray aaa = Nd4j.create(data2d);
                    double prediction = flowModel_nnl_7.output(aaa).getDouble(0);
                    log.info("WWW " + prediction + " " + ss[1]);
                }
            }

            log.info("Load time: " + (System.currentTimeMillis()-t1) + " ms");
            /*
            initNNOpts(p.getProperty("n_pairtop_nn_v"),
                       p.getProperty("n_pairtop_nn_h"));
            log.info("Load time: " + (System.currentTimeMillis()-t1) + " ms");
            */
/*
        } catch (NamingException ne) {
            log.error("init: Naming", ne);
        } catch (SQLException sqe) {
            log.error("init: DB: " + sqe, sqe);
*/
        } catch (Exception e) {  // ? what types
            log.error("Model load: " + e);
        } finally {
            // DaoBase.closeSQL(conn);
        }
        log.info("FeelingMirror Init OK");
    }

/* easy reference/old
    public static class PictureResponse {
        Picture p;
        int value = -1;
        String method;
        boolean first = false;
        ListHolder lh;
        AtomSpec atoms = AtomSpec.NO_ATOM;
        double factor = -1.0;
    }
    private enum AtomSpec {
        NO_ATOM(0),
        C_O2N3N4(1),
        C_O2N3(2),
        C_N3N4(3),
        A_N1N6N7(4),
        A_N1N6(5),
        A_N6N7(6),
        T_O2N3(7),
        T_N3O4(8);
        private int value;
        private AtomSpec(int value) {
            this.value = value;
        }
    }
*/

}
