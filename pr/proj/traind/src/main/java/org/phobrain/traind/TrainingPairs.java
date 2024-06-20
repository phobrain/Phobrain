package org.phobrain.traind;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  TrainingPairs/cmdline - generate training data from Phobrain's database.
 **
 */

import java.io.File;
import java.io.PrintStream;

import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Random;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverManager;

import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;

import org.phobrain.util.Stdio;
import org.phobrain.util.ConfigUtil;
import org.phobrain.util.MiscUtil;

import org.phobrain.db.util.DBUtil;
import org.phobrain.db.record.Picture;
import org.phobrain.db.record.Keywords;
import org.phobrain.db.record.Pair;
import org.phobrain.db.record.ApprovedPair;
import org.phobrain.db.dao.ApprovalDao;
import org.phobrain.db.dao.UniquePairDao;
import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.PictureDao;
import org.phobrain.db.dao.PairDao;
import org.phobrain.db.dao.KeywordsDao;

public class TrainingPairs extends Stdio {

    // Main Settings

    // Splits:
    //
    //   SplitPlanF was for unbalanced data when native pos was ~20%:
    //          pos:neg X:X on train, (X/3):X on test, native pos was ~20%.
    //          Wound up w/ unblanced pos, getting 'em w/ net help 
    //          is addictive too. :-)
    //   SplitPlanG is 'normal' given excess pos: 
    //          X:X pos:neg in train & test.
    //
    //   SplitPlanH would find non-overlapping train/test pic sets, likely trying
    //          combos of archives after gathering stats on intra/inter-archive 
    //          training pairs. Rigorous validation hasn't been done since early PlanF.
    //
    //  TODO: filter tower repeats and the like down to limited, more-varied sets.

    private static String SplitPlan = "SplitPlanG"; 

    private static boolean addZeros = false;

    // all 'useTop' options need d0 i.e. pr.pairs_[vh], which may be phased out

    private static boolean useTopPos = false;
    private static boolean useTopNeg = false;  // only matters if excess negs, n/a
    private static boolean useTopNegUsed = false;  
    private static boolean padNegsWithQuery = false; // fill in negs with d0-worst pairs
                                                    // pos acc < 80%, neg ~85%, 
                                                    // so dropped for now
    // /settings
    
    private static final Random rand = new Random();

    private static void usage(String msg) {
        System.err.println("Usage: [prog] <v|h|b> [split]");
        if (msg != null) {
            System.err.println("\t=> " + msg);
        }
        System.err.println("\t- v, h result in v,vb or h,hb dirs");
        System.err.println("\t       for train on v, h, or v+h");
        System.err.println("\t       while testing only on v or h");
        System.err.println("\tsplit [needs work] for _1, _2 versions");
        System.exit(1);
    }


    private static String balanced = "*** balanced ***";

    private static int sortZeroCt = 0;

    static Set<String> missing_id = new HashSet<>();
    static Map<String, Picture> picMap = new HashMap<>();

    static Picture getPic(Connection conn, String id) 
            throws SQLException {

        Picture p = picMap.get(id);
        if (p != null) {
            return p;
        }
        p = PictureDao.getPictureById(conn, id);
        if (p == null) {
            missing_id.add(id);
        } else {
            picMap.put(id, p);
        }
        return p;
    }

    private static ApprovedPair procPair(Connection conn, ApprovedPair ap,
                                         boolean setVertical, // for pr.pair_local
                                         Set<String>  orient_mismatch,
                                         Boolean sortPos)
            throws SQLException {

        if (missing_id.contains(ap.id1)  ||
            missing_id.contains(ap.id2)) {
            return null;
        }

        if (orient_mismatch != null  &&
            (orient_mismatch.contains(ap.id1)  ||
            orient_mismatch.contains(ap.id2))) {
            return null;
        }

        Picture p1 = getPic(conn, ap.id1);
        if (p1 == null) {
            //err("No pic: " + ap.id1);
            return null;
        }
        Picture p2 = getPic(conn, ap.id2);
        if (p2 == null) {
            return null;
        }
        if (p1.vertical != p2.vertical) {
            if (orient_mismatch != null) {
                orient_mismatch.add(p1.id + "|" + p2.id); // compound
            }
            return null;
        }
        if (setVertical) { // 'seen' pair_local
            ap.vertical = p2.vertical;
            ap.status = 2; // equiv
        } else if (ap.vertical != p2.vertical) {
            if (orient_mismatch != null) {
                orient_mismatch.add(p1.id + "|" + p2.id); // compound
            }
            return null;
        }

        if (sortPos != null) {
            String sortCol = "d0p";
            ap.sortVal = PairDao.getVal(conn, ap.id1, ap.id2, 
                                          (ap.vertical ? "v" : "h"), 
                                          sortCol, true/*default 0*/);
            if (ap.sortVal == 0) {
                sortZeroCt++;
            }
        }

        // abusing a scratch var
        ap.otherId = "" + p1.archive + "/" + p1.fileName + " " +
                      p2.archive + "/" + p2.fileName;

        return ap;
    }

    private static List<ApprovedPair> manual_1 = new ArrayList<>();
    private static List<ApprovedPair> bulk_1 = new ArrayList<>();

    private static List<ApprovedPair> initial_rejects = new ArrayList<>();

    private static List<ApprovedPair> manual_2 = new ArrayList<>();
    private static List<ApprovedPair> bulk_2 = new ArrayList<>();
    //List<ApprovedPair> manual_2_b4 = new ArrayList<>();
    //List<ApprovedPair> manual_2_after = new ArrayList<>();

    private static int train_pos_targ;
    private static int train_neg_targ;
    private static int test_pos_targ;
    private static int test_neg_targ;
    private static int MOD_POS;

    // actually achieved:

    private static int train_pos_ct = 0;
    private static int test_pos_ct = 0;
    private static int train_neg_ct = 0;
    private static int test_neg_ct = 0;

    // 4 db query results
    private static Set<String> random_showings = null;
    private static List<ApprovedPair> seen = null; // both h+v
    private static List<ApprovedPair> lap_1 = null;
    private static List<ApprovedPair> opp_1 = null;
    private static List<ApprovedPair> lap_2 = null;
    private static List<ApprovedPair> opp_2 = null;

    private static void chopIdP(ApprovedPair ap) {

        // switch to stripped arch/seq id's
    
        String tid1 = ap.id1.replace(":", "/"); // just in case
        String tid2 = ap.id2.replace(":", "/");

        String[] id1 = tid1.split("/"); // delim for python aagh
        String[] id2 = tid2.split("/");

        if (id1.length != 2  ||  id2.length != 2) {
            err("Bad id split. [" + tid1 + "] [" + tid2 + "]");
        }
        String s = id1[1];
        for (int i=0; i<s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                id1[1] = s.substring(0, i);
                break;
            }
        }
        s = id2[1];
        for (int i=0; i<s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                id2[1] = s.substring(0, i);
                break;
            }
        }

        tid1 = id1[0] + "/" + id1[1];
        tid2 = id2[0] + "/" + id2[1];

        ap.idP = tid1 + "|" + tid2;
    }

    private static void useBaseIdsIdP(List<ApprovedPair> list) {
        if (list == null) {
            err("list is null\n" + MiscUtil.getStack(7));
            return;
        }
        for (ApprovedPair ap : list) {
            chopIdP(ap);
        }
    }

    private static String otherIdP(String[] idP, String id) {

        if (idP.length != 2) {
            new Exception("here").printStackTrace();
            err("idP w t f or why? " + id + " " + idP.length);
        }

        String otherid = null;

        if (id.equals(idP[0])) {
            otherid = idP[1];
        } else if (id.equals(idP[1])) {
            otherid = idP[0];
        }

        return otherid;
    }

    private static int skippedPairs = 0;

    private static void handleId(String id, 
                                Set<String> trainIdSet, Set<String> testIdSet, boolean train,
                                Set<String> archseq_pos, Set<String> archseq_neg,
                                Set<String[]> trainPosPairs, Set<String[]> trainNegPairs,
                                Set<String[]> testPosPairs, Set<String[]> testNegPairs) {

        Set<String> thisIdSet = (train ? trainIdSet : testIdSet);
        Set<String> otherIdSet = (train ? testIdSet : trainIdSet);

        for (String pr : archseq_pos) {

            if (!pr.contains(id)) {
                continue;
            }
            String[] idP = pr.split("\\|");
if (idP.length != 2) err("len " + idP.length + " on " + pr);
            String otherid = otherIdP(idP, id);
            if (otherid == null) {
                continue;
            }

            if (otherIdSet.contains(otherid)) {
                skippedPairs++;
                continue;
            }
            if (!thisIdSet.contains(otherid)) {
                err("other not in this set: " + otherid);
                thisIdSet.add(otherid);
            }
            if (train) {
                trainPosPairs.add(idP);
            } else {
                testPosPairs.add(idP);
            }
         }

         for (String pr : archseq_neg) {

            if (!pr.contains(id)) {
                continue;
            }
            String[] idP = pr.split("\\|");
if (idP.length != 2) err("len " + idP.length + " on " + pr);
            String otherid = otherIdP(idP, id);
            if (otherid == null) {
                 continue;
            }

            if (otherIdSet.contains(otherid)) {
                skippedPairs++;
                continue;
            }
            if (!thisIdSet.contains(otherid)) {
                err("not this: " + otherid);
            }
            if (train) {
                trainNegPairs.add(idP);
            } else {
                testNegPairs.add(idP);
            }
        }
    }

    public static void main(String[] args) {
  
        if (args.length < 1) {
            usage(null);
        }
        if (args.length > 2) {
            usage(null);
        }

        boolean old_split = false;
        boolean split = false;
        if (args.length == 2) {
            if (!"split".equals(args[1])) {
                usage("Expected 'split'");
            }
            split = true;
            SplitPlan = "SplitPlanH";
        }
        String orient = args[0];
        if (!"v".equals(orient)  &&  
            !"h".equals(orient)  &&  
            !"b".equals(orient)) {
            usage(null);
        }

        String target_1 = orient;
        String target_2 = orient + "b";

        pout("-- target output dirs: " + target_1 + " " + target_2);

        File target_dir1 = null;
        File target_dir2 = null;

        if (!old_split) {
            target_dir1 = new File(target_1);
            target_dir2 = new File(target_2);
            String msg = "";
            if (target_dir1.exists()) {
                msg += target_dir1 + " ";
                //err("exists: " + target_dir1);
            }
            if (target_dir2.exists()) {
                msg += target_dir2;
                //err("exists: " + target_dir2);
            }
            if (msg.length() > 0) {
                pout("==== Exists: " + msg);
                pout("==== Removing in 5 sec");
                try { Thread.sleep(5000); } catch (Exception ignore){}
                target_dir1.delete();
                target_dir2.delete();
            }
            try {
                target_dir1.mkdir();
                target_dir2.mkdir();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        } else {
            err("split option not maintained since v,vb h,hb made automatic");

            target_1 = target_1 + "_1";
            target_dir1 = new File(target_1);
            if (target_dir1.exists()) {
                err("exists: " + target_dir1);
            }
            target_2 = target_2 + "_2";
            target_dir2 = new File(target_2);
            if (target_dir2.exists()) {
                err("exists: " + target_dir2);
            }
            try {
                target_dir1.mkdir();
                target_dir2.mkdir();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        // train v+h but test on v or h
        boolean train_only_both = false;

        if ("b".equals(orient)) {
            // unused/untested
            orient = null;
        } else {
            // normal
            train_only_both = true;
        }

        long t0 = System.currentTimeMillis();

        try {
            pout("=== DB start queries");

            Class.forName("org.postgresql.Driver");
            final Connection conn1 = DriverManager.getConnection(
                                                      "jdbc:postgresql:pr", 
                                                      "pr", "@@pr");
            final Connection conn2 = DriverManager.getConnection(
                                                      "jdbc:postgresql:pr", 
                                                      "pr", "@@pr");
            final Connection conn3 = DriverManager.getConnection(
                                                      "jdbc:postgresql:pr", 
                                                      "pr", "@@pr");
            final Connection conn4 = DriverManager.getConnection(
                                                      "jdbc:postgresql:pr", 
                                                      "pr", "@@pr");
            conn1.setAutoCommit(false);
            conn1.setAutoCommit(false);
            conn2.setAutoCommit(false);
            conn3.setAutoCommit(false);
            conn4.setAutoCommit(false);

            // null == train and test on both v,h
            final String q_orient = 
                  ("v".equals(orient)  ||  "h".equals(orient) ? orient : null);
            final String opp_orient = 
                    ( !train_only_both ? null : ("h".equals(q_orient) ? "v" : "h") ) ;

/*
            // TODO -H-specific
            String vh = q_orient;
            if (vh == null) {
                if (orient.contains("v")) {
                    vh = "v";
                } else if (orient.contains("h")) {
                    vh = "h";
                } else {
                    err("need v/h or more thought");
                }
            }
            
            final String vh2 = vh;
            Thread t1 = new Thread() {
                public void run() {
                    try {
                        random_showings = // arch1:seq1_arch2:seq2
                           ShowingPairDao.getPairsWithMethod(conn1, false,
                                                             "rand/" + vh2);
                        useBaseIdsIdP(random_showings);
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
            };
            t1.start();
*/

            Thread t2 = new Thread() {
                public void run() {
                    try {
                        // positive is status==1
                        lap_1 = ApprovalDao.getAllApprovedPairs(conn2,
                                                new Integer(1), q_orient, null, 
                                                false, null, true, null);
                        if (addZeros) {
                            // unconfirmed positive is status==0
                            List<ApprovedPair> lap_0 =
                                ApprovalDao.getAllApprovedPairs(conn2,
                                                new Integer(0), q_orient, 
                                                null, // curatorUpper,
                                                true, // d0
                                                null, //first
                                                true, // forward
                                                null // set
                                            );
                            int size = lap_0.size() / 3;
                            if (useTopPos) {
                                pout("== addZeros: adding top as pos: " + size);
                                for (int i=0; i<size; i++) {
                                    lap_1.add(lap_0.get(i));
                                }
                            } else {
                                pout("== addZeros: adding bottom as pos: " + size);
                                for (int i=2*size; i<lap_0.size(); i++) {
                                    lap_1.add(lap_0.get(i));
                                }
                            }
                        }
                        // for keeping pairs of derivatives (typically crops) 
                        //      separate in train/test
                        useBaseIdsIdP(lap_1);

                        if (opp_orient != null) {
                            // same for other orientation, 
                            // but no status==0-promotion
                            // positive is status==1
                            opp_1 = ApprovalDao.getAllApprovedPairs(conn2,
                                                new Integer(1), opp_orient, 
                                                null, false, 
                                                null, true, null);
                            useBaseIdsIdP(opp_1);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
            };
            t2.start();

            Thread t3 = new Thread() {
                public void run() {
                    try {
                        // negative is status==2
                        lap_2 = ApprovalDao.getAllApprovedPairs(conn3, 
                                                new Integer(2), q_orient, null, 
                                                false, null, true, null);
                        if (lap_2 == null) {
                            err("== approved=2 list is null, q_orient=" + q_orient);
                        }

                        useBaseIdsIdP(lap_2);
                        if (opp_orient != null) {
                            opp_2 = ApprovalDao.getAllApprovedPairs(conn3, 
                                                new Integer(2), opp_orient, 
                                                null, false, 
                                                null, true, null);
                            if (opp_2 == null) {
                                err("== opp_2 list is null, opp_orient=" + 
                                                            opp_orient);
                            }
                            useBaseIdsIdP(opp_2);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
            };
            t3.start();

            Thread t4 = new Thread() {
                public void run() {
                    try {
                        // unique pairs == pr.pair_local table
                        // no v,h distinction TODO: fix retroactively
                        seen = UniquePairDao.getAllSeen(conn4);
                        if (seen == null) {
                            err("seen' is null at call");
                        }
                        useBaseIdsIdP(seen);
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
            };
            t4.start();

            //t1.join();
            t2.join();
            t3.join();
            t4.join();

            pout("=== DB done queries " + (System.currentTimeMillis()-t0) + " ms");

            if (seen == null) {
                err("'seen' is null");
            }

            // positive cases
            int tot_pos = lap_1.size();

            Set<String> approve_set = new HashSet<>();
            Set<String> reject_set = new HashSet<>();

            // split into manual/bulk  TODO: stop it if do_pos_v1() works

            Set<String> missing_id = new HashSet<>(); 
            Set<String> orient_mismatch = new HashSet<>(); // compound

            int approved_random_sel = 0;

            DateFormat df = new SimpleDateFormat("dd/MM/yyyy");
            //Date dt = df.parse("19/11/2017");
            //Timestamp ref_ts = new Timestamp(dt.getTime());

            for (ApprovedPair ap : lap_1) {

                ap = procPair(conn1, ap, false,
                        orient_mismatch, null); // null=no sort
                if (ap == null) {
                    continue;
                }

                approve_set.add(ap.idP);

                if (Character.isLowerCase(ap.curator.charAt(0))) {
                    manual_1.add(ap);
                } else {
                    bulk_1.add(ap);
                }

                /*
                // Pairs selected from random ones are 'pure'
                //  and likely worth tracking and maybe weighting.
                if (random_showings.contains(ap.idP)) {
                    approved_random_sel++;
                }
                */
            }
            if (opp_1 != null) {

                List<ApprovedPair> tmp_l = new ArrayList<>();

                for (ApprovedPair ap : opp_1) {
                    ap = procPair(conn1, ap, false,
                            orient_mismatch, null); // null=no sort
                    if (ap == null) {
                        continue;
                    }
                    tmp_l.add(ap);
                    approve_set.add(ap.idP);
                }
                opp_1 = tmp_l;
                for (ApprovedPair ap : opp_1) {
                    if (ap.otherId == null) {
                        err("NULL opp_1 " + ap.idP + " status " + ap.status);
                    }
                }
            }

            // get explicit neg / status=2
            // split into manualA/manualB/bulk

            int disapproved_random = 0;

            Date dt = df.parse("1/11/2017");
            Timestamp inceptionDate = new Timestamp(dt.getTime());

            for (ApprovedPair ap : lap_2) {

                ap = procPair(conn1, ap, false, orient_mismatch, null); 
                                //HACK -? false); // false==d0d
                if (ap == null) {
                    continue;
                }
                reject_set.add(ap.idP);

                if (Character.isLowerCase(ap.curator.charAt(0))) {

                    manual_2.add(ap);
/*
                    if (ap.createTime.before(inceptionDate)) {
                        manual_2_b4.add(ap);
                    } else {
                        manual_2_after.add(ap);
                    }
*/
                } else {
                    bulk_2.add(ap);
                }

                /*
                if (random_showings.contains(ap.idP)) {
                    disapproved_random++;
                }
                */
            }

            if (opp_2 != null) {

                List<ApprovedPair> tmp_l = new ArrayList<>();

                for (ApprovedPair ap : opp_2) {

                    ap = procPair(conn1, ap, false, orient_mismatch, null); 
                                //HACK -? false); // false==d0d
                    if (ap == null) {
                        continue;
                    }
                    tmp_l.add(ap);
                    reject_set.add(ap.idP);
                }
                opp_2 = tmp_l;
                for (ApprovedPair ap : opp_2) {
                    if (ap.otherId == null) {
                        err("NULL opp_2 " + ap.idP + " status " + ap.status);
                    }
                }
            }

            if (orient_mismatch.size() > 0) {
                Set<String> ids = new HashSet<>();
                for (String s : orient_mismatch) {
                    String[] ss = s.split("\\|");
                    ids.add(ss[0]);
                    ids.add(ss[1]);
                }
                pout("\nOrient (?) mismatches: " + 
                            orient_mismatch.size() + "\n" + 
                            String.join(", ", orient_mismatch) +
                            "\nIds\n" +
                            String.join(", ", ids));
            }
            
            int initial_rej_random = 0;

            //DateFormat df = new SimpleDateFormat("dd/MM/yyyy");

            if (seen == null) {
                err("'seen' null at loop");
            }

            int tmp_sz = opp_2.size();

            for (ApprovedPair ap : seen) {

                if (ap.id1.equals(ap.id2)) {
                    pout("Skipping 'seen' A-A: " + ap.id1);
                    continue;
                }
                if (approve_set.contains(ap.idP)) {
                    continue;
                }
                if (reject_set.contains(ap.idP)) {
                    continue;
                }

                ap = procPair(conn1, ap, true, // set v/h
                            null, null); //HACK -? false); // false == use d0d
                if (ap == null) {
                    continue;
                }
                if (orient == null  ||  ap.vertical == orient.equals("v")) {
                    initial_rejects.add(ap);
                    reject_set.add(ap.idP);
                } else if (opp_2 != null) {
                    opp_2.add(ap);
                    reject_set.add(ap.idP);
                }
            }

            pout("-- opp_2 from seen: " + (opp_2.size()-tmp_sz) + " from 2: " + tmp_sz);

            int tot_neg = initial_rejects.size() +
                          manual_2.size() + 
                          bulk_2.size();

            // informational count of pos/neg overlap 
            //      involving crops, using the abbreviated 
            //      id's (stripped -2, -2 etc.)

            Set<String> archseq_neg = new HashSet<>();
            for (ApprovedPair ap : initial_rejects) {
                archseq_neg.add(ap.idP);
            }
            for (ApprovedPair ap : manual_2) {
                archseq_neg.add(ap.idP);
            }
            for (ApprovedPair ap : bulk_2) {
                archseq_neg.add(ap.idP);
            }

            Set<String> archseq_pos = new HashSet<>();
            for (ApprovedPair ap : manual_1) {
                // manual 0's optionally folded-in above
                archseq_pos.add(ap.idP);
            }
            for (ApprovedPair ap : bulk_1) {
                // bulk-approved in review
                archseq_pos.add(ap.idP);
            }

            // overlap?
            Set<String> testSet = new HashSet<>(archseq_pos);
            testSet.retainAll(archseq_neg);
            if (testSet.size() > 0) {
                Set<String> ids = new HashSet<>();
                for (String s : testSet) {
                    archseq_pos.remove(s);
                    archseq_neg.remove(s);
                    String[] ss = s.split("\\|");
                    ids.add(ss[0]);
                    ids.add(ss[1]);
                }
                pout("Stats: Pairs(arch:seq|arch:seq) both pos&neg: " +
                        " size_overlap=" + testSet.size() + 
                        " Ids: " +  ids.size());
            }

            List<String> allPairs = new ArrayList<>(archseq_pos);
            allPairs.addAll(archseq_neg);

            Set<String> idSet = new HashSet<>();

            for (String s : allPairs) {
                String[] ss = s.split("\\|");
                idSet.add(ss[0]);
                idSet.add(ss[1]);
            }
            List<String> ids = new ArrayList<>(idSet);
            pout("Total ids: " + ids.size());

            int mid_initial_rej = -1;
            ApprovedPair mid = null;
            int manual_1_split = 0;
            int manual_2_split = 0;
            int bulk_1_split = 0;
            int bulk_2_split = 0;

            if (old_split) {

                pout("this Split code is old..");

                // initial_rej from uniquepairs have the original
                //    classification date for manual (vs. bulk kwd) pairs
                mid_initial_rej = initial_rejects.size() / 2;

                mid = initial_rejects.get(mid_initial_rej);
                pout("Initial mid: " + mid_initial_rej + " t=" + mid.createTime);
                int prev = 0;
                int next = 0;
                for (int i=1; i<mid_initial_rej-1; i++) {
                    boolean quit = false;
                    ApprovedPair x = initial_rejects.get(mid_initial_rej + i);
                    if (x.createTime.equals(mid.createTime)) {
                        next = i;
                    } else {
                        quit = true;
                    }
                    x = initial_rejects.get(mid_initial_rej - i);
                    if (x.createTime.equals(mid.createTime)) {
                        prev = i;
                    } else if (quit) {
                        break;
                    }
                }
                if (prev + next > 0) {
                    if (prev <= next) {
                        mid_initial_rej += next + 1;
                        pout("Going to next by " + (next+1) + ": " + 
                              mid_initial_rej);
                    } else {
                        pout("Going to next by " + (next+1) + ": " + 
                              mid_initial_rej);
                        mid_initial_rej -= (prev + 1);
                        pout("Going to prev by " + (prev+1) + ": " + 
                              mid_initial_rej);
                    }
                    if (mid_initial_rej < 0  ||  mid_initial_rej > 
                                                 initial_rejects.size()-1) {
                        err("Ran off with the goat.");
                    }
                    
                    mid = initial_rejects.get(mid_initial_rej);
                }
                pout("= Split initial_reject to 2x" + mid_initial_rej + 
                         " using time: " + mid.createTime);

                for (int i=0; i<manual_2.size(); i++) {
                    ApprovedPair ap = manual_2.get(i);
                    if (!ap.createTime.before(mid.createTime)) {
                        manual_2_split = i;
                        break;
                    }
                }
                for (int i=0; i<bulk_2.size(); i++) {
                    ApprovedPair ap = bulk_2.get(i);
                    if (!ap.createTime.before(mid.createTime)) {
                        bulk_2_split = i;
                        break;
                    }
                }

                pout("Resulting split: status=2:");
                pout("\tmanual: " + manual_2_split + "|" + 
                                     (manual_2.size()-manual_2_split));
                pout("\tbulk: " + bulk_2_split + "|" + 
                                 (bulk_2.size()-bulk_2_split));
                pout("\ttotal: " + (manual_2_split + bulk_2_split) + "|" + 
                                   (manual_2.size() - manual_2_split + 
                                    bulk_2.size() - bulk_2_split));

                for (int i=0; i<manual_1.size(); i++) {
                    ApprovedPair ap = manual_1.get(i);
                    if (!ap.createTime.before(mid.createTime)) {
                        manual_1_split = i;
                        break;
                    }
                }
                for (int i=0; i<bulk_1.size(); i++) {

                    ApprovedPair ap = bulk_1.get(i);
                    if (!ap.createTime.before(mid.createTime)) {
                        bulk_1_split = i;
                        break;
                    }
                }

                pout("Resulting split: status=1:");
                pout("\tmanual: " + manual_1_split + "|" + 
                                    (manual_1.size()-manual_1_split));
                pout("\tbulk: " + bulk_1_split + "|" + 
                                 (bulk_1.size()-bulk_1_split));
                pout("\ttotal: " + (manual_1_split + bulk_1_split) + "|" + 
                                 (manual_1.size() - manual_1_split + 
                                  bulk_1.size() - bulk_1_split));
            } // end old 'split'

            pout("---- summary");

            if (missing_id.size() > 0) {
                pout("missing ids: " + missing_id.size() + "\n" +
                        String.join(", ", missing_id));
            }

            pout("sortZeroCt: " + sortZeroCt);

            pout("Positive: " + (manual_1.size() + bulk_1.size()) +
                 "\n\tmanual " + manual_1.size() + " bulk " + bulk_1.size());
                 //"\n\trandom/shown " + approved_random_sel);

            int tot_rej_review = manual_2.size() + 
                                 bulk_2.size();

            pout("Negative: " + tot_neg);
            pout("\tinitial_rejects: " + initial_rejects.size());
            pout("\trej in review: " + tot_rej_review +
                    "\n\t\tmanual " + manual_2.size() + 
                    "\n\t\t  bulk " + bulk_2.size());
            /*
            pout("\trandom/shown " + (disapproved_random + initial_rej_random) +
                     " init " + initial_rej_random + 
                     "  disapp " + disapproved_random);
            */
/*
            pout("Cut time: " + ref_ts + "\n" +
                    "\tintial rejects: " +
                      pre_cut_initial_rejects + "|" + 
                        (initial_rejects.size() - pre_cut_initial_rejects));
            pout("\tpost cut rejects: " + post_cut_rejects + "|" +
                      (tot_rej_review - post_cut_rejects));
            pout("\ttotal neg: " + (pre_cut_initial_rejects + 
                                       post_cut_rejects) + "|" +
                                      (initial_rejects.size() + tot_rej_review -
                                       pre_cut_initial_rejects +
                                       post_cut_rejects));
            pout("\ttotal pos: " + pre_cut_1s + "|" + post_cut_1s);
*/

            int ok_pos = manual_1.size() + bulk_1.size();

            pout("Totals: ok_pos: " + ok_pos + 
                       " tot_neg: " + tot_neg);

            // targets

            int use_pos = ok_pos;
            int use_neg = tot_neg;

            if (old_split) {
                use_pos /= 2;
                use_neg /= 2;
                pout("per split use_pos: " + use_pos + " use_neg: " + use_neg);
            } else {
                pout("use_pos: " + use_pos + " use_neg: " + use_neg);
            }

            double pos_neg = (double) use_pos / (double) use_neg;

            double P_N = -99.99;

            int X = -1;

            if ("SplitPlanF".equals(SplitPlan)) {

                // SplitPlanF is good for starting out 
                //      if positives are ~<20% of random sample

                pout("==== SplitPlanF: train_pos = train_neg = test_neg, " +
                                " test_pos = .33333");
                //   train_neg = X
                //   train_pos = X
                //   test pos = X/3
                //   test neg = X
                //  => 1.333333X pos, 2X neg
                //  => pos_neg: 1.33333 / 2 = .6667
                X = (int) (use_pos / 1.333); 
                P_N = 2.0 / 3.0; // 0.66666667;

            } else if ("SplitPlanG".equals(SplitPlan)) {

                // classic proportions


                if (use_pos == use_neg) {
                    X = use_pos / 2;
                } else if (use_pos < use_neg) {
                    X = use_pos / 2;
                } else {
                    X = use_neg / 2;
                }

                pout("==== SplitPlanG: train_pos = train_neg " +
                            "= test_pos = test_neg = " + X);

                P_N = 1.0;

            } else if ("SplitPlanH".equals(SplitPlan)) {

                //err("TODO - find archive- or random-based train/test split without pics in common"); // try egrep -v on existing

                Set<String> trainIdSet = new HashSet<>();
                Set<String> testIdSet = new HashSet<>();

                Set<String[]> trainPosPairs = new HashSet<>();
                Set<String[]> trainNegPairs = new HashSet<>();
                Set<String[]> testPosPairs = new HashSet<>();
                Set<String[]> testNegPairs = new HashSet<>();

                Collections.shuffle(ids);

                //  pair lineup: archseq_pos archseq_neg

                for (String id : ids) {

                    if (trainIdSet.contains(id)  ||
                        testIdSet.contains(id)) {

                        continue;
                    }

                    int trainPairs = trainPosPairs.size() + trainNegPairs.size();
                    int testPairs = testPosPairs.size() + testNegPairs.size();

                    if (trainPairs > testPairs) {
                        testIdSet.add(id);
                    } else {
                        trainIdSet.add(id);
                    }
                }

                for (String id : testIdSet) {
                    handleId(id, trainIdSet, testIdSet, false,
                                 archseq_pos, archseq_neg,
                                 trainPosPairs, trainNegPairs,
                                 testPosPairs, testNegPairs);
                }
                for (String id : trainIdSet) {
                    handleId(id, trainIdSet, testIdSet, true,
                                 archseq_pos, archseq_neg,
                                 trainPosPairs, trainNegPairs,
                                 testPosPairs, testNegPairs);
                }
                pout("Split-by-id-random sizes: \n" +
                    "\tIds train/test: " + trainIdSet.size() + "/" +
                                            testIdSet.size() + "\n" +
                    "\tPairs pos/neg: " + archseq_pos.size() + "/" +
                                         archseq_neg.size());
                pout("\tTrain pos/neg: " + trainPosPairs.size() + "/" +
                                           trainNegPairs.size());
                pout("\tTest pos/neg: " + testPosPairs.size() + "/" +
                                          testNegPairs.size());
                pout("Skipped pairs: " + skippedPairs);
err("ok");

            } else {

                err("No SplitPlan[FG] set");

            }

            if (P_N < 0.0) {
                err("<0: " + P_N);
            }

            double P_N_INV = 1.0 / P_N;

            if (pos_neg > P_N) {

                // extra pos

                if (padNegsWithQuery) {

                    pout("== Extra pos: padding negs by worst d0's");

                    int t = (int) (P_N * use_neg);
                    t = use_pos - t;
                    t = (int)(P_N_INV * t);                   
                    pout("-- padding negs with " + t + " d0-worst");
                    List<Pair> extra_neg = PairDao.getPairsByD0(conn1, orient, false/*worst*/, 
                                                            t, approve_set, reject_set);
                    for (Pair p : extra_neg) {

                        ApprovedPair ap = new ApprovedPair(p.id1, p.id2);
                        chopIdP(ap);
                        ap.sortVal = p.sortVal;
                        ap.d0 = p.sortVal;
                        ap.curator = "+neg";
                        ap.status = 2;
                        ap.vertical = "v".equals(orient);
                        ap = procPair(conn1, ap, false, null, null);
                        initial_rejects.add(ap);  // TODO - own list?
                    }
                    balanced = "*** !! dragooned bottom " + t + 
                                " negs from pr.pairs_X into initial_rejects";
//Qq
//err("test so quit");

                } else if ("SplitPlanG".equals(SplitPlan)) {

                    int t = use_pos;
                    use_pos = use_neg;
                    balanced = "*** !! threw out " + (t-use_pos) +
                                " pos. Add same number of neg to balance";

                } else {

                    // extra pos and not padding with low-d0's: scale back

                    //SplitPlanA use_pos = (int) (0.6 * use_neg);
                    //SplitPlanB use_pos = (int) (1.1 * use_neg);
                    //SplitPlanC use_pos = (int) (1.6 * use_neg);
                    //SplitPlanD use_pos = (int) (2.1 * use_neg);

                    int t = use_pos;
                    use_pos = (int) (P_N * use_neg);
                    t -= use_pos;

                    balanced = "*** !! threw out " + t + " pos. Add " +
                            ((int)(P_N_INV * t)) + " neg to balance";
                }

            } else if (pos_neg < (P_N - 0.001)) {

                // extra neg: scale back

pout("TODO - add pos predictions - scaling back neg for now");
                int t = use_neg;
                
                //SplitPlanA use_neg = (int)(1.6667 * use_pos);
                //SplitPlanB use_neg = (int)(0.90909 * use_pos);
                //SplitPlanC use_neg = (int)(0.625 * use_pos);
                //SplitPlanD use_neg = (int)(0.4762 * use_pos);
                use_neg = (int)(P_N_INV * use_pos);
                t -= use_neg;

                balanced = "*** !! threw out " + t + " negs. Add " +
                            ((int)(P_N * t)) + " pos to balance";
            }

            pout(balanced);


            if ("SplitPlanF".equals(SplitPlan)) {

                // SplitPlanF - must agree w/ above note/code
                //   train_neg = X
                //   train_pos = X
                //   test pos = X/3
                //   test neg = X
                //  => 1.3333X pos, 2X neg
                //  => pos_neg: 1.333 / 2 = .6667
                train_pos_targ = X;
                train_neg_targ = X;
                test_pos_targ = (int) (X / 3.0);
                test_neg_targ = X;
                MOD_POS = 4; // every 4th pos is a test case => 3:1

            } else if ("SplitPlanG".equals(SplitPlan)) {

                // SplitPlanG - must agree w/ above note/code
                //   train_neg = X
                //   train_pos = X
                //   test pos = X
                //   test neg = X
                train_pos_targ = X;
                train_neg_targ = X;
                test_pos_targ = X;
                test_neg_targ = X;
                MOD_POS = 2; // every other pos is a test case

            } else {

                err("?? No SplitPlan[FG] in 2nd case");

            }

            pout("train_pos/neg: " + train_pos_targ + "/" + train_neg_targ);
            pout("test_pos/neg: " +  test_pos_targ + "/" + test_neg_targ);
            pout("mod_pos: " + MOD_POS);

            int opp_size = 0; 
            if (train_only_both) {
                
                // need pos==neg, limit to size of target orientation

                pout("-- train-only w/ both v,h: pos/neg(status 1, 2+seen): " + 
                                opp_1.size() + ", " + opp_2.size());

                opp_size = X; // limit to size of target orientation's set

                if (opp_1.size() < opp_size) {
                    pout("Trimming opp_size from X to opp_1: " + opp_1.size());
                    opp_size = opp_1.size();
                }

                if (opp_2.size() < opp_size) {
                    pout("Trimming opp_size from X to opp_2: " + opp_2.size());
                    opp_size = opp_2.size();
                }

                // trim by opp_size, so randomize

                Collections.shuffle(opp_1);
                Collections.shuffle(opp_2);

                pout("-- '[vh]b' train: will be adding " + 
                                                opp_size + " pos/neg");
            }
                
            boolean SHUFFLE = false;
            if (SHUFFLE) {

                pout("Shuffle..");

                // put pos together 1st? not used for a while

                Collections.shuffle(manual_1);
                Collections.shuffle(bulk_1);
                Collections.shuffle(bulk_2);

                Collections.shuffle(initial_rejects);
                Collections.shuffle(manual_2);
                Collections.shuffle(bulk_2);
            }

            Set<String> excludes = new HashSet<>(); // safety net

            if (old_split) {

                pout("== WARNING - OLD 'split' CODE");

                do_it(conn1,
                        true,  target_1, orient, use_pos, use_neg, 
                        opp_size, excludes);
                do_it(conn1,
                        false, target_2, orient, use_pos, use_neg, 
                        opp_size, excludes);
            } else {
                do_it(conn1,
                        null, target_1, target_2, orient, use_pos, use_neg, 
                        opp_size, excludes);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
       
    }

    // _v1: use current nets' best=d0 ratings on pos cases
    // or worst: see useTopPos

    private static void do_pos_v1(Connection conn,
                                Boolean first, 
                                String target_1, String target_2, 
                                String orient,
                                int use_pos, int use_neg, int opp_size,
                                Set<String> excludes) 
            throws Exception {

        // first==null == no split -> normal for a while, 2022/7
        // target2 also uses opposite orientation cases in training

        PrintStream train_pos_1 = new PrintStream(target_1 + "/train.pos");
        PrintStream train_pos_2 = new PrintStream(target_2 + "/train.pos");
        PrintStream test_pos_1 = new PrintStream(target_1 + "/test.pos");
        PrintStream test_pos_2 = new PrintStream(target_2 + "/test.pos");

        pout("-- sorting pos_1 by d0, total: " +
                    (manual_1.size() + bulk_1.size()));

        List<ApprovedPair> pos_cases = new ArrayList<>();
        pos_cases.addAll(manual_1);
        pos_cases.addAll(bulk_1);
        for (ApprovedPair ap: pos_cases) { 
            ap.sortVal = ap.d0;
        }
        if (useTopPos) {
            Collections.sort(pos_cases); // low->hi
        } else {
            Collections.sort(pos_cases, Collections.reverseOrder()); // hi->low
        }

        int n_pos = 0;
        int get_test = 0;

        for (ApprovedPair ap : pos_cases) {

            if (excludes.contains(ap.idP)) { // compound
                continue;
            }
            excludes.add(ap.idP); // compound
            n_pos++;
            if (n_pos % MOD_POS == 0) {
                get_test++;
            }
            if (get_test > 0  &&
                    (orient == null  ||  
                     (orient.endsWith("v")  &&  ap.vertical)  ||
                     (orient.endsWith("h")  &&  !ap.vertical))) {
                // test set
                // using pic file name to map to histos later on
                test_pos_1.println(ap.otherId); // adapted
                test_pos_2.println(ap.otherId); // adapted
                test_pos_ct++;
                get_test--;
            } else {
                // training set
                train_pos_1.println(ap.otherId); // adapted
                train_pos_2.println(ap.otherId); // adapted
                train_pos_ct++;
            }
            if (n_pos >= use_pos) {
                pout("-- got target " + use_pos);
                break;
            }
        }

        // add the opposite orientation to training set 2

        for (int i=0; i<opp_size; i++) {
            // here depending on the training code to shuffle
            ApprovedPair ap = opp_1.get(i);
            train_pos_2.println(ap.otherId); // adapted
        }

        pout("-- actual pos train/train_both/test: " + 
                        train_pos_ct + "/" + 
                        (train_pos_ct + opp_size) + "/" +
                        test_pos_ct);

        train_pos_1.close();
        train_pos_2.close();
        test_pos_1.close();
        test_pos_2.close();

        /*
        int x = 5;
        for (ApprovedPair ap: pos_cases) { 
            pout("d0 " + ap.d0);
            if (--x < 0) err("quit");
        }
        */

    }

    private static void do_pos_v0(Connection conn,
                                Boolean first, String target, String orient,
                                int use_pos, int use_neg,
                                Set<String> excludes) 
            throws Exception {

        PrintStream train_pos = new PrintStream(target + "/train.pos");
        PrintStream test_pos = new PrintStream(target + "/test.pos");

        int n_pos = 0;
        int get_test = 0;
        int mt_tower_skip = 0;

        // pos manual

        int man_pos = manual_1.size();
        if (first != null) {
            man_pos /= 2;
        }

        if (man_pos > use_pos) {

            pout("Trimming back man_pos from " + man_pos + 
                 " to use_pos " + use_pos);
            if (first == null) {
                pout("REALLY bad dude - not even using all pos manual");
            }
            man_pos = use_pos;

        } else if (man_pos == manual_1.size()) {
            pout("Using all positive manual: " + manual_1.size());
        }

        pout("Pos target: manual " + man_pos);

        List<ApprovedPair> train_pos_list = new ArrayList<>();

        if (man_pos >= manual_1.size()) {

            pout("Adding all manual positive cases");

            for (ApprovedPair ap : manual_1) {
                if (excludes.contains(ap.idP)) { // compound
                    continue;
                }
                excludes.add(ap.idP); // compound
                n_pos++;
                if (n_pos % MOD_POS == 0) {
                    get_test++;
                }
                if (get_test > 0  &&
                    (orient == null  ||  
                     (orient.endsWith("v")  &&  ap.vertical)  ||
                     (orient.endsWith("h")  &&  !ap.vertical))) {
                    // test set
                    // using pic file name to map to histos later on
                    test_pos.println(ap.otherId); // adapted
                    test_pos_ct++;
                    get_test--;
                } else {
                    // training set
                    train_pos_list.add(ap);
                    //train_pos.println(ap.otherId); // hacked
                    train_pos_ct++;
                }
            }

        } else if (first != null) {

            int mid = manual_1.size() / 2;

            pout("Splitting manual_1 to " + mid + " at " +
                  manual_1.get(mid).createTime);

            int start, end;
            if (first) {
                pout("Adding 1st half");
                start = 0;
                end = mid;
            } else {
                pout("Adding 2nd half");
                start = mid;
                end = manual_1.size();
            }
            for (int i=start; i<end; i++) {
                ApprovedPair ap = manual_1.get(i);

                if (excludes.contains(ap.idP)) { // compound
                    continue;
                }
                excludes.add(ap.idP); // compound
                n_pos++;
                if (n_pos % MOD_POS == 0) {
                    get_test++;
                }
                if (get_test > 0  &&
                    (orient == null  ||  
                     (orient.endsWith("v")  &&  ap.vertical)  ||
                     (orient.endsWith("h")  &&  !ap.vertical))) {
                    // test set
                    // using pic file name to map to histos later on
                    test_pos.println(ap.otherId); // adapted
                    test_pos_ct++;
                    get_test--;
                } else {
                    // training set
                    train_pos_list.add(ap);
                    //train_pos.println(ap.otherId); // hacked
                    train_pos_ct++;
                }
            }

        } else {

            pout("Sampling " + man_pos + " manual positive cases from " +
                               manual_1.size());

            Collections.shuffle(manual_1);
            int got = 0;
            for (ApprovedPair ap : manual_1) {
                if (excludes.contains(ap.idP)) { // compound
                    continue;
                }
                String[] ids = ap.idP.split("\\|"); // compound
                if (KeywordsDao.idsHaveKwd(conn, "mt_tower", ids)) {
                    mt_tower_skip++;
                    continue;
                }
                excludes.add(ap.idP); // compound
                n_pos++;
                got++;
                if (got > man_pos) {
                    break;
                }
                if (n_pos % MOD_POS == 0) {
                    get_test++;
                }
                if (get_test > 0  &&
                    (orient == null  ||
                     (orient.endsWith("v")  &&  ap.vertical)  ||
                     (orient.endsWith("h")  &&  !ap.vertical))) {
                    // test set
                    // using pic file name to map to histos later on
                    test_pos.println(ap.otherId); // adapted
                    test_pos_ct++;
                    get_test--;
                } else {
                    // training set
                    train_pos_list.add(ap);
                    //train_pos.println(ap.otherId); // hacked
                    train_pos_ct++;
                }
            }
            pout("mt_tower_skip = " + mt_tower_skip);
        }
        if (n_pos != man_pos) {
            pout("Man pos mismatch: wanted " + man_pos +
                                     " got " + n_pos);
        }

        pout("Positive/manual: total: " + n_pos +
                           " train " + train_pos_ct +
                           " test " + test_pos_ct);

        int bulk_pos = use_pos - n_pos;

        pout("Remaining pos to get: " + bulk_pos);

        // carry over get_test

        if (bulk_pos >= bulk_1.size()) {

            pout("Adding all bulk positive cases");

            for (ApprovedPair ap : bulk_1) {
                if (excludes.contains(ap.idP)) { // compound
                    continue;
                }
                excludes.add(ap.idP); // compound
                n_pos++;
                if (n_pos % MOD_POS == 0) {
                    get_test++;
                }
                if (get_test > 0  &&
                    (orient == null  ||  
                     (orient.endsWith("v")  &&  ap.vertical)  ||
                     (orient.endsWith("h")  &&  !ap.vertical))) {
                    // test set
                    // using pic file name to map to histos later on
                    test_pos.println(ap.otherId); // adapted
                    test_pos_ct++;
                    get_test--;
                } else {
                    // training set
                    train_pos_list.add(ap);
                    //train_pos.println(ap.otherId); // hacked
                    train_pos_ct++;
                }
            }

        } else if (first != null) {

            int mid = bulk_1.size() / 2;

            pout("Splitting bulk_1 to " + mid + " at " +
                  bulk_1.get(mid).createTime);

            int start, end;
            if (first) {
                start = 0;
                end = mid;
            } else {
                start = mid;
                end = bulk_1.size();
            }
            for (int i=start; i<end; i++) {
                ApprovedPair ap = bulk_1.get(i);

                if (excludes.contains(ap.idP)) { // compound
                    continue;
                }
                excludes.add(ap.idP); // compound

                n_pos++;
                if (n_pos % MOD_POS == 0) {
                    get_test++;
                }
                if (get_test > 0  &&
                    (orient == null  ||  
                     (orient.endsWith("v")  &&  ap.vertical)  ||
                     (orient.endsWith("h")  &&  !ap.vertical))) {
                    // test set
                    // using pic file name to map to histos later on
                    test_pos.println(ap.otherId); // adapted
                    test_pos_ct++;
                    get_test--;
                } else {
                    // training set
                    train_pos_list.add(ap);
                    //train_pos.println(ap.otherId); // hacked
                    train_pos_ct++;
                }
            }

        } else {
            pout("Sampling " + bulk_pos + " bulk positive cases");
            Collections.shuffle(bulk_1);
            int got = 0;
            for (ApprovedPair ap: bulk_1) {
                if (excludes.contains(ap.idP)) { // compound
                    continue;
                }
                String[] ids = ap.idP.split("\\|"); // compound
                if (KeywordsDao.idsHaveKwd(conn, "mt_tower", ids)) {
                    mt_tower_skip++;
                    continue;
                }
                excludes.add(ap.idP); // compound
                n_pos++;
                got++;
                if (got > bulk_pos) {
                    break;
                }
                if (n_pos % MOD_POS == 0) {
                    get_test++;
                }
                if (get_test > 0  &&
                     (orient == null  ||
                     (orient.endsWith("v")  ==  ap.vertical))) {
                    // test set
                    // using pic file name to map to histos later on
                    test_pos.println(ap.otherId); // adapted
                    test_pos_ct++;
                    get_test--;
                } else {
                    // training set
                    train_pos_list.add(ap);
                    //train_pos.println(ap.otherId); // adapted
                    train_pos_ct++;
                }
            }
            pout("mt_tower_skip = " + mt_tower_skip);
        }

        //pout("POS Hi->Lo");
        //Collections.sort(train_pos_list); 
        //Collections.reverse(train_pos_list); 
        // ---
        //pout("POS Lo->Hi (COUNTER)");
        //Collections.sort(train_pos_list); 
        // ---
        pout("POS Random order train");
        Collections.shuffle(train_pos_list);
        for (int i=0; i<5; i++) { 
            ApprovedPair ap = train_pos_list.get(i);
            pout("pos " + ap.sortVal + " " + ap.idP + " " + ap.otherId);
        }
        ApprovedPair ape = train_pos_list.get(train_pos_list.size()-2);
        pout("..\npos " + ape.sortVal + " " + ape.idP + " " + ape.otherId);
        ape = train_pos_list.get(train_pos_list.size()-1);
        pout("pos " + ape.sortVal + " " + ape.idP + " " + ape.otherId);

        for (ApprovedPair ap : train_pos_list) {
            train_pos.println(ap.otherId); // adapted
        }

        pout("Positive done, Yeah!\ntotal: " + n_pos + "(" +
                               (train_pos_ct + test_pos_ct) + ")" +
                               " train: " + train_pos_ct +
                               " test: " + test_pos_ct +
                               "\nmt_tower skip: " + mt_tower_skip);

        pout("Positive targets: total: " + use_pos +
                               " train: " + train_pos_targ +
                               " test: " + test_pos_targ);
        train_pos.close();
        test_pos.close();
    }

    /**
     **  do_it wrapper for disused 'old_split' option (2 passes)
     */
    private static void do_it(Connection conn,
                                Boolean first, String target, String orient,
                                int use_pos, int use_neg, int opp_size,
                                Set<String> excludes) 
            throws Exception {

            do_it(conn, first, target, null, orient, use_pos, use_neg, 
                        opp_size, excludes);
    }

    private static void do_it(Connection conn,
                                Boolean first, 
                                String target_1, String target_2,
                                String orient, 
                                int use_pos, int use_neg, int opp_size,
                                Set<String> excludes) 
            throws Exception {

        // first==null == no split

        do_pos_v1(conn, first, target_1, target_2, orient,
                                use_pos, use_neg, opp_size, excludes);

        // negative cases - same data goes to the test targets

        PrintStream train_neg_1 = new PrintStream(target_1 + "/train.neg");
        PrintStream train_neg_2 = new PrintStream(target_2 + "/train.neg");
        PrintStream test_neg_1 = new PrintStream(target_1 + "/test.neg");
        PrintStream test_neg_2 = new PrintStream(target_2 + "/test.neg");

        int get_test = 0;

        List<ApprovedPair> negs = new ArrayList<>();

        if (first == null) {

            pout("Adding all initial_rejects/manual/bulk 2's " +
                                             " and sorting reverse for train");
            negs.addAll(initial_rejects);
            negs.addAll(manual_2);
            negs.addAll(bulk_2);

        } else {

            pout("Splitting initial_rejects at " + 
                 initial_rejects.get(initial_rejects.size()/2).createTime);
            pout("Splitting manual_2 at " + 
                 manual_2.get(manual_2.size()/2).createTime);
            pout("Splitting bulk_2 at " + 
                 bulk_2.get(bulk_2.size()/2).createTime);

            if (first) {

                negs.addAll(initial_rejects.subList(0, 
                                            initial_rejects.size()/2));
                negs.addAll(manual_2.subList(0, manual_2.size()/2));
                negs.addAll(bulk_2.subList(0, bulk_2.size()/2));

            } else { // 2nd

                negs.addAll(initial_rejects.subList(initial_rejects.size()/2,
                                                initial_rejects.size()));
                negs.addAll(manual_2.subList(manual_2.size()/2,
                                         manual_2.size()));
                negs.addAll(bulk_2.subList(bulk_2.size()/2,
                                       bulk_2.size()));
            }
        }

        if (useTopNeg) {
            useTopNegUsed = true;
            pout("NEG Lo->Hi");
            Collections.sort(negs); // lo->hi
        } else {
            pout("NEG Hi->Lo");
            Collections.sort(negs, Collections.reverseOrder()); 
        }

        // ---
        //pout("NEG Hi->Lo (COUNTER)");
        //Collections.sort(initial_rejects); // lo->hi
        //Collections.reverse(initial_rejects); 
        // ---
        //pout("NEG Random order");
        //Collections.shuffle(initial_rejects);

        pout("Neg order:");
        for (int i=0; i<5; i++) { 
            ApprovedPair ap = negs.get(i);
            pout("neg " + ap.sortVal + " " + ap.idP + " " + ap.otherId);
        }
        ApprovedPair ape = negs.get(negs.size()-2);
        pout("..\nneg " + ape.sortVal + " " + ape.idP + " " + ape.otherId);
        ape = negs.get(negs.size()-1);
        pout("neg " + ape.sortVal + " " + ape.idP + " " + ape.otherId);

        pout("Adding " + use_neg + " neg cases out of " + negs.size());

        int excluded = 0;
        int n_neg = 0;

        for (ApprovedPair ap : negs) {

            if (excludes.contains(ap.idP)) { // compound
                excluded++;
                continue;
            }
            excludes.add(ap.idP); // compound

            n_neg++;
            if (n_neg > use_neg) {
                break;
            }

            if (n_neg % 2 == 0) {
                get_test++;
            }
            if (get_test > 0  &&
                    (orient == null  ||  
                     "v".equals(orient) == ap.vertical)) {
                // test set
                // using pic file name to map to histos later on
                test_neg_1.println(ap.otherId); // adapted
                test_neg_2.println(ap.otherId); // adapted
                test_neg_ct++;
                get_test--;
            } else {
                // training set
                //train_neg_list.add(ap);
                train_neg_1.println(ap.otherId); // adapted
                train_neg_2.println(ap.otherId); // adapted
                train_neg_ct++;
            }
        }
        pout("Added orient/neg: " + n_neg + 
                        " to base (excluded " + excluded + ")");

        // add opposite-oriented cases to train_neg_2

        for (int i=0; i<opp_size; i++) {
            // here depending on the training code to shuffle
            ApprovedPair ap = opp_2.get(i);
            train_neg_2.println(ap.otherId); // adapted
        }

        test_neg_1.close();
        test_neg_2.close();
        train_neg_1.close();
        train_neg_2.close();

/*
            for (ApprovedPair ap : train_neg_list) {
                train_neg.println(ap.otherId); // hacked
            }
*/

        pout("Final pos train " + train_pos_ct + 
                        " both " + (train_pos_ct + opp_size) +
                        " test " + test_pos_ct);
        pout("Pos targets: train " + train_pos_targ + 
                        " test " + test_pos_targ);
        pout("Final neg train " + train_neg_ct + 
                        " both " + (train_neg_ct + opp_size) +
                        " test " + test_neg_ct);
        pout("Neg targets: train " + train_neg_targ + 
                        " test " + test_neg_targ);

        String conditions = "\t# conditions: SplitPlan: " + SplitPlan + ";";
        if (addZeros) {
            conditions += " addZeros,";
        }
        if (useTopPos) {
            conditions += " useTopPos,";
        }
        if (useTopNeg  &&  useTopNegUsed) {
            conditions += " useTopNeg true: Lo->Hi,";
        }
        if (padNegsWithQuery) {
            conditions += " padNegsWithQuery";
        }
        if (conditions.endsWith(",")) {
            conditions = conditions.substring(0, conditions.length()-1);
        }

        pout("");
        pout(conditions);
        pout("\t" + balanced);
        pout("\tTrain: pos: " + train_pos_ct + " neg: " + train_neg_ct);
        pout("\t      both: " + (train_pos_ct + opp_size) + "/" +
                                (train_neg_ct + opp_size));

        pout("\tTest:  pos: " + test_pos_ct + 
                   " neg: " + test_neg_ct);

        pout("Done " + (first != null ? first ? "1st" : "2nd" : ""));

    }
}
