package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  TrainingPairsDao - aggregate/impure
 **/

import javax.naming.InvalidNameException;

import org.phobrain.db.record.Picture;
import org.phobrain.db.record.ApprovedPair;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrainingPairsDao extends DaoBase {

    private static final Logger log = LoggerFactory.getLogger(
                                                 TrainingPairsDao.class);

    private static int sortZeroCt = 0; // re-init in getSameArchSeqsPosNeg()

    private static ApprovedPair procPair(Connection conn, ApprovedPair ap,
                                         Set<String>  missing_id,
                                         Set<String>  orient_mismatch,
                                         Boolean sortPos)
            throws SQLException {

        if (missing_id != null  &&
            (missing_id.contains(ap.id1)  ||
             missing_id.contains(ap.id2))) {
            return null;
        }

        if (orient_mismatch != null  &&
            (orient_mismatch.contains(ap.id1)  ||
             orient_mismatch.contains(ap.id2))) {
            return null;
        }
        Picture p1 = PictureDao.getPictureById(conn, ap.id1);
        if (p1 == null) {
            //log.error("No pic: " + ap.id1);
            if (missing_id != null) {
                missing_id.add(ap.id1);
            }
            return null;
        }
        Picture p2 = PictureDao.getPictureById(conn, ap.id2);
        if (p2 == null) {
            if (missing_id != null) {
                missing_id.add(ap.id2);
            }
            //log.error("APP: Not v? " + ap.id2 + " " + ap.vertical);
            return null;
        }
        if (p1.vertical != p2.vertical  ||  p1.vertical != ap.vertical) {
            if (orient_mismatch != null) {
                orient_mismatch.add(p1.id + "|" + p2.id); // compound
            }
            return null;
        }

        if (sortPos != null) {
            String sortCol = "p_d0";

            ap.sortVal = PairDao.getVal(conn, ap.id1, ap.id2, 
                                          (ap.vertical ? "v" : "h"), 
                                          sortCol, true/*default 0*/);
            if (ap.sortVal == 0) {
                sortZeroCt++;
            }
        }

        ap.id1 = ap.id1 + "|" + ap.id2;  // compound
        ap.id2 = "" + p1.archive + "/" + p1.fileName + " " +
                      p2.archive + "/" + p2.fileName;

        return ap;
    }

    private static boolean indexArchSeq(Map<String, ApprovedPair> map, 
                                                    ApprovedPair ap) {

        String[] ids = ap.id1.split("\\|");

        if (ids.length != 2) {
            log.error("BAD ID0: id1|id2 expected: " + ap.id1 + " got len " + 
                                                            ids.length);
            return true;
        }

        String[] id1 = ids[0].split("/");
        String[] id2 = ids[1].split("/");

        if (id1.length != 2  ||  id2.length != 2) {
            log.error("BAD id in ID1: id1|id2 expected '/' in each: " + ap.id1);
            return true;
        }

        StringBuilder sb = new StringBuilder();

        for (int i=0; i<id1[1].length(); i++) {
            char c = id1[1].charAt(i);
            if (!Character.isDigit(c)) {
                break;
            }
            sb.append(c);
        }
        id1[1] = sb.toString();
        sb.setLength(0);
        for (int i=0; i<id2[1].length(); i++) {
            char c = id2[1].charAt(i);
            if (!Character.isDigit(c)) {
                break;
            }
            sb.append(c);
        }
        id2[1] = sb.toString();

        map.put(id1[0] + "/" + id1[1] + "|" +
                id2[0] + "/" + id2[1],
                ap);

        return false;
    }

    // 4 db query results
    //Set<String> random_showings = null;
    private static List<ApprovedPair> seen = null;
    private static List<ApprovedPair> lap_1 = null;
    private static List<ApprovedPair> lap_2 = null;

    private static void do_it(Connection conn, 
            Boolean huh, Object target_1, String orient, 
            Object use_pos, Object use_neg, Object excludes)
            throws SQLException {
            log.info("STuBB");
    }

    private static void fixIds(ApprovedPair ap) {
        log.info("Split " + ap.id1);
        String[] ids = ap.id1.split("\\|");
        ap.id1 = ids[0];
        ap.id2 = ids[1];
    }

    public static List<ApprovedPair> getSameArchSeqsPosNeg(String orient)
            throws SQLException {

        long t0 = System.currentTimeMillis();

        sortZeroCt = 0;

        // 1,2 == approved, not

        List<ApprovedPair> manual_1 = new ArrayList<>();
        List<ApprovedPair> bulk_1 = new ArrayList<>();

        List<ApprovedPair> initial_rejects = new ArrayList<>();

        List<ApprovedPair> manual_2 = new ArrayList<>();
        List<ApprovedPair> bulk_2 = new ArrayList<>();
        //List<ApprovedPair> manual_2_b4 = new ArrayList<>();
        //List<ApprovedPair> manual_2_after = new ArrayList<>();

        int train_pos_targ;
        int train_neg_targ;
        int test_pos_targ;
        int test_neg_targ;
        int MOD_POS;

        final int CONN_CT = 3;

        Connection[] conns = new Connection[CONN_CT];

        try {
            log.info("=== start queries");

            for (int i=0; i<CONN_CT; i++) {
                conns[i] = getConn();
            }

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
            */
            seen = null;
            lap_1 = null;
            lap_2 = null;

            /*
            final String vh2 = vh;
            Thread t1 = new Thread() {
                public void run() {
                    try {
                        random_showings = // arch1:seq1_arch2:seq2
                           ShowingPairDao.getPairsWithMethod(conns[0], false,
                                                             "rand/" + vh2);
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
                        lap_1 = ApprovalDao.getAllApprovedPairs(conns[0],
                                                1, orient, null, 
                                                false, null, true, null);
                    } catch (Exception e) {
                        log.error("AP/1's: " + e);
                        lap_1 = null;
                    }
                }
            };
            t2.start();

            Thread t3 = new Thread() {
                public void run() {
                    try {
                        lap_2 = ApprovalDao.getAllApprovedPairs(conns[1], 
                                                2, orient, null, 
                                                false, null, true, null);
                    } catch (Exception e) {
                        log.error("AP/2's: " + e);
                        lap_2 = null;
                    }
                }
            };
            t3.start();

            Thread t4 = new Thread() {
                public void run() {
                    try {
                        seen = UniquePairDao.getAllSeen(conns[2]);
                        if (seen == null) {
                            throw new SQLException("AllSeen: null");
                        }
                    } catch (Exception e) {
                        log.error("AllSeen: " + e);
                        seen = null;
                    }
                }
            };
            t4.start();

            //t1.join();
            t2.join();
            t3.join();
            t4.join();

            log.info("=== queries done in ms " + 
                                    (System.currentTimeMillis()-t0));

            if (seen == null  ||  lap_1 == null  ||  lap_2 == null) {
                throw new SQLException("query or more returned null");
            }

            // positive cases
            int tot_pos = lap_1.size();

            Set<String> approve_set = new HashSet<>();
            Set<String> reject_set = new HashSet<>();

            // split into manual/bulk

            Set<String> missing_id = new HashSet<>(); 
            Set<String> orient_mismatch = new HashSet<>(); // compound

            int approved_random_sel = 0;

            DateFormat df = new SimpleDateFormat("dd/MM/yyyy");
            //Date dt = df.parse("19/11/2017");
            //Timestamp ref_ts = new Timestamp(dt.getTime());

            for (ApprovedPair ap : lap_1) {
                ap = procPair(conns[0], ap, 
                        missing_id, orient_mismatch, null); // null=no sort
                if (ap == null) {
                    continue;
                }

                /*
                if (random_showings.contains(ap.id1)) {
                    approved_random_sel++;
                }
                */
                approve_set.add(ap.id1); // combined key
                if (Character.isLowerCase(ap.curator.charAt(0))) {
                    manual_1.add(ap);
                } else {
                    bulk_1.add(ap);
                }
            }

            // get neg / status=2

            int disapproved_random = 0;

            // split into manualA/manualB/bulk

            Date dt = df.parse("1/11/2017");
            Timestamp inceptionDate = new Timestamp(dt.getTime());

            for (ApprovedPair ap : lap_2) {
                ap = procPair(conns[0], ap, 
                        missing_id, orient_mismatch, null); //HACK -? false); // false==d0d
                if (ap == null) {
                    continue;
                }
                /*
                if (random_showings.contains(ap.id1)) {
                    disapproved_random++;
                }
                */
                reject_set.add(ap.id1); // joint key

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
            }

            log.info("Orient (?) mismatches: " + orient_mismatch.size());
            
            /*
            if ("bv".equals(orient)) {
                orient = "v";
            } else if ("bh".equals(orient)) {
                orient = "h";
            }
            */

            int initial_rej_random = 0;

            //DateFormat df = new SimpleDateFormat("dd/MM/yyyy");

            if (seen == null) {
                throw new SQLException("'seen' null at loop");
            }
            for (ApprovedPair ap : seen) {
                if (ap.id1.equals(ap.id2)) {
                    log.info("Skipping 'seen' A-A: " + ap.id1);
                    continue;
                }
                String prKey = ap.id1 + "|" + ap.id2;
                if (approve_set.contains(prKey)) {
                    continue;
                }
                if (reject_set.contains(prKey)) {
                    continue;
                }
                ap.vertical = "v".equals(orient);
                ap = procPair(conns[0], ap, missing_id, null, null); //HACK -? false); // false == use d0d
                if (ap == null) {
                    continue;
                }
                initial_rejects.add(ap);
                reject_set.add(ap.id1);
                /*
                if (random_showings.contains(ap.id1)) {
                    initial_rej_random++;
                }
                */
            }

            int tot_neg = initial_rejects.size() +
                          manual_2.size() + 
                          bulk_2.size();

            Map<String, ApprovedPair> archseq_neg = new HashMap<>();
            boolean bad = false;
            for (ApprovedPair ap : initial_rejects) {
                if (indexArchSeq(archseq_neg, ap)) {
                    bad = true;
                }
            }
            Map<String, ApprovedPair> archseq_pos = new HashMap<>();
            for (ApprovedPair ap : manual_1) {
                if (indexArchSeq(archseq_pos, ap)) {
                    bad = true;
                }
            }
            for (ApprovedPair ap : bulk_1) {
                if (indexArchSeq(archseq_pos, ap)) {
                    bad = true;
                }
            }
            if (bad) {
                throw new SQLException("Fuck it.");
            }

            Set<String> negPairIds = archseq_neg.keySet();
            Set<String> posPairIds = archseq_pos.keySet();

            if (posPairIds.retainAll(negPairIds)) {
                log.info("pair arch:seq|arch:seq split pos/neg: " +
                        " size_overlap=" + posPairIds.size() + "\n" +
                        posPairIds);

                List<ApprovedPair> ret = new ArrayList<>();
                for (String key : posPairIds) {
                    // id1[0] + "/" + id1[1] + "|" +
                    // id2[0] + "/" + id2[1]);
                    ApprovedPair pos = archseq_pos.get(key);
                    ApprovedPair neg = archseq_neg.get(key);

                    if (pos == null  ||  neg == null) {
                        throw new SQLException("Missing key from pos/neg: " + 
                                                        key);
                    }
                    fixIds(pos);
                    fixIds(neg);

                    ret.add(pos);
                    ret.add(neg);
                }

                return ret;
            }
            log.error("Not impl");
            if (true) throw new SQLException("Not impl");
            int mid_initial_rej = -1;
            ApprovedPair mid = null;
            int manual_1_split = 0;
            int manual_2_split = 0;
            int bulk_1_split = 0;
            int bulk_2_split = 0;

            boolean split = false;

            if (split) {
                log.warn("Split code is old..");

                // initial_rej from uniquepairs have the original
                //    classification date for manual (vs. bulk kwd) pairs
                mid_initial_rej = initial_rejects.size() / 2;

                mid = initial_rejects.get(mid_initial_rej);
                log.info("Initial mid: " + mid_initial_rej + " t=" + 
                                            mid.createTime);
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
                        log.info("Going to next by " + (next+1) + ": " + 
                              mid_initial_rej);
                    } else {
                        log.info("Going to next by " + (next+1) + ": " + 
                              mid_initial_rej);
                        mid_initial_rej -= (prev + 1);
                        log.info("Going to prev by " + (prev+1) + ": " + 
                              mid_initial_rej);
                    }
                    if (mid_initial_rej < 0  ||  mid_initial_rej > 
                                                 initial_rejects.size()-1) {
                        log.error("Ran off with the goat.");
                        throw new SQLException("Some kinda problem.");
                    }
                    
                    mid = initial_rejects.get(mid_initial_rej);
                }
                log.info("= Split initial_reject to 2x" + mid_initial_rej + 
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

                log.info("Resulting split: status=2:");
                log.info("manual: " + manual_2_split + "|" + 
                                     (manual_2.size()-manual_2_split));
                log.info("bulk: " + bulk_2_split + "|" + 
                                 (bulk_2.size()-bulk_2_split));
                log.info("total: " + (manual_2_split + bulk_2_split) + "|" + 
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

                log.info("Resulting split: status=1:");
                log.info("manual: " + manual_1_split + "|" + 
                                    (manual_1.size()-manual_1_split));
                log.info("bulk: " + bulk_1_split + "|" + 
                                 (bulk_1.size()-bulk_1_split));
                log.info("total: " + (manual_1_split + bulk_1_split) + "|" + 
                                 (manual_1.size() - manual_1_split + 
                                  bulk_1.size() - bulk_1_split));
            }

            // see if any multi-edit archive:sequence's are split
            // between neg and pos.
            

            log.info("---- summary");

            log.info("missing ids: " + missing_id.size());

            log.info("sortZeroCt: " + sortZeroCt);

            log.info("Positive: " + (manual_1.size() + bulk_1.size()) +
                 "\n\tmanual " + manual_1.size() + " bulk " + bulk_1.size() +
                 "\n\trandom/shown " + approved_random_sel);
            int tot_rej_review = manual_2.size() + 
                                 bulk_2.size();
            log.info("Negative: " + tot_neg);
            log.info("\tinitial_rejects: " + initial_rejects.size());
            log.info("\trej in review: " + tot_rej_review +
                    "\n\t\tmanual " + manual_2.size() + 
                    "\n\t\tbulk " + bulk_2.size());
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

            log.info("Totals: ok_pos: " + ok_pos + 
                       " tot_neg: " + tot_neg);

            // targets

            int use_pos = ok_pos;
            int use_neg = tot_neg;

            if (split) {
                use_pos /= 2;
                use_neg /= 2;
                log.info("per split use_pos: " + use_pos + " use_neg: " + use_neg);
            } else {
                log.info("use_pos: " + use_pos + " use_neg: " + use_neg);
            }

            double pos_neg = (double) use_pos / (double) use_neg;

            log.info("==== PlanF: train_pos = train_neg = test_neg, " +
                       " test_pos = .333");
            //   train_neg = X
            //   train_pos = X
            //   test pos = X/3
            //   test neg = X
            //  => 1.3333X pos, 2X neg
            //  => pos_neg: 1.333 / 2 = .6667
            final double P_N = 0.667;

            final double P_N_INV = 1.0 / P_N;

            if (pos_neg > P_N) {

                // extra pos: scale back
                int t = use_pos;

                //PlanA use_pos = (int) (0.6 * use_neg);
                //PlanB use_pos = (int) (1.1 * use_neg);
                //PlanC use_pos = (int) (1.6 * use_neg);
                //PlanD use_pos = (int) (2.1 * use_neg);
                use_pos = (int) (P_N * use_neg);
                t -= use_pos;

                log.info("*** !! threw out " + t + " pos. Add " +
                                    ((int)(P_N_INV * t)) + " neg to balance");
            } else if (pos_neg < (P_N - 0.001)) {

                // extra neg: scale back

log.info("TODO - add pos predictions - scaling back neg for now");
                int t = use_neg;
                
                //PlanA use_neg = (int)(1.6667 * use_pos);
                //PlanB use_neg = (int)(0.90909 * use_pos);
                //PlanC use_neg = (int)(0.625 * use_pos);
                //PlanD use_neg = (int)(0.4762 * use_pos);
                use_neg = (int)(P_N_INV * use_pos);
                t -= use_neg;
                log.info("IIII *** !! threw out " + t + " negs. Add " +
                                    ((int)(P_N * t)) + " pos to balance");
            }

            // PlanF - must agree w/ above note/code
            //   train_neg = X
            //   train_pos = X
            //   test pos = X/3
            //   test neg = X
            //  => 1.3333X pos, 2X neg
            //  => pos_neg: 1.333 / 2 = .6667
            int X = (int) (use_pos / 1.333); 
            train_pos_targ = X;
            train_neg_targ = X;
            test_pos_targ = (int) (X / 3.0);
            test_neg_targ = X;
            MOD_POS = 4; // every 4th pos is a test case => 3:1

            log.info("train_pos/neg: " + train_pos_targ + "/" + train_neg_targ);
            log.info("test_pos/neg: " +  test_pos_targ + "/" + test_neg_targ);

            boolean SHUFFLE = false;
            if (SHUFFLE) {

                log.info("Shuffle..");

                Collections.shuffle(manual_1);
                Collections.shuffle(bulk_1);
                Collections.shuffle(bulk_2);

                Collections.shuffle(initial_rejects);
                Collections.shuffle(manual_2);
                Collections.shuffle(bulk_2);
            }

            Set<String> excludes = new HashSet<>(); // safety net

            if (split) {
                // String target = args[0];
                String target_1 = null;
                String target_2 = null;

                do_it(null,
                        true,  target_1, orient, use_pos, use_neg, excludes);
                do_it(null,
                        false, target_2, orient, use_pos, use_neg, excludes);
            } else {
                do_it(null,
                        null, orient /*target*/, orient, use_pos, use_neg, excludes);
            }
        } catch (Exception e) {
            throw new SQLException("Gotcha: " + e);
        } finally {
            try {
                for (Connection conn : conns) {
                    closeSQL(conn);
                }
            } catch (Exception f) {
                log.error("Exception f!: " + f);
            }
        }
        return null;
    }
}

