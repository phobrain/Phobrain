package org.phobrain.servlet;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  GetEngine - main/live 'brain' of Phobrain.
 **     Serves photo pairs or single photos.
 **
 */

import org.phobrain.util.ConfigUtil;
import org.phobrain.util.MathUtil;
import org.phobrain.util.MiscUtil;
import org.phobrain.util.MiscUtil.SeenIds;
import org.phobrain.util.ListHolder;
import org.phobrain.util.HashCount;
import org.phobrain.util.AtomSpec;

import org.phobrain.math.Higuchi;

import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.SessionDao;
import org.phobrain.db.dao.BrowserDao;
import org.phobrain.db.dao.UserDao;
import org.phobrain.db.dao.ShowingPairDao;
import org.phobrain.db.dao.PictureDao;
import org.phobrain.db.dao.KeywordsDao;
import org.phobrain.db.dao.PictureMapDao;
import org.phobrain.db.dao.PairDao;
import org.phobrain.db.dao.PairTopDao;
import org.phobrain.db.dao.UniquePairDao;
import org.phobrain.db.dao.ApprovalDao;

import org.phobrain.db.record.Session;
import org.phobrain.db.record.Browser;
import org.phobrain.db.record.Screen;
import org.phobrain.db.record.User;
import org.phobrain.db.record.ShowingPair;
import org.phobrain.db.record.DotHistory;
import org.phobrain.db.record.Picture;
import org.phobrain.db.record.PictureResponse;
import org.phobrain.db.record.Keywords;
import org.phobrain.db.record.PictureMap;
import org.phobrain.db.record.ApprovedPair;
import org.phobrain.db.record.Pair;
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

public class GetEngine {

    private static final Logger log = LoggerFactory.getLogger(GetEngine.class);

    final boolean KEYWORDS = false;

    private long DEBUG_SLEEP_O_KINGLY_BUG = 0;

    private int modMillis(int n) {
        return (int) (System.currentTimeMillis() % n);
    }

    private class UserProfile {

        long create = System.currentTimeMillis();

        List<ShowingPair> history;  // most recent first
        ShowingPair last = null;
        ShowingPair lastPlus = null;
        int prevRating = -1;
        boolean recentNeg = false;

        int deltaUserTime = 0;

        boolean longerThanLast = false;
        boolean longerThanSecondLast = false;
        boolean speedUp = false;

        boolean longClick = false;
        boolean longerClick = false;
        boolean longestClick = false;
        boolean shortClick = false;
        boolean shorterClick = false;

        // set by analyzeDots()
        int dots1 = 0;
        int dots2 = 0;
        int lastCrossings = 0;

        int avgDot = 0;
        int maxDot = 0;
        boolean lowDot = false;
        boolean medDot = false;
        boolean highDot = false;
        boolean prevDot = false;

        double skew = 0.0;
        double scaleSkew = 0.0;
        boolean extraStuff = false;
        boolean restlessMouse = false;

        int dotMap(int n) {
            if (last != null) {
                return last.dotMap(n);
            }
            return modMillis(n);
        }

        // Mouse click time filter
        //
        // Data:
        //   psql=> \o bill_mt_for_lorena
        //   psql=> select mouse_time from showing order by id asc;
        //   psql=> \o
        //  [remove -1 and times > 3 digits]
        //
        // Analysis:
        //   % pr/db/ck_an.sh < bill_mt_for_lorena
        //
        // 160 ms avg for me going over all of Lorena's pix
        // Using time cutoffs coded below results in counts of
        //
        //     >190  >170            <150  <130 < 50  (times in ms)
        //    longer long @ center @ short shorter
        //       184  245 @   521  @ 293   152  lots of 0's in prod: 258/700-odd
        //

        boolean longerThanLastUserTime = false;
        boolean longerThanSecondLastUserTime = false;
        boolean longerThanAverageUserTime = false;
        boolean longerThanAverageUserTime1Dev = false;

        int avgUserTime;
        double userTimeDev;
        int maxDeltaUserTime = 0;
        int minLoadTime = Integer.MAX_VALUE;

        UserProfile(Connection conn, long browserID, int screens,
                                     ShowingPair lastLocal)
            throws SQLException {

            history = ShowingPairDao.getLastShowings(conn, browserID, 10);

            if (history == null  ||  history.size() == 0) {
                log.info("UserProfile: history 0");
                return;
            }

            if (lastLocal != null) {
                last = lastLocal;
            } else {
                last = history.get(0);
            }

            if (history.size() > 1) {

                lastPlus = history.get(1);
                prevRating = lastPlus.rating;

                if (screens == 1) {

                    deltaUserTime = last.userTime - history.get(1).userTime;

                } else if (history.size() > 2) {

                    deltaUserTime = last.userTime - history.get(2).userTime;
                }
            }

            StringBuilder sum = new StringBuilder();

            sum.append("HISTORY ").append(history.size());

            // speedup is a shorter-range thing

            if (history.size() >= screens * SPEEDUP_COMPARE) {

                sum.append(" times: ");

                boolean stillGood = true;
                int[] times = new int[SPEEDUP_COMPARE];
                for (int i=0;i<SPEEDUP_COMPARE;i++) {
                    ShowingPair sp = history.get(i);
                    if (sp.rating != 3) { // need to compare apples/apples?
                        stillGood = false;
                        break;
                    }
                    times[i] = sp.userTime;
                    sum.append(sp.userTime).append(" ");
                }

                if (stillGood) {
                    // check for speedup
                    speedUp = true;
                    for (int i=0;i<SPEEDUP_COMPARE-1;i++) {
                        if (times[i] > times[i+1]) {
                            speedUp = false;
                            break;
                        }
                    }
                    if (speedUp) {
                        // TODO - if speedup, maybe add fake showings of
                        // rest in scene to exclude
                        //log.info("browser " + browserID + " speeding up");
                        sum.append("- speeding up ");
                    }
                }
                sum.append("\n");
            }

            int nCk = 0;
            int totCk = 0;
            int totCkAll = 0;
            int totUserAll = 0;
            int big = 0;
            int tot_dot = 0;
            int n_dot = 0;

            double scaleTime = 0.0;

            for (int i=0;i<history.size();i++) {

                ShowingPair sp = history.get(i);

                // use sp.tmpInt to encode drawing begin/end

                sp.tmpInt = 0;

                if (sp.rating == 10  &&  sp.dotStartScreen > 0) {

                    sp.tmpInt = (10 * sp.dotStartScreen) +
                                sp.dotEndScreen;
                    if (sp.tmpInt < 11  ||  sp.tmpInt > 22) {
                        sp.tmpInt *= -1;
                    }
                }

                if (i < 6  &&  sp.rating == 13) {
                    recentNeg = true;
                }

                totCkAll += sp.clickTime;
                totUserAll += sp.userTime;

                scaleTime += (double) sp.userTime / (i+1);

                if (sp.loadTime > 0  &&  sp.loadTime < minLoadTime) {
                    minLoadTime = sp.loadTime;
                }
                if (sp.clickTime > 40) {
                    totCk += sp.clickTime;
                    nCk++;
                    if (sp.clickTime > 1000) {
                        big++;
                    }
                }
                if (sp.dotCount > 0) {
                    tot_dot += sp.dotCount;
                    n_dot++;
                    if (sp.dotCount > maxDot) {
                        maxDot = sp.dotCount;
                    }
                }
            }
            avgUserTime = totUserAll / history.size();

            scaleSkew = (double) last.userTime / scaleTime;
            log.info("scaleSkew " + scaleSkew);
            //if (scaleSkew > 1.0) {
            //}

            userTimeDev = 0.0;
            for (ShowingPair sp : history) {
                int t = Math.abs(sp.userTime - avgUserTime);
                userTimeDev += (double)t * (double)t;
//log.info("\ngUseruserTimeDev " + userTimeDev + " tt " + tt + " t " + t);
                if (t > maxDeltaUserTime) {
                    maxDeltaUserTime = t;
                }
            }
            userTimeDev /= (double)history.size();
            userTimeDev = Math.sqrt(userTimeDev);
            sum.append("\navgUserT ").append(avgUserTime)
               .append(" dev ").append(userTimeDev)
               .append(" maxDelta ").append(maxDeltaUserTime)
               .append(" N ").append(history.size()).append("\n");
            sum.append("\nlastUT ").append(last.userTime).append("\n");

            if (history.size() > 1) {

                if (last.clickTime > history.get(1).clickTime) {
                    sum.append("longerThanLast true\n");
                    longerThanLast = true;
                }
                if (last.userTime > history.get(1).userTime) {
                    sum.append("longerThanLastUserTime true\n");
                    longerThanLastUserTime = true;
                }
                if (last.userTime > avgUserTime) {
                    sum.append("longerThanAverageUserTime true\n");
                    longerThanAverageUserTime = true;
                }
                if (last.userTime > avgUserTime + userTimeDev) {
                    sum.append("longerThanAverageUserTime true\n");
                    longerThanAverageUserTime1Dev = true;
                }
                if (history.get(1).dotCount > 0) {
                    prevDot = true;
                }
                if (history.size() > 2) {
                    if (last.clickTime > history.get(2).clickTime) {
                        sum.append("longerThanSecondLast true\n");
                        longerThanSecondLast = true;
                    }
                    if (last.userTime > history.get(2).userTime) {
                        sum.append("longerThanSecondLastUserTime true\n");
                        longerThanSecondLastUserTime = true;
                    }
                }
            }

            avgDot = n_dot > 0 ? tot_dot / n_dot : 0;

            if (last.dotCount > 0) {

                int margin = avgDot / 10;
                if (margin == 0) {
                    margin = 5;
                }
                sum.append("avg_dot/this ").append(avgDot).append("/")
                                           .append(last.dotCount);
                if (last.dotCount < avgDot - margin) {
                    lowDot = true;
                    sum.append(" lowdot\n");
                } else if (last.dotCount > avgDot + margin) {
                    highDot = true;
                    sum.append(" highdot\n");
                } else {
                    medDot = true;
                    sum.append(" meddot\n");
                }
            }

            if (nCk == 0) {

                // cell phones or other monsters - enter the twilight zone
                // last may have taken a long time to load so user clicked
                //      ahead?
                // might be normal in tap devices (android) where 0's seen
                //      except a 4 based on 700-odd total showings
                // TODO - average/margin? data doesn't seem even enough

                sum.append("ct/vt ").append(last.clickTime).append("/")
                                    .append(last.userTime);
                if (last.clickTime <= 0) {
                    if (last.userTime < 1000) {        // 116 cases
                        shorterClick = true;
                        sum.append(" shorterCk/view\n");
                    } else if (last.userTime < 3000) { // 129 cases
                        // drop thru
                        sum.append(" center/viewtime\n");
                    } else {                           //  13 cases, 3 very long
                        longClick = true;
                        sum.append(" longCk/view\n");
                    }

                } else if (last.clickTime < 3) {
                    // long time for a phone/tablet,
                    // maybe impossible for a mouse

                    shorterClick = true;
                    sum.append(" shorterCk(tap)\n");
                } else if (last.clickTime < 10) {
                    // drop thru
                    sum.append(" center/cell\n");
                } else if (last.clickTime < 20) {
                    longClick = true;
                    sum.append(" longCk(tap)\n");
                } else if (last.clickTime < 50) {
                    longerClick = true;
                    sum.append(" longerCk(tap)\n");
                } else {
                    longestClick = true;
                    sum.append(" longestCk(tap)\n");
                }

            } else {  // nCk > 0

                int avg = totCk / nCk;
                sum.append("avg_ct/this ").append(avg)
                   .append("/").append(last.clickTime);

                if (last.clickTime > avg * 2) {
                    // user choice - should dot count override when > 0?
                    longestClick = true;
                    sum.append(" longestCk\n");
                } else if (last.clickTime > (int)(1.5 * (double)avg)) {
                    longerClick = true;
                    sum.append(" longerCk\n");
                } else if (last.clickTime > (int)(1.25 * (double)avg)) {
                    longClick = true;
                    sum.append(" longCk\n");
                } else if (last.clickTime == 0) {
                    // enter the twilight zone
                    // last may have taken a long time to load so user
                    //      clicked ahead?
                    // might be normal in tap devices (android) where 0's
                    //      seen except a 4 based on 700-odd total showings
                    if (last.userTime < 1000) {        // 116 cases
                        shorterClick = true;
                        sum.append(" shorterCk/view (").append(last.clickTime)
                           .append(")\n");
                    } else if (last.userTime < 3000) { // 129 cases
                        // drop thru
                        sum.append(" center/viewtime\n");
                    } else {                           //  13 cases, 3 very long
                        longClick = true;
                        sum.append(" longCk/view (").append(last.clickTime)
                           .append(")\n");
                    }
                } else if (last.clickTime < 3) {
                    // long time for a phone/tablet, maybe impossible
                    //      for a mouse
                    shorterClick = true;
                    sum.append(" shorterCk(tap)\n");
                } else if (last.clickTime < 10) {
                    // drop thru
                    sum.append(" center/cell\n");
                } else if (last.clickTime < 20) {
                    longClick = true;
                    sum.append(" longCk(tap)\n");
                } else if (last.clickTime < 50) {
                    longerClick = true;
                    sum.append(" longerCk(tap)\n");
                } else if (last.clickTime < (int)(0.5 * (double)avg)) { // MAGIC
                    shorterClick = true;
                    sum.append(" shorterCk\n");
                } else if (last.clickTime < (int)(0.75 * (double)avg)) { // MAGIC
                    shortClick = true;
                    sum.append(" shortCk\n");
                } else {
                    sum.append(" center\n");
                }
            }

            int maxA = 0;
            int lenArea = 0;

            int totView = 0;
            int minView = 99999999;
            int maxView = 0;

            int maxDepth = history.size();

            for (int i=0;i<maxDepth;i++) {

                ShowingPair sp = history.get(i);

                totView += sp.userTime;
                if (sp.userTime > maxView) maxView = sp.userTime;
                if (sp.userTime < minView) minView = sp.userTime;

                if (sp.mouseMaxa > 300) {
                    maxA++;
                }
                if (sp.mouseDx > 0  &&  sp.mouseDy > 0) {
                    float ratio = (float)sp.mouseDist /
                                  (float)(sp.mouseDx * sp.mouseDy);
                    if (ratio > 0.6) {
                        lenArea++;
                    }
                }
            }

            int avgView = totView / maxDepth;

            // t = longest diff(avg to max, min)
            int t = avgView - minView;
            int tt = maxView - avgView;
            if (tt > t) t = tt;
            if (t == 0) {
                log.info("SKEW t->2");
                t = 2;
            }
            int avgdiff = Math.abs(avgView - last.userTime);
            skew = (double) avgdiff / t;
            log.info("SKEW 1 " + skew +
                     " avgdiff " + avgdiff +
                     " t " + t);
            if (skew == Double.NaN) {
                log.error("SKEW avgView " + avgView +
                          " last.userTime " + last.userTime +
                          " t " + t);
                skew = 0.5;
            }

            skew -= Math.floor(skew);
            log.info("SKEW 2 " + skew);
            skew += 1.0;
            log.info("SKEW 3 " + skew);

            // skew is 1.x

            if (longClick) {
//log.info("SKEW t " + skew + " long");
                // skew = -1.0 + skew * 2.0;
                // skew = Math.acos(skew) / Math.PI;
                skew = Math.pow(skew, 2.0);
            } else if (longerClick) {
//log.info("SKEW t " + skew + " longer");
                //skew = -1.0 + skew * 2.0;
                //skew =  ((0.5 * Math.PI) + Math.asin(skew)) / Math.PI;
                skew = Math.pow(skew, 3.0);
            } else if (longestClick) {
//log.info("SKEW t " + skew + " longest");
                //skew = -1.0 + skew * 2.0;
                //skew =  ((0.5 * Math.PI) + Math.atan(skew)) / Math.PI;
                skew = Math.pow(skew, 4.0);
            } else if (shortClick) {
//log.info("SKEW t " + skew + " short");
                //skew = 0.5 * (1.0 + Math.sin(skew));
                skew = Math.pow(2.0, skew);
            } else if (shorterClick) {
//log.info("SKEW t " + skew + " shorter");
                //skew = 0.5 * (1.0 + Math.cos(skew));
                skew = Math.pow(3.0, skew);
            } else if (last.clickTime > 0) {
//log.info("SKEW t " + skew + " reg");
                skew = Math.exp(skew);
            }
            skew = Math.abs(skew);
            skew -= Math.floor(skew);
            if (longerThanSecondLastUserTime) {
                skew = 1.0 - skew;
            }
            log.info("SKEWit " + skew);

            if (last.mouseMaxa > 300  &&  maxA > maxDepth / 2) {
                extraStuff = true;
                sum.append(" extra on maxa ").append(maxA).append("\n");
            } else if (lenArea > maxDepth / 2) {
                extraStuff = true;
                sum.append(" extra on lenarea ").append(lenArea).append("\n");
            }

            restlessMouse = restlessFilter(history, sum);

            if (last != null  &&  last.dotHistory != null) {
                last.analyzeDots();
                dots1 = last.dotHistory.dots1;
                dots2 = last.dotHistory.dots2;
                lastCrossings = last.dotHistory.lastCrossings;
            }

            log.info(sum.toString());
        }

        // in UserProfile
        private boolean restlessFilter(List<ShowingPair> history,
                                       StringBuilder sum) {

            ShowingPair last = history.get(0);

            if (last.mouseMaxv > 300) {
                // most general case
                sum.append(" restlessMs vel ").append(last.mouseMaxv)
                                                 .append("\n");
                return true;
            }
            if (last.mouseDist > 200  &&  last.dotCount == 0) {
                // not fast, but far without drawing
                sum.append(" restlessMs longer dist ").append(last.mouseDist)
                                                         .append("\n");
                return true;
            }
            if (last.rating == 4  &&  last.pixOutPic > 0) {
                sum.append(" restlessMs pixOutPic ").append(last.mouseDist)
                                                         .append("\n");
                return true;
            }

            if (history.size() > 1) {

                // check for mouse straying between clicks on the option

                ShowingPair sp = history.get(1);
                if (last.rating == sp.rating  &&  last.mouseDist > 25) {
                    sum.append(" restlessMs dist ")
                       .append(last.mouseDist).append("\n");
                    return true;
                }
            }
            return false;
        }


        /**
         *  scaleInt() - return 1..top based on scaleSkew
         */
        int scaleInt(int top) {

            if (scaleSkew > 0.0  &&  scaleSkew < 1.0) {
                return 1 + (int) (top * scaleSkew);
            }
            log.warn("scaleSkew out of bounds: " + scaleSkew);
            if (last.userTime > 0) {
                return 1 + last.userTime % top;
            }
            return rand.nextInt(top);
        }

        boolean sameMethod(int n) {

            if (n > history.size()) {
                return false;
            }

            String method = history.get(0).selMethod1;
            if (!method.equals(history.get(0).selMethod2)) {
                return false;
            }

            for (int i=1;i<n;i++) {
                if (!method.equals(history.get(i).selMethod1)) {
                    return false;
                }
                if (!method.equals(history.get(i).selMethod2)) {
                    return false;
                }
            }
            return true;
        }
    }

    /*
    **  dotHistMe - use drawn dots to derive per-pic weights
    **      for weighted-random selection. The original weights/pics
    **      are sorted in descending order, so this overlays TODO combines
    **      the ordering with the new weights.
    **      Starting with histograms summarizing mouse movement
    **      sent from browser, with a linear map of histo to
    **      whatever size ListHolder is passed.
    **          2024_03_12 Trying first masking out the pics
    **              matching histo 0's.
    */
    private void dotHistMe(int[] hist, ListHolder lh, StringBuilder sb) {

        if (hist == null) {
            throw new RuntimeException("dotHistMe: hist null");
        }

        // map hist(ogram) size to lh
        double factor = (double) hist.length / lh.size();

        log.info("dotHistMe factor " + factor +
             " pics " + lh.size() +
             " hist " + hist.length +
             ": " + Arrays.toString(hist));

        ListHolder lhx = new ListHolder();
        lhx.dbl_l = new ArrayList<>();

        for (int i=0; i<lh.value_l.size(); i++) {

            int j = (int) ((double)i * factor);
            if (j >= hist.length) {
                break;
                //log.error("was FATAL i=" + i + "  j=" + j); //System.exit(1);
            }

            // TODO add instead of replace
            //      ...scale somehow?

            if (hist[j] < 1) {
                // drop 0's when replacing
                continue;
            }
            lhx.id2_l.add(lh.id2_l.get(i));
            lhx.value_l.add((long) hist[j]);
            // TODO add pulse or other func
            lhx.dbl_l.add((double) hist[j]);
        }

        String s = "dotHistMe: " + hist.length + " -> " +
                    lh.size() + "->" + lhx.size() + "\n" +
                    Arrays.toString(lhx.value_l.toArray());
        log.info(s);

        if (sb != null) {
            sb.append(s).append("\n");
        }

        lh.id2_l = lhx.id2_l;
        lh.value_l = lhx.value_l;
        lh.dbl_l = lhx.dbl_l;
    }

    private void dotHistMe(ShowingPair sp, ListHolder lh, StringBuilder sb) {

        // choose a histogram

        int[] hist = null;
        switch (sp.dotCount % 3) {
        case 0:
            hist = sp.dotHistory.angleHist; // 36
            break;
        case 1:
            hist = sp.dotHistory.distHist; // 51
            break;
        case 2:
        default:
            hist = sp.dotHistory.velocityHist; // 51
            break;
        }

        dotHistMe(hist, lh, sb);

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
*/
    public int INTRO_FIRSTS = 10;
    public int INTRO_PREF_IMAGES = 100;

    public final int SPEEDUP_COMPARE = 4;

    private double TAP_MAX = 0.0;
/*
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

    private static final Random rand = new Random();

    private static String PHOBRAIN_LOCAL;

    private static String GRAPH_DIR;
    private static String PULSE_FILE;
    private static String PIC_DELAY;
    private static boolean PIC_DELAY_UP;
    private static boolean BIG_DELAY_UP;
    private static int BASE_UP_SLEEP;
    private static int BASE_DOWN_SLEEP;

    private static int ACCEPT_SEEN_SKIP = 1;

    public static int N_DEEP;
    public static int PAIR_DELTA_FACTOR;
    public static int NET_LATENCY_CUT;

    private static String statusDir = null;

    protected static final ServletData data = ServletData.get();

    private static GetEngine instance = null;

    public static GetEngine getEngine() {
        synchronized(GetEngine.class) {
            if (instance == null) {
                instance = new GetEngine();
            }
        }
        return instance;
    }

    private GetEngine() {

        Connection conn = null;
        try {
            conn = DaoBase.getConn();

            long t1 = System.currentTimeMillis();

            DEBUG_SLEEP_O_KINGLY_BUG = Long.parseLong(
                            ConfigUtil.runtimeProperty("debug.sleep.o.kingly_bulg", "0"));

            // TODO - use same algo as other apps??
            PHOBRAIN_LOCAL = ConfigUtil.runtimeProperty("local.dir");

            statusDir = ConfigUtil.runtimeProperty("status.dir");
            if (statusDir == null) {
                log.warn("No status.dir, using local.dir");
                statusDir = ConfigUtil.runtimeProperty("local.dir");
            } else {
                statusDir = PHOBRAIN_LOCAL + "/" + statusDir;
            }
            GRAPH_DIR = PHOBRAIN_LOCAL + "/" + ConfigUtil.runtimeProperty("graph.dir");
            //PULSE_FILE = PHOBRAIN_LOCAL + "/" + p.getProperty("pulse.file");
            PULSE_FILE = ConfigUtil.runtimeProperty("pulse.file");
            String tmax = ConfigUtil.runtimeProperty("tap.max");
            TAP_MAX = Double.parseDouble(tmax);
            log.info("tap.max " + TAP_MAX);

            PIC_DELAY = ConfigUtil.runtimeProperty("pic.delay.alg", "density");
            String t = ConfigUtil.runtimeProperty("pic.delay.alg.up", "true");
            PIC_DELAY_UP = Boolean.parseBoolean(t);
            t = ConfigUtil.runtimeProperty("big.delay.alg.up", "true");
            BIG_DELAY_UP = Boolean.parseBoolean(t);
            t = ConfigUtil.runtimeProperty("big.delay.up.sleep", "100");
            BASE_UP_SLEEP = Integer.parseInt(t);
            t = ConfigUtil.runtimeProperty("big.delay.down.sleep", "500");
            BASE_DOWN_SLEEP = Integer.parseInt(t);

            t = ConfigUtil.runtimeProperty("n.deep", "10");
            N_DEEP = Integer.parseInt(t);

            log.info("n.deep " + N_DEEP);

            t = ConfigUtil.runtimeProperty("big.delay.delta.factor", "200");
            PAIR_DELTA_FACTOR = Integer.parseInt(t);

            t = ConfigUtil.runtimeProperty("big.delay.net.latency.cut", "600");
            NET_LATENCY_CUT = Integer.parseInt(t);

            log.info("\n pic.delay.alg " + PIC_DELAY +
                     "\n pic.delay.alg.up " + PIC_DELAY_UP +
                     "\n big.delay.alg.up " + BIG_DELAY_UP +
                     "\n big.delay.up.sleep " + BASE_UP_SLEEP +
                     "\n big.delay.down.sleep " + BASE_DOWN_SLEEP +
                     "\n big.delay.delta.factor " + PAIR_DELTA_FACTOR);

            log.info("Load time: " + (System.currentTimeMillis()-t1) + " ms");
            /*
            initNNOpts(p.getProperty("n_pairtop_nn_v"),
                       p.getProperty("n_pairtop_nn_h"));
            log.info("Load time: " + (System.currentTimeMillis()-t1) + " ms");
            */
        } catch (NamingException ne) {
            log.error("init: Naming", ne);
        } catch (SQLException sqe) {
            log.error("init: DB: " + sqe, sqe);
        } finally {
            DaoBase.closeSQL(conn);
        }
        log.info("GetEngine Init OK");
    }

    private double click2Skew(int screen, String clickLoc) {

        if (clickLoc == null) {
            return rand.nextDouble();
        }

        double skew = 0.0;

        if (clickLoc.endsWith("c")) {
            if (screen == 0) {
                // MAGIC based on side-by-side screens centered over icons
                if (clickLoc.startsWith("LT")) {
                    skew = 0.2;
                } else if (clickLoc.startsWith("LB")) {
                    skew = 0.4;
                } else if (clickLoc.startsWith("RT")) {
                    skew = 0.6;
                } else if (clickLoc.startsWith("RB")) {
                    skew = 0.5;
                } else {
                    log.error("UNEXPECTED clickLoc: " + clickLoc);
                    skew = 0.0;
                }
            } else {
                // MAGIC mirrors screen 0
                if (clickLoc.startsWith("LT")) {
                    skew = 0.6;
                } else if (clickLoc.startsWith("LB")) {
                    skew = 0.5;
                } else if (clickLoc.startsWith("RT")) {
                    skew = 0.2;
                } else if (clickLoc.startsWith("RB")) {
                    skew = 0.4;
                } else {
                    log.error("UNEXPECTED clickLoc: " + clickLoc);
                    //skew = 0.0;
                }
            }
       } else if (screen == 0) {
            // MAGIC based on side-by-side screens centered over icons
            if ("LT".equals(clickLoc)) {
                skew = 0.0;
            } else if ("LB".equals(clickLoc)) {
                skew = 0.6;
            } else if ("RT".equals(clickLoc)) {
                skew = 0.7;
            } else if ("RB".equals(clickLoc)) {
                skew = 0.5;
            } else {
                log.error("Unexpected clickLoc: " + clickLoc);
                //skew = 0.0;
            }
        } else {
            // MAGIC mirrors above
            if ("LT".equals(clickLoc)) {
                skew = 0.7;
            } else if ("LB".equals(clickLoc)) {
                skew = 0.5;
            } else if ("RT".equals(clickLoc)) {
                skew = 0.0;
            } else if ("RB".equals(clickLoc)) {
                skew = 0.6;
            } else {
                log.error("Unexpected clickLoc: " + clickLoc);
                //skew = 0.0;
            }
        }
        log.info("SKEW " + skew + " screen " + screen +
                        " clickLoc " + clickLoc);

        return skew;
    }

    private int getPulse() {
        //log.warn("skipping pulse for now");
        return 1;
        /*
        BufferedReader in = null;
        try {
            int sum = 0;
            int count = 0;
            double lastV = 1.0;
            in = new BufferedReader(new FileReader(PULSE_FILE));
            String line;
            while ((line = in.readLine()) != null) {
                double v = Double.parseDouble(line.trim());
                count++;
                sum += (int) v;
                lastV = v;
            }
            if (count == 0) {
                log.error("No pulse from " + PULSE_FILE);
                return 1;
            }
            int avg = sum / count;
            int val = Math.abs(avg - (int)lastV);
            if (val == 0) {
                return 1;
            }
            return val;
        } catch (Exception e) {
            log.error("Getting pulse: " + e);
            return 1;
        } finally {
            if (in != null) {
                try { in.close();} catch (Exception ignore) {}
            }
        }
        */
    }

    private static Map<Long, List<String>> browserLastList =
                                            new ConcurrentHashMap<>();
                              //Collections.synchronizedMap(new HashMap<>());

    private static Map<Long, SeenIds> browserSeen =
                                            new ConcurrentHashMap<>();
                              //Collections.synchronizedMap(new HashMap<>());

    private class NeighborsCache {

        long created = System.currentTimeMillis();

        int viewNum;
        String orient;

        List<Pair> upList = new ArrayList<>();
        List<Pair> downList = new ArrayList<>();

        NeighborsCache(int viewNum, String orient) {
            this.viewNum = viewNum;
            this.orient = orient;
        }
    }

    // nbr and nextnbr

    private static Map<Long, NeighborsCache> d0NbrCache =
                                            new ConcurrentHashMap<>();
    private static Map<Long, NeighborsCache> d0NextNbrCache =
                                            new ConcurrentHashMap<>();

    private class D0BadCache {

        long created = System.currentTimeMillis();

        int viewNum;
        String orient;

        List<Pair> list = new ArrayList<>();

        D0BadCache(int viewNum, String orient) {
            this.viewNum = viewNum;
            this.orient = orient;
        }
    }

    private static Map<Long, D0BadCache> d0BadCache =
                                            new ConcurrentHashMap<>();


    public static void clearBrowserSeen(long browserID) {
        log.info("clearBrowserSeen " + browserID);
        browserSeen.remove(browserID);

        browserLastList.remove(browserID);// seems like a good idea
        d0NbrCache.remove(browserID);// seems like a good idea
        d0NextNbrCache.remove(browserID);// seems like a good idea
        // TODO: trim old nbr cache
    }

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
                    ret.seen = ShowingPairDao.getSeen(conn, session.browserID);
                    ret.vertical = ShowingPairDao.countShowings(conn,
                                                  session.browserID, "v");
                    ret.horizontal = ShowingPairDao.countShowings(conn,
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
            ret.seen = ShowingPairDao.getSeen(conn, session.browserID);
            ret.vertical = ShowingPairDao.countShowings(conn,
                                                  session.browserID, "v");
            ret.horizontal = ShowingPairDao.countShowings(conn,
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

    public void addSeen(Connection conn, long browserID, String id,
                        String orient)
            throws SQLException {
        log.info("addSeen " + browserID + " " + id);

        SeenIds set = browserSeen.get(browserID);
        if (set == null) {
            log.info("SET NULL " + browserID);
            set = new SeenIds();
            browserSeen.put(browserID, set);
        }
        if (set.seen == null) {
            // TODO - roll into 1 query
            set.seen = ShowingPairDao.getSeen(conn, browserID);
            set.vertical = ShowingPairDao.countShowings(conn,
                                                  browserID, "v");
            set.horizontal = ShowingPairDao.countShowings(conn,
                                                  browserID, "h");
        }
        set.seen.add(id);
        if ("v".equals(orient)) {
            set.vertical++;
        } else {
            set.horizontal++;
        }
        // by the time we are adding is a good time to
        // purge any exclude
        set.exclude.clear();
    }

    private static Map<Long, Integer> browserLastOrderInSession =
                                                        new HashMap<>();

    public synchronized static int getOrderInSession(Connection conn,
                                                     long browserID)
            throws SQLException {
        Integer order = browserLastOrderInSession.get(browserID);
        if (order == null) {
            int n = ShowingPairDao.countShowings(conn, browserID);
            order = n + 1;
        } else {
            order++;
        }
        browserLastOrderInSession.put(browserID, order);
        return order;
    }

    public PictureResponse chooseRandomImage(Connection conn,
                                                int viewNum,
                                                Session session)
            throws SQLException {

        return chooseRandomImage(conn, viewNum, null, session, true);
    }

    public PictureResponse chooseRandomImage(Connection conn,
                                                int viewNum, String orient,
                                                Session session, //int option,
                                                boolean fallbackLinear)
            throws SQLException {

//if (true) new Exception("Rand???").printStackTrace();

        browserLastList.remove(session.browserID);// clear Plus history

        List<String> l = data.getPicList(viewNum, orient);;
        if (l == null  ||  l.size() == 0) {
            throw new RuntimeException(
                     "chooseRandomImage: List size 0 for viewNum " + viewNum);
                     // + " option " + option);
        }

        PictureResponse pr = new PictureResponse();
        pr.method = "rand/" + orient;

        log.info("Chooserand: " + // option " + option + " " + pr.method +
                 " view " + viewNum +
                 " size " + l.size() +
                 " orient " + orient);

        // give it a try
        int tries = l.size() / 2;
        if (tries > 1000) {
            tries = 1000;
        }
//for (String s : l) {
//log.info("ZZ " + s);
//}

        SeenIds seenIds = getSeen(conn, session);

        for (int i=0;i<tries;i++) {
            //log.info("browser " + session.browserID + " Random try");
            int which = rand.nextInt(l.size());
            String id = l.get(which);

            if (seenIds.contains(id)) {
                continue;
            }

            log.info("browser " + session.browserID + " Random: tries " + i + " id: " + id);
            pr.p = PictureDao.getPictureById(conn, id);
            if (pr.p == null) {
                log.info("No pic " + id);
                continue;
            }
            pr.method += "/" + i;
            return pr;
        }

        // get ugly

        log.info("browser " + session.browserID +
                 ": Couldn't find unseen id in " + tries +
                 " tries on size of " + l.size() +
                 (fallbackLinear ?
                   "\nFalling back on sequential search of master list" : ""));

        if (!fallbackLinear) {
            return null;
        }

        // TODO cache list
        for (String id : l) {
            if (seenIds.contains(id)) {
                continue;
            }
            pr.p = PictureDao.getPictureById(conn, id);
            if (pr.p == null) {
                log.error("No pic " + id + " orient " + orient);
                continue;
            }
            pr.method += "->seq";
            return pr;
        }
        //log.info("totally done");
        return null;
        //throw new SQLException("Couldn't find unseen image");
    }


    private int selectRandomDistributedValue(List<Double> l, int limit) {
        if (l == null) {
            throw new RuntimeException(
                               "selectRandomDistributedValue list null");
        }
        if (l.size() == 0) {
            throw new RuntimeException(
                               "selectRandomDistributedValue list size 0");
        }
        if (l.size() == 1) {
            return 0;
        }
        if (limit == -1  ||  limit > l.size()) {
            limit = l.size();
        }
        double sum = 0.0;
        for (int i=0;i<limit;i++) {
            sum += l.get(i);
        }
        double d = rand.nextDouble();
        double target = sum * d;
        int choice = 0;
        target -= l.get(choice);
        while (target > 0.0) {
            choice++;
            if (choice == l.size()) {
                choice--;
                break;
            }
            target -= l.get(choice);
        }
        //log.info("RANDOM (" + choice + "/" + limit + ") " +
        //                  target + "/" + sum + " " + d);
        return choice;
    }

    private List<Long> invertDistribution(List<Long> l) {
        long max = - Long.MAX_VALUE;
        long min = Long.MAX_VALUE;
        for (long i : l) {
            if (i > max) max = i;
            if (i < min) min = i;
        }
        long range = max - min;
        if (range < 0) {
            log.error("invertDistribution: range negative");
            range *= -1;
        }
        //if (min < 0) range -= min;

        range++;
        List<Long> ll = new ArrayList<>(l.size());
        for (long i : l) {
            ll.add(range - i);
        }
        return ll;
    }

    private List<Double> sortList(List<Double> l) {
        List<Double> ll = new ArrayList<>(l);
        Collections.sort(ll, Collections.reverseOrder());
        return ll;
    }



    private void createDblL(ShowingPair sp, ListHolder lh, StringBuilder sb) {

        if (sp != null  &&  sp.dotCount > 0) {
            // reflect user movement
            dotHistMe(sp, lh, sb);
            return;
        }

        lh.dbl_l = new ArrayList<>();

        int p = getPulse();
        double scale = Math.sqrt(p);
        if (sb != null) {
            sb.append("SCALE ").append(scale).append(" ");
        }
        boolean exceeded = false;// you know it when you see it
        for (int i=0;i<lh.value_l.size();i++) {
            double d = Math.pow(lh.value_l.get(i), scale);
            if (d == Double.POSITIVE_INFINITY) {
                exceeded = true;
                d = Double.MAX_VALUE;
            } else {
                //sum += d;
                //ct++;
            }
            lh.dbl_l.add(d);
        }
        if (exceeded) {
            if (sb != null) {
                sb.append("EXCEEDED ");
            } else {
                log.info("EXCEEDED ");
            }
        }
    }

    /*
    **  createDblL - simple translation of value_l
    */
    private void createDblL(ListHolder lh) {
        createDblL(null, lh, null);
    }

    private PictureResponse tryLhP(Connection conn,
                                    Session session, String orient,
                                    int[] histogram,
                                    ListHolder lh)
            throws SQLException {

        int size = lh.size();

        if (histogram == null) {
            createDblL(lh);
        } else {
            dotHistMe(histogram, lh, null);
        }

        if (lh.dbl_l.size() == 0) {
            log.error("dbl_l size is 0, value_l " + lh.value_l.size());
        }

        int ct = 0;

        SeenIds seenIds = getSeen(conn, session);

        //for (int i=0;i<lh.size();i++)
        while (lh.size() > 0) {

            ct++;

            int choice = selectRandomDistributedValue(lh.dbl_l, -1);

            String id2 = lh.id2_l.get(choice);
            if (seenIds.contains(id2)) {
                lh.id2_l.remove(choice);
                lh.value_l.remove(choice);
                lh.dbl_l.remove(choice);
                continue;
            }
            PictureResponse pr = new PictureResponse();
            pr.p = PictureDao.getPictureById(conn, id2);
            if (pr.p == null) {
                log.info("No version for unseen seq " + id2);
                lh.id2_l.remove(choice);
                lh.value_l.remove(choice);
                lh.dbl_l.remove(choice);
                continue;
            }
            return pr;
        }
        return null;
    }

    private PictureResponse tryLhP(Connection conn,
                                    Session session, String orient,
                                    ShowingPair sp,
                                    ListHolder lh)
            throws SQLException {

        int[] hist = null;
        switch (sp.dotCount % 3) {
        case 0:
            hist = sp.dotHistory.angleHist; // 36
            break;
        case 1:
            hist = sp.dotHistory.distHist; // 51
            break;
        case 2:
        default:
            hist = sp.dotHistory.velocityHist; // 51
            break;
        }

        return tryLhP(conn, session, orient, hist, lh);
    }

    private void logIt(ListHolder lh) {
        logIt(lh, true);
    }

    final boolean noPrint = true;

    // rescales if sort=true
    private void logIt(ListHolder lh, boolean sort) {
        if (lh.value_l == null  ||  lh.value_l.size() == 0) {
            log.info("skipping logIt for null/empty value_l");
            return;
        }
        if (statusDir == null) {
            throw new RuntimeException("No status.dir");
        }
        if (lh.size() == 0) {
            throw new RuntimeException("logIt: list size is 0");
        }

        double min = Double.MAX_VALUE;
        double max = 0.0;
        StringBuilder sb = new StringBuilder();
        sb.append(" ");
        boolean rescaled = false;
        boolean trimmed = false;
        long firstVal = -1;
        long lastVal = -1;
        try {
            PrintWriter out = null;
            if (!noPrint) {
                out = new PrintWriter(statusDir + "/XX");
            }
            if (sort) {
                // coincides with weighted selection, so scale
                // and factor in pulse and create dbl_l
                for (long l : lh.value_l) {
                    if (l > max) max = l;
                    if (l < min) min = l;
                }
                long offset = 0;
                if (min > (max - min) / 5.0) {
                    offset = (int)min - 2;
                    rescaled = true;
                    for (int i=0;i<lh.value_l.size();i++) {
                        lh.value_l.set(i, lh.value_l.get(i) - offset);
                    }
                    // null == no dot-derived weights
                    createDblL(null, lh, sb);
                }

                double sum = 0.0;
                int ct = 0;

                if (lh.dbl_l == null) {

                    // null == no dot-derived weights
                    createDblL(null, lh, sb);

                } else {
                    boolean exceeded = false;
                    for (int i=0;i<lh.dbl_l.size();i++) {
                        double d = lh.dbl_l.get(i);
                        if (d == Double.MAX_VALUE) {
                            exceeded = true;
                        } else {
                            sum += d;
                            ct++;
                        }
                    }
                    if (exceeded) {
                        sb.append("EXCEEDED ");
                    }
                }

                // get sorted list to print & min, max

                List<Double> ll = sortList(lh.dbl_l);
                min = ll.get(ll.size()-1);
                max = ll.get(0);

                // initial cutoff is half the average

                double cutoff = (sum / ct) * 0.5;

                double doffset = 0.0;

                if (min > (max - min) / 5.0) {
                    doffset = min - 2.0;
                    sb.append("RESCALE ").append(doffset).append(" ");
                    rescaled = true;
                    for (int i=0;i<lh.dbl_l.size();i++) {
                        lh.dbl_l.set(i, lh.dbl_l.get(i) - doffset);
                    }
                    for (int i=0;i<ll.size();i++) {
                        ll.set(i, ll.get(i) - doffset);
                    }
                    min -= doffset;
                    max -= doffset;
                }
                if (!noPrint) {
                    for (Double d : ll) {
                        out.println("" + d);
                    }
                }
            } else {
                if (!noPrint) {
                    firstVal = lh.value_l.get(0);
                    lastVal = lh.value_l.get(lh.value_l.size()-1);

                    for (Long l : lh.value_l) {
                        if (l > max) max = l;
                        if (l < min) min = l;
                        out.println("" + l);
                    }
                }
            }
            if (!noPrint) {
                out.close();
            }
        } catch (Exception e) {
            log.error("LogIt", e);
        }
        log.info("Graphed SIZE: " + lh.size() +
                 " RANGE (min-max): " + min + "-" + max +
                 (sort ? " Sort: " + sb.toString() :
                         " (choose first: " + firstVal +
                           " last " + lastVal + ")"));
    }

    private Picture chooseFromDistribution(Connection conn, String type,
                                     Session session, UserProfile up,
                                     ListHolder lh, int limit)
                throws SQLException {
        return chooseFromDistribution(conn, type, session, up, lh, limit, null);
    }

    private Picture chooseFromDistribution(Connection conn, String type,
                                     Session session, UserProfile up,
                                     ListHolder lh, int limit,
                                     String orient)
                throws SQLException {

        // ListHolder entries in range < limit expended/replaced
        //  up to limit times

        if (lh.size() != lh.value_l.size()) {
            throw new RuntimeException(
                         "chooseFromDistribution: inconsistent sizes: id2_l: " +
                         lh.size() + " value_l: " + lh.value_l.size() );
        }

        logIt(lh, limit != -2);// creates dbl_l first time;-2 for d0/noscale

        long t1 = System.currentTimeMillis();

        int tryLimit = limit;
        if (tryLimit < 1) {
            tryLimit = lh.size();
        }
        // restrict choice to first 25, hopefully top TODO verify cases
        // 3/18 - bumped to 50 since too much uniformity in NN
        //        results. TODO - retest w/ color and maybe use
        //        25 for that.
        // 4/18 - extend > 50 if speedup / fast
        //        TODO - add flattening of curve to simulate higher temp
        //
        int baseLimit = 50;
        if (limit == -2) {
            //baseLimit = lh.size();
            //baseLimit *= (8.0 + up.skew);// MAGIC
            double fac = up.skew;
            if (fac == 0.0) {
                log.error("skew is 0");
                fac = 0.5;
            }
            while (fac < 3.0) {
                fac *= 10.0;
            }
            int ifac = (int) fac;
            if (ifac == 0) {
                log.error("fac->0? " + fac);
                baseLimit = lh.size();
            } else {
                baseLimit = lh.size() / ifac;
            }
            log.info("d0 baseLimit -> " + baseLimit);
        } else if (up.speedUp  ||  up.restlessMouse) {
            baseLimit *= (1.25 + up.skew);// MAGIC
            //log.info("baseLimit -> " + baseLimit);
        }
/*
assume list is sorted.. couldn't go too wrong
        // if the list is sorted, focus on the high end
        // HACK - keep track to avoid this check or enforce sorting
        boolean trim = true;
        for (int i=0;i<lh.value_l.size()-1;i++) {
            if (lh.value_l.get(i) < lh.value_l.get(i+1)) {
                trim = false;
                break;
            }
        }
*/

//log.info("chooseFromDistribution " + type + " " + lh.size() + " " + limit);

        SeenIds seenIds = getSeen(conn, session);

        int tried = 0;
        StringBuilder seen = new StringBuilder();
        for (int i=0;i<tryLimit && lh.size()>0;i++) {
            tried++;

            boolean trim = false;
            int selLimit = lh.size();
            if (selLimit > baseLimit) {
                selLimit = baseLimit;
                trim = true;
            }
            int choice = selectRandomDistributedValue(lh.dbl_l, selLimit);
            String id2 = lh.id2_l.get(choice);

            lh.id2_l.remove(choice);
            lh.dbl_l.remove(choice);
            long value = lh.value_l.get(choice);
            lh.value_l.remove(choice);

            if (seenIds.contains(id2)) {

                seen.append(id2).append(" (")
                    .append(value).append(", ")
                    .append(choice).append(") ");

                continue;
            }
            if (orient != null  &&
                "v".equals(orient)  !=  data.verticals.contains(id2)) {
// STILL NEEDED?
                continue;
            }

            Picture p = PictureDao.getPictureById(conn, id2);

            if (p != null) {  // null if pic deleted after lists built
                log.info(type + ": got one;time=" +
                             (System.currentTimeMillis()-t1) +
                             " trim: " + trim +
                             " selLimit " + selLimit +
                             " tried: " + tried +
                             " browserID " + session.browserID + " id " + id2 +
                             " choice " + choice + " seen: " + seen.toString());
                return p;
            }

        }
        log.info(type + ": failed, tried " + tried + " time " +
                             (System.currentTimeMillis()-t1) +
                             " browserID " + session.browserID +
                             " seen " + seen);

        return null;
    }

    private ListHolder intersectSet(ListHolder lh, ListHolder bakLh,
                                  Set<String> prefSet)
            throws SQLException  {

        // HACK
        if (lh.value_l != null  &&  lh.value_l.size() == 0) {
            lh.value_l = null;
        }

        ListHolder lh2 = new ListHolder();

        for (int i=0;i<lh.size();i++) {

            String id = lh.id2_l.get(i);

            if (prefSet.contains(id)) {

                //log.info("intersectSet: " + seq);
                lh2.id2_l.add(id);
                if (lh.value_l != null) {
                    lh2.value_l.add(lh.value_l.get(i));
                }

            } else if (bakLh != null) {

                bakLh.id2_l.add(id);
                if (lh.value_l != null) {
                    bakLh.value_l.add(lh.value_l.get(i));
                }
            }
        }
        //log.info("intersectSet: " + id_l.size() + " out of " + id_l0.size());

        return lh2;
    }

    private int coderIndex(String coder) {
        if ("b".equals(coder)) {
            return 0;
        } else if ("m".equals(coder)) {
            return 1;
        } else {
            log.error("Unknown coder: " + coder);
            return 1;// HACK
        }
    }


    private void fallbackRandom(PictureResponse pr, Connection conn,
                                int viewNum, Session session)
            throws SQLException {
        fallbackRandom(pr, conn, viewNum, null, session);
    }

    private void fallbackRandom(PictureResponse pr, Connection conn,
                                int viewNum, String orient, Session session)
            throws SQLException {

        if (pr.p != null) {
            throw new RuntimeException("fallbackRandom - p not null");
        }
        log.info("browser " + session.browserID + ": Going for random");

        PictureResponse pr2 = chooseRandomImage(conn, viewNum, orient,
                                                    session, true);
        if (pr2 != null) {
            pr.p = pr2.p;
            pr.method += pr2.method;
        }
    }

    private ListHolder filterList(StringBuilder pref, ShowingPair last,
                                  int viewNum, String kwdCoder,
                                  ListHolder lh, ListHolder altLh,
                                  ListHolder bakLh, boolean copyIf)
            throws SQLException {

        if (bakLh.size() > 0) {
            throw new RuntimeException("BAK > 0");
        }
        if (lh.value_l != null  &&  lh.value_l.size() == 0) { //HACK
            lh.value_l = null;
        }
        if (altLh != null  &&  altLh.value_l != null  &&
               altLh.value_l.size() == 0) { //HACK
            altLh.value_l = null;
        }

//System.out.println("VN " + viewNum + " order " + last.orderInSession);

        String meth = null;

        ListHolder lh2 = null;

        if (copyIf) {
            // make modifiable copies
            lh2 = new ListHolder();
            lh2.id2_l = new ArrayList(lh.id2_l);
            if (lh.value_l != null) {
                lh2.value_l = new ArrayList(lh.value_l);
            }

            log.info("filterList copied for view-only " + lh2.size());

            return lh2;
        } else {

            log.info("filterList use orig " + lh.size());
            return lh;
        }
    }

    private PictureResponse pickFirstOk(Connection conn,
                                        int viewNum, String orient,
                                        Session session,
                                        ListHolder lh,
                                        String method)
            throws SQLException {

        return pickFirstOk(conn, viewNum, orient, session,
                                    lh, method, -1);
    }
    private PictureResponse pickFirstOk(Connection conn,
                                        int viewNum, String orient,
                                        Session session,
                                        ListHolder lh,
                                        String method, int ct)
            throws SQLException {

        PictureResponse pr = new PictureResponse();

        int okCt = 0;

        Set<String> picSet = data.getPicSet(viewNum, orient);

        SeenIds seenIds = getSeen(conn, session);

        int nope = 0;
        int seen = 0;

        for (int i=0;i<lh.size();i++) {

            String id2 = lh.id2_l.get(i);

            if (!picSet.contains(id2)) {
                nope++;
                continue;
            }
            if (seenIds.contains(id2)) {
                seen++;
                continue;
            }
            pr.p = PictureDao.getPictureById(conn, id2);
            if (pr.p != null) {
                if (lh.value_l != null  &&  lh.value_l.size() > 0) {
                    pr.value = lh.value_l.get(i);
                }
                if (ct == -1  ||  okCt == ct) {
                    pr.method = method;
                    pr.first = true;
                    return pr;
                }
                log.info("1stok skipping for ct");
                okCt++;
            }
        }
        log.warn("firstOk: nothing got;checked " + lh.size() +
                    ": view/orient: " + viewNum + orient +
                    ": picSet size " + picSet.size() + " but not in it: " + nope +
                    " already-seen " + seen +
                    " lh " + lh.toString());
new Exception("BAAA").printStackTrace();
        return null;
    }

    // single screen - unit==1 on UserProfile
    private PictureResponse chooseFromList(Connection conn,
                                     Session session, UserProfile up,
                                     int viewNum, String kwdCoder,
                                     String lastId, ListHolder lh,
                                     String type, boolean copyIf)
            throws SQLException {

        if (up.last == null) {
            throw new RuntimeException("up.last is null");
        }

        long t1 = System.currentTimeMillis();

        log.info("chooseFromList " + lastId + ": size " + lh.size());
        if (lh.size() == 0) {
            log.info("chooseFromList " + type + " FALLBACK: " + lastId +
                     " " + viewNum);
            PictureResponse pr = new PictureResponse();
            pr.method = "minus/0/";
            fallbackRandom(pr, conn, viewNum, session) ;
            if (pr.p == null) {
                return null;
            }
            return pr;
        }
        ListHolder bakLh = new ListHolder();
        StringBuilder pref = new StringBuilder();
        ListHolder lh2 = filterList(pref, up.last, viewNum, kwdCoder,
                                    lh, null, bakLh, copyIf);

        PictureResponse pr = new PictureResponse();

        long sysTime = 0;
        if (up.last.rateTime != null) {
            sysTime = up.last.rateTime.getTime() -
                      up.last.createTime.getTime();
        } else {
            log.warn("Defaulting sysTime to userTime");
            sysTime = up.last.userTime;
        }
        double diff = Math.abs(sysTime - up.last.userTime);
        if (diff > 0.1 * sysTime) {
            log.warn("DIFF IN TIMES: sys " + sysTime +
                     " user " + up.last.userTime);
        }
        if (up.last.userTime > 1000  &&  sysTime > 1100) {

            // set atom action

            pr.atoms = AtomSpec.A_N1N6;
            if (up.longerClick || up.longestClick) {
                pr.factor = TAP_MAX * 0.7;
            } else if (up.shortClick || up.shorterClick) {
                pr.factor = TAP_MAX * 0.3;
            } else {
                pr.factor = TAP_MAX * 0.5;
            }
        }

        if (up.longerClick || up.longestClick) {
            logIt(lh2, false);
            // relaxing orient -?
            PictureResponse pr2 = pickFirstOk(conn, viewNum, null, session,
                                 lh2, type.toLowerCase() + (pref) + "/lng+ck/first");
            if (pr2 != null) {
                pr.lh = lh2;
                pr.p = pr2.p;
                pr.value = pr2.value;
                pr.method = pr2.method;
                long t2 = System.currentTimeMillis();
                log.info(type.toLowerCase() + " first sel " + (t2 - t1));
                return pr;
            }
            if (bakLh.size() > 0) {
                logIt(bakLh, false);
                pr2 = pickFirstOk(conn, viewNum, null, session,
                                        bakLh, type.toLowerCase() + "/bk/lng+ck/first");
                if (pr2 != null) {
                    pr.lh = bakLh;
                    pr.p = pr2.p;
                    pr.value = pr2.value;
                    pr.method = pr2.method;
                    long t2 = System.currentTimeMillis();
                    log.info(type.toLowerCase() + " bak first sel " +
                                                   (t2 - t1));
                    return pr;
                }
            }
        }

        pr.method = type.toLowerCase() + pref;
        pr.p = chooseFromDistribution(conn, type, session, up, lh2, -1);
        if (pr.p != null) {
            pr.lh = lh2;
            return pr;
        }
        if (bakLh.size() > 0) {
            pr.p = chooseFromDistribution(conn, type, session, up, bakLh, -1);
            if (pr.p != null) {
                pr.lh = bakLh;
                pr.method += "/bk";
                return pr;
            }
        }

        fallbackRandom(pr, conn, viewNum, session) ;
        if (pr.p == null) {
            return null;
        }
        return pr;
        //throw new SQLException("chooseFromList [" + type + "]: OUT OF OPTIONS");
    }

    public PictureResponse chooseKwdImage(Connection conn, Session session,
                                   String kwdCoder, String kwd)
            throws SQLException {

        // both orientations
        log.info("chooseKwdImage: " + kwd);

        browserLastList.remove(session.browserID);// clear Plus history

        SeenIds seenIds = getSeen(conn, session);

        List<String> l = KeywordsDao.getIdsCoderKwd(conn, kwdCoder, kwd);
        while (l.size() > 0) {
            int i = rand.nextInt(l.size());
            String id = l.get(i);
            if (seenIds.contains(id)) {
                l.remove(i);
                continue;
            }
            PictureResponse pr = new PictureResponse();
            pr.p = PictureDao.getPictureById(conn, id);
            pr.method = "kwd/random";
            return pr;
        }
        log.info("Seen all for kwd [" + kwd + "]");
        return null;
    }

    private PictureResponse chooseBestComboPairtop(Connection conn,
                                    Session session, UserProfile up,
                                    int viewNum, String orient,
                                    String lastId, String columns)
            throws SQLException {

        long t1 = System.currentTimeMillis();

        String meth = columns.replaceAll(" ", "");
        String cols[] = columns.split(" ");
        if (cols.length != 2) {
            throw new RuntimeException("FIXME: " + columns);
        }

        int nToGet = 200;

        Set<String> picSet = data.getPicSet(viewNum, orient);

        ListHolder lh = PairTopDao.getPairtopSymm(conn, "col",
                                           orient, lastId, cols[0],
                                           false, // no inverting
                                           "ASC", nToGet, picSet);

        log.info("==== got 1st: " + lh.size());

        Set<String> target = new HashSet<>();
        target.addAll(lh.id2_l);
        // random sanity check
        if (target.size() != lh.size()) {
            throw new RuntimeException("Dupes in list " + lastId);
        }

        ListHolder lh2 = PairTopDao.getPairtopSymm(conn, "col",
                                            orient, lastId, cols[1],
                                            false, // no inverting
                                            "ASC", 800, picSet);

        log.info("==== got 2nd: " + lh2.size());

        ListHolder ok_lh = new ListHolder();
        ListHolder bak_lh = new ListHolder();

        SeenIds seenIds = getSeen(conn, session);

        int valid = 0;
        for (int i=0;i<lh2.size();i++) {
            String id2 = lh2.id2_l.get(i);
            if (seenIds.contains(id2)) {
                continue;
            }
            if (target.contains(id2)) {
                ok_lh.id2_l.add(id2);
                ok_lh.value_l.add(lh2.value_l.get(i));
            } else {
                bak_lh.id2_l.add(id2);
                bak_lh.value_l.add(lh2.value_l.get(i));
            }
        }
        log.info("==== ok/bak sizes: " + ok_lh.size() + "/" + bak_lh.size());

        PictureResponse pr = new PictureResponse();
        pr.method = meth;
        if (ok_lh.size() > 0) {
            ok_lh.value_l = invertDistribution(ok_lh.value_l);
            pr.p = chooseFromDistribution(conn, "COMBO", session, up,
                                                ok_lh, -1, orient);
            if (pr.p != null) {
                pr.lh = ok_lh;
                return pr;
            }
        }
        if (bak_lh.size() > 0) {
            bak_lh.value_l = invertDistribution(bak_lh.value_l);
            pr.p = chooseFromDistribution(conn, "COMBO/bak", session, up,
                                                bak_lh, -1, orient);
            if (pr.p != null) {
                pr.method += "/bk";
                pr.lh = bak_lh;
                return pr;
            }
        }
        log.info("chooseBestComboPairtop empty-handed");
        return null;
    }


    private PictureResponse handleUserV(Connection conn,
                                       int viewNum, String orient,
                                       Session session, UserProfile up,
                                       PictureResponse pr,
                                       StringBuilder sum, StringBuilder tsum,
                                       List<String> id_l, List<String> bak_id_l,
                                       String tblIntType, boolean invert,
                                       String tblLogTag,
                                       String method, String bakMethod,
                                       String tsel)
           throws SQLException {

        if (id_l.size() == 0) {
            return null;
        }

        // lists are consumed

        long t4 = System.currentTimeMillis();// echoes caller var

        ListHolder lh = PictureDao.getIntList(conn, tblIntType, id_l, invert);
        pr.p = chooseFromDistribution(conn, tblLogTag, session, up,
                                            lh, -1, orient);
        pr.lh = lh;
        pr.method = method; // hope for the best
        tsum.append(tsel).append(System.currentTimeMillis() - t4);

        if (pr.p != null  &&  tblLogTag.startsWith("KWD")) {
            //browserLast.put(browserID, lh.id2_l);
        } else {
            //browserLast.remove(browserID);// final purge

            if (bak_id_l != null  &&  bak_id_l.size() > 0) {
                t4 = System.currentTimeMillis();

                lh = PictureDao.getIntList(conn, tblIntType, bak_id_l, invert);

                pr.p = chooseFromDistribution(conn, tblLogTag, session, up,
                                                    lh, -1, orient);
                pr.lh = lh;
                pr.method = bakMethod;
                tsum.append(" bak ").append(System.currentTimeMillis() - t4);
            }

        }
        sum.append(tsum);
        log.info(sum.toString());
        if (pr.p == null) {
            return null;
        }
        return pr;
    }

    // modifies the id lists
    // if 1st call returns null, id_l contains only unseen pics
    //    and bak_id_l emptied if present.
    //    I'm using bak_id_l == null on subsequent calls with a
    //    validated id_l list as a flag that db doesn't need
    //    to be checked on id_l traversal
    private void handleUser2(Connection conn,
                                        int viewNum, String orient,
                                        Session session, UserProfile up,
                                        PictureResponse pr,
                                        StringBuilder sum, StringBuilder tsum,
                                        int pVal, String lastId,
                                        List<String> id_l, List<String> bak_id_l,
                                        String tblIntType,
                                        String tblLogTag,
                                        String method, String bakMethod,
                                        String tsel)
           throws SQLException {

        log.info("handleUser2 len " + (id_l == null ? 0 : id_l.size()));

        if (pr.p != null) {
            log.error("pr.p is not null");
        }

        // Lists are consumed

        if (id_l == null  ||  id_l.size() == 0) {
            return;// ignore bak
        }
        long t4 = System.currentTimeMillis();// echoes caller var

        ListHolder lh = PictureDao.getIntList(conn, tblIntType, id_l, false);

        // find the closest pic that hasn't been seen, keeping our
        //   wits about us in case we want to look again along another
        //   dimension

        int best = -1;
        String bestId2 = lh.id2_l.get(0);
        long bestVal = lh.value_l.get(0);
        long bestDiff = Math.abs(bestVal - pVal);
        long min = 99999999;
        long max = 0;
        int total = 0;
        //ListHolder lh3 = new ListHolder();// unknown validity

        Map<Long, List<String>> diffSort = new TreeMap<>();
        Map<Long, List<String>> fallback = new TreeMap<>();

        Set<String> view = data.getPicSet(viewNum, orient);

        SeenIds seenIds = getSeen(conn, session);

        for (int i=0;i<lh.size();i++) {

            String id2 = lh.id2_l.get(i);

            if (seenIds.contains(id2)) {
                continue;
            }
            if (!view.contains(id2)) {
                continue;
            }

            long val = lh.value_l.get(i);

            long diff = Math.abs(val - pVal);
            total++;
            if (diff < min) min = diff;
            if (diff > max) max = diff;

            List<String> ids = diffSort.get(diff);
            if (ids == null) {
                ids = new ArrayList<>();
                diffSort.put(diff, ids);
            }
            ids.add(id2);

            if (best == -1) {

                best = i;// it could be Ted Cruz
                bestId2 = id2;
                bestVal = val;
                bestDiff = diff;
                //lh2.id2_l.add(id2);
                //lh2.value_l.add(lh.value_l.get(i));
                continue;

            } else if (diff < bestDiff) {
                best = i;
                bestId2 = id2;
                bestVal = val;
                bestDiff = diff;
                //lh2.id2_l.add(id2);
                //lh2.value_l.add(lh.value_l.get(i));
                continue;
            }

            // might sort through some more

            //lh3.id2_l.add(id2);
            //lh3.value_l.add(lh.value_l.get(i));
        }

        ListHolder lh2 = new ListHolder();
        int needed = total / 4;
        log.info("max/min " + max + "/" + min + " needed " + needed);
        for (Map.Entry pair : diffSort.entrySet()) { // TreeMap goes in ascending order
            long diff = (long) pair.getKey();
            List<String> ids = (List<String>) pair.getValue();
            //log.info("diff " + diff + " ct " + ids.size());

            if (needed > 0) {
                for (String id : ids) {
                    lh2.id2_l.add(id);
                    lh2.value_l.add(diff);
                }
                needed -= ids.size();
            } else {
                fallback.put(diff, ids);
            }
        }
        tsum.append(tsel).append(System.currentTimeMillis() - t4);
        log.info("selected " + lh2.size() + " fallback " + fallback.size());
        if (lh2.size() > 0) {

            lh2.value_l = invertDistribution(lh2.value_l);
            pr.p = chooseFromDistribution(conn, "pck/m", session, up, lh2, -1);
            if (pr.p != null) {
                // use it
                logIt(lh2);
                pr.lh = lh2;
                pr.method = method + "_m";
                return;
            }
        }
        log.info("Failed/first bestDiff, min, max " + bestDiff + " " +
                  min + " " + max);

        // see if the grass is greener in bakland

        if (bak_id_l != null  &&  bak_id_l.size() > 0) {
            t4 = System.currentTimeMillis();

            lh = PictureDao.getIntList(conn, tblIntType, bak_id_l, false);

            diffSort.clear();
            total = 0;

            for (int i=0;i<lh.size();i++) {

                String id2 = lh.id2_l.get(i);

                if (seenIds.contains(id2)) {
                    continue;
                }
                if (!view.contains(id2)) {
                    continue;
                }
                if (orient != null  &&
                    "v".equals(orient) != data.verticals.contains(id2)) {
                    continue;
                }

                long val = lh.value_l.get(i);

                long diff = Math.abs(val - pVal);
                if (val < min) min = val;
                if (val > max) max = val;

                List<String> ids = diffSort.get(diff);
                if (ids == null) {
                    ids = new ArrayList<>();
                    diffSort.put(diff, ids);
                }
                ids.add(id2);
                total++;

                if (best == -1) {

                    best = i;// it could be Ted Cruz
                    bestId2 = id2;
                    bestVal = val;
                    bestDiff = diff;
                    //lh2.id2_l.add(id2);
                    //lh2.value_l.add(lh.value_l.get(i));
                    continue;

                } else if (diff < bestDiff) {
                    best = i;
                    bestId2 = id2;
                    bestVal = val;
                    bestDiff = diff;
                    //lh2.id2_l.add(id2);
                    //lh2.value_l.add(lh.value_l.get(i));
                    continue;
                }
            }
            lh2.id2_l.clear();
            lh2.value_l.clear();
            needed = total / 4;

            for (Map.Entry pair : diffSort.entrySet()) { // ascending order
                long diff = (int) pair.getKey();
                List<String> ids = (List<String>) pair.getValue();
                log.info("bakdiff " + diff + " ct " + ids.size());

                if (needed > 0) {
                    for (String id : ids) {
                        lh2.id2_l.add(id);
                        lh2.value_l.add(diff);
                    }
                    needed -= ids.size();
                } else {
                     List<String> fids = fallback.get(diff);
                     if (fids == null) {
                         fallback.put(diff, ids);
                     } else {
                         fids.addAll(ids);
                     }
                }
            }
            lh2.value_l = invertDistribution(lh2.value_l);
            pr.p = chooseFromDistribution(conn, "pck/m", session, up, lh2, -1);
            if (pr.p != null) {
                log.info("Got ok bak BestDiff " + bestDiff);
                // use it
                logIt(lh2);
                pr.lh = lh2;
                pr.method = bakMethod + "_m";
                return;
            }
            tsum.append(tsel + "/bk").append(System.currentTimeMillis() - t4);
        }
        log.info("Failed/final bestDiff, min, max " + bestDiff + " " +
                  min + " " + max);

        // wipe and repop

        id_l.clear();
        for (Map.Entry pair : fallback.entrySet()) {
            id_l.addAll((List<String>) pair.getValue());
        }
        if (bak_id_l != null) {
            bak_id_l.clear();
        }
    }



    private ListHolder reverseList(ListHolder lh, double skew,
                                   boolean stratify) {

        if (lh == null) {
            return null;
        }

        ListHolder ret = new ListHolder();

        int mid = lh.size() / 2;

        log.info("reverseList MID " + mid + " stratify " + stratify);

        if (mid < 10) {
            for (int i=lh.size()-1;i>-1;i--) {
                ret.id2_l.add(lh.id2_l.get(i));
                if (lh.value_l != null) {
                    ret.value_l.add(lh.value_l.get(i));
                }
            }
            if (lh.value_l != null) {
                ret.value_l = invertDistribution(ret.value_l);
            }
            return ret;
        }
        int start;
        int n;
        if (stratify) {
            start = (int)((double)lh.size() * skew);
            if (start == lh.size()) start = 0;
            n = 100;
            if (n > mid) n = mid;
        } else {
            start = lh.size() - 1;
            n = lh.size();
        }
        if (lh.value_l != null  && lh.value_l.size() > 0) {
            for (int i=start;i>start-n && i>-1;i--) {
                ret.id2_l.add(lh.id2_l.get(i));
                ret.value_l.add(lh.value_l.get(i));
            }
            for (int i=lh.size()-1;i>start;i--) {
                ret.id2_l.add(lh.id2_l.get(i));
                ret.value_l.add(lh.value_l.get(i));
            }
            for (int i=start-n;i>-1;i--) {
                ret.id2_l.add(lh.id2_l.get(i));
                ret.value_l.add(lh.value_l.get(i));
            }
            ret.value_l = invertDistribution(ret.value_l);
        } else {
            for (int i=start;i>start-n && i>-1;i--) {
                ret.id2_l.add(lh.id2_l.get(i));
            }
            for (int i=lh.size()-1;i>start;i--) {
                ret.id2_l.add(lh.id2_l.get(i));
            }
            for (int i=start-n;i>-1;i--) {
                ret.id2_l.add(lh.id2_l.get(i));
            }
        }
//log.info("SKEW " + skew + " start " + start + " n " + n + " size " + lh.size());
        return ret;
    }

    private ListHolder stratifyList(ListHolder lh, double skew) {

        if (lh == null) {
            return null;
        }

        int mid = lh.size() / 2;

        log.info("stratifyList MID " + mid + " skew " + skew);

        if (mid < 10) {
            return lh;
        }
        ListHolder ret = new ListHolder();

        if (skew > 0.0) {
            while (skew > 1.0) {
                skew /= 2;
            }
        }
        int start = (int) (skew * lh.size());
        int n = 100;
        if (n > mid) n = mid;
        int end = start + n;
        if (end > lh.size()) {
            end = lh.size();
        }
        for (int i=start;i<end;i++) {
            ret.id2_l.add(lh.id2_l.get(i));
            if (lh.value_l != null) {
                ret.value_l.add(lh.value_l.get(i));
            }
        }
        for (int i=0;i<start;i++) {
            ret.id2_l.add(lh.id2_l.get(i));
            if (lh.value_l != null) {
                ret.value_l.add(lh.value_l.get(i));
            }
        }
        for (int i=end;i<lh.size();i++) {
            ret.id2_l.add(lh.id2_l.get(i));
            if (lh.value_l != null) {
                ret.value_l.add(lh.value_l.get(i));
            }
        }
        return ret;
    }

    private PictureResponse tryId(Connection conn,
                                  String orient,
                                  Session session,
                                  Set<String> picSet,
                                  SeenIds seenIds,
                                  String id)
            throws SQLException {

        if (!picSet.contains(id)) {
            return null;
        }
        if (seenIds.contains(id)) {
            return null;
        }
        PictureResponse pr = new PictureResponse();
        pr.p = PictureDao.getPictureById(conn, id);
        if (pr.p == null) {
            return null;
        }
        return pr;
    }

    private List<Screen> linearScreenSearch(Connection conn,
                                            final int viewNum, String orient,
                                            Session session,
                                            int n)
            throws SQLException {

        int matched_orient = 0;

        int skipped_seen = 0;// for LOCAL

        SeenIds seenIds = getSeen(conn, session);

        List<String> picList = data.getPicList(viewNum, orient);

        if (picList == null  ||  picList.size() == 0) {
            log.error("View " + viewNum + "/" + orient +": no pics");
            return null;
        }

        int center = rand.nextInt(picList.size());

        log.info("Trying 'linear' search.. orient: " + orient + " size " +
                     picList.size() + " center " + center);

        for (int i=center;i<picList.size();i++) {

            String id1 = picList.get(i);

            matched_orient++;

            if (seenIds.contains(id1)) {
                continue;
            }

            Set<String> seenPairsId1 = null;
            Set<String> seenPairsId2 = null;
            if (session.curator != null) {
                seenPairsId1 = UniquePairDao.getPicsSeen(conn, id1, "id1");
                seenPairsId2 = UniquePairDao.getPicsSeen(conn, id1, "id2");
            }

            // work away

            for (int j=picList.size()-1;j>i;j--) {

                String id2 = picList.get(j);

                matched_orient++;

                if (seenIds.contains(id2)) {
                    continue;
                }

                // choose screens: will id2 fit in either slot

                String scr1 = id1;
                String scr2 = id2;

                if (session.curator != null) {
                    if (seenPairsId1.contains(id2)) {
                        if (seenPairsId2.contains(id2)) {
                            // won't fit in either slot
                            skipped_seen++;
                            continue;
                        }
                        // id2 fits in 0 slot
                        scr1 = id2;
                        scr2 = id1;
                    }
                }

                PictureResponse pr1 = new PictureResponse();
                pr1.p = PictureDao.getPictureById(conn, scr1);
                if (pr1.p == null) {
                    log.error("No version for seq " + scr1);
                    continue;
                }
                pr1.method = "->seq0";

                PictureResponse pr2 = new PictureResponse();
                pr2.p = PictureDao.getPictureById(conn, scr2);
                if (pr2.p == null) {
                    log.error("No version for seq " + scr2);
                    continue;
                }
                pr2.method = "->seq1";

                List<Screen> ret = new ArrayList<>();

                ret.add(new Screen(session.browserID, 1, "v",
                                                        scr1, pr1));
                ret.add(new Screen(session.browserID, 2, "v",
                                                        scr2, pr2));

                log.info("linearScreenSearch skipped_seen " + skipped_seen +
                          " " + ret.get(0).id_s + " " + ret.get(1).id_s);

                return ret;
            }
        }

        log.info("'linear' search pass 2 " +
                 " matched_orient " + matched_orient +
                 " skipped_seen " + skipped_seen);

        for (int i=0;i<center;i++) {

            String id1 = picList.get(i);

            matched_orient++;

            if (seenIds.contains(id1)) {
                continue;
            }

            Set<String> seenPairsId1 = null;
            Set<String> seenPairsId2 = null;
            if (session.curator != null) {
                seenPairsId1 = UniquePairDao.getPicsSeen(conn, id1, "id1");
                seenPairsId2 = UniquePairDao.getPicsSeen(conn, id1, "id2");
            }

            for (int j=center-1;j>i;j--) {

                String id2 = picList.get(j);

                matched_orient++;

                if (seenIds.contains(id2)) {
                    continue;
                }

                // choose screens: will id2 fit in either slot

                String scr1 = id1;
                String scr2 = id2;

                if (session.curator != null) {
                    if (seenPairsId1.contains(id2)) {
                        if (seenPairsId2.contains(id2)) {
                            // won't fit in either slot
                            skipped_seen++;
                            continue;
                        }
                        // id2 fits in 1 slot
                        scr1 = id2;
                        scr2 = id1;
                    }
                }

                PictureResponse pr1 = new PictureResponse();
                pr1.p = PictureDao.getPictureById(conn, scr1);
                if (pr1.p == null) {
                    log.error("No version for seq " + scr1);
                    continue;
                }
                pr1.method = "->seq0";

                PictureResponse pr2 = new PictureResponse();
                pr2.p = PictureDao.getPictureById(conn, scr2);
                if (pr2.p == null) {
                    log.error("No version for seq " + scr2);
                    continue;
                }
                pr2.method = "->seq1";

                List<Screen> ret = new ArrayList<>();

                ret.add(new Screen(session.browserID, 1, "v",
                                                        scr1, pr1));
                ret.add(new Screen(session.browserID, 2, "v",
                                                        scr2, pr2));

                log.info("linearScreenSearch skipped_seen " + skipped_seen +
                          " " + ret.get(0).id_s + " " + ret.get(1).id_s);

                return ret;
            }
        }

        log.info("DONE/'fail' linearScreenSearch: skipped " + skipped_seen +
                  " matched_orient " + matched_orient);
        return new ArrayList<>();
        //return null;
    }

    private List<Screen> handleRandomScreens(Connection conn,
                                                int viewNum, String orient,
                                                Session session, UserProfile up)
            throws SQLException {

        return handleRandomScreens(conn, viewNum, orient, session, up, false);
    }

    private List<Screen> get2ndPrPosScreen(Connection conn,
                                            Session session, UserProfile up,
                                            int viewNum, String orient,
                                            Set<String> picSet,
                                            String posColumn,
                                            PictureResponse initPr)
            throws SQLException {

        String id1 = initPr.p.id;

        log.info("get2ndPrPosScreen: " + id1 + " method " + initPr.method);

        if (picSet == null) {
            picSet = data.getPicSet(viewNum, orient);
        }

        Set<String> seenPairs1 = null;
        //Set<String> seenPairs2 = null;

        if (session.curator != null) {
            seenPairs1 = UniquePairDao.getPicsSeen(conn, id1, "id1");
            log.info("got seen0 TODO - cache?");
            // will need this if we add a swap rule:
            //seenPairs2 = UniquePairDao.getPicsSeen(conn, id1, "id2");
        }

        int limit = 200;

        ListHolder lh2 = null;

        if (PairDao.hasD0(orient)) {

            lh2 = PairDao.getPosPairs(conn, orient, session.curator, posColumn, id1,
                                                  false, // get left
                                                  null, // hasKwd
                                                  true, // DESC == best
                                                  limit, picSet);
        }
        if (lh2 == null) {

            String func = (up.restlessMouse || up.skew > 0.68) ? "cosine" : "cartes";
            int[] dims = {256, 1728, 1984 };
            int dim = dims[ (int)(up.skew * (double)dims.length)];
            func = func + ".1." + dim;

            log.info(": falling back on vectors for " + id1);
            lh2 = PictureDao.matchVector(conn, orient,
                                    2, id1,
                                    func,
                                    data.getViewArchives(viewNum),
                                    limit, picSet);
        }

        if (lh2.size() == 0) {
            log.info("zilch on pos/vec pairs");
            return null;
        }

        // got lh2.. some options

        if (up.last == null  ||
            up.last.dotHistory == null  ||
            up.last.dotHistory.angleHist == null) {

            // first idea, createDblL uses dots if avail

            long val = lh2.size() + 2;
            for (int i=0;i<lh2.size();i++) {
                lh2.value_l.set(i, (val*val));
                val--;
            }
        }

        // up.last => use dot-derived histogram
        createDblL(up.last, lh2, null);

        PictureResponse pr2 = new PictureResponse();

        int try2 = 0;
        int try2_net = 0;
        int skipped_seen = 0;
        while (lh2.size() > 0) {
            int l2 = lh2.size();
            pr2.p = chooseFromDistribution(conn, posColumn + "/" + try2,
                                                     session, up, lh2, -2);
            l2 -= lh2.size();
            try2_net += l2;

            if (pr2.p == null) {
                break;
            }

            try2++;

            String id2 =  pr2.p.id;
            if (seenPairs1 != null  &&  seenPairs1.contains(id2)) {
                log.info("skipping curator-seenpair..");
                skipped_seen++;
                continue;
            }
            if (id2.equals(id1)) {
                continue;
            }

            pr2.method = "+/" + posColumn + "/" + try2;

            List<Screen> scl = new ArrayList<>();

            scl.add(new Screen(session.browserID, 1, "v", id1,
                                                      initPr));
            scl.add(new Screen(session.browserID, 2, "v", id2,
                                                      pr2));
            return scl;
        }

        log.info("get2ndPrPosScreen - zilch");

        return null;
    }

    private List<Screen> get2ndPairtopScreen(Connection conn,
                                            Session session, UserProfile up,
                                            int viewNum, String orient,
                                            long t1, String tag,
                                            PictureResponse initPr,
                                            Set<String> picSet)
            throws SQLException {

        final int depth = 400;

        if (tag == null) {
            List<String> tagL = data.getPairtopNNTags("v".equals(orient));
            tag = tagL.get(rand.nextInt(tagL.size()));
            log.info("get2ndPairtopScreen: random tag: " + tag);
        } else {
            log.info("get2ndPairtopScreen: tag: " + tag);
        }

        if (picSet == null) {
            picSet = data.getPicSet(viewNum, orient);
        }

        int try1 = 0;
        int try1_net = 0;
        int try1_seen = 0;
        int skipped_seen = 0;// for LOCAL

        PictureResponse pr2 = new PictureResponse();

        Set<String> seenPairs1 = null;
        //Set<String> seenPairs2 = null;

        String id1 = initPr.p.id;
        if (session.curator != null) {
            seenPairs1 = UniquePairDao.getPicsSeen(conn, id1, "id1");
            // will need this if we add a swap rule:
            //seenPairs2 = UniquePairDao.getPicsSeen(conn, id1, "id2");
        }

        String matchRightTo = id1;
        //initPr.method += "/P/";
        pr2.method = "P/";

        ListHolder lh2 = PairTopDao.getPairtopAsym(conn,
                                             orient, matchRightTo,
                                             true, // l[_prev]->r
                                             tag,
                                             false, "DESC", depth, picSet);

        int try2 = 0;
        int try2_net = 0;

        while (lh2.size() > 0) {
            int l2 = lh2.size();
            pr2.p = chooseFromDistribution(conn, "PH/"+tag, session, up,
                                                      lh2, -1);
            l2 -= lh2.size();
            try2_net += l2;

            if (pr2.p == null) {
                log.info("get2ndPairtopScreen: exhausted list " +
                                            "- quicker give-up");
                break;
            }
            try2++;
            String id2 = pr2.p.id;
            if (seenPairs1 != null  &&  seenPairs1.contains(id2)) {
                log.info("skipping curator-seenpair..");
                skipped_seen++;
                continue;
            }
            if (id2.equals(id1)) {
                continue;
            }

            pr2.method += tag + "/" + try2 + "/" + try2_net;

            List<Screen> scl = new ArrayList<>();

            scl.add(new Screen(session.browserID, 1, "v", id1,
                                                      initPr));
            scl.add(new Screen(session.browserID, 2, "v", id2,
                                                      pr2));

            log.info("get2ndPairtopScreen: t=" +
                            (System.currentTimeMillis() - t1));
            return scl;
        }

        return null;
    }

    private List<Screen> get2ndPairtopScreen(Connection conn,
                                            Session session, UserProfile up,
                                            int viewNum, String orient,
                                            long t1, List<String> ids,
                                            String tag, ListHolder lh,
                                            Set<String> picSet)
            throws SQLException {

        final int depth = 400;

        if (tag == null) {
            List<String> tagL = data.getPairtopNNTags("v".equals(orient));
            tag = tagL.get(rand.nextInt(tagL.size()));
            log.info("get2ndPairtopScreen: random tag: " + tag);
        } else {
            log.info("get2ndPairtopScreen: tag: " + tag);
        }

        if (picSet == null) {
            picSet = data.getPicSet(viewNum, orient);
        }

        int try1 = 0;
        int try1_net = 0;
        int try1_seen = 0;
        int skipped_seen = 0;// for LOCAL

        PictureResponse initPr = new PictureResponse();
        PictureResponse pr2 = new PictureResponse();

        Set<String> seenPairs1 = null;
        //Set<String> seenPairs2 = null;

        while (lh.size() > 0) {
            int l1 = lh.size();
            initPr.p = chooseFromDistribution(conn, "PHop/"+tag, session, up,
                                                     lh, -1);
            l1 -= lh.size();
            try1_net += l1;

            if (initPr.p == null) {
                // warn just in case
                log.warn("get2ndPairtopScreen: " +
                                "FALLING BACK ON RANDOM FOR FIRST");
                initPr = chooseRandomImage(conn, viewNum, orient,
                                                    session, true);
                if (initPr == null) {
                    log.info("DONE w/ pics on get2ndPairtopScreen?");
                    return new ArrayList<>();
                    //return null;
                }
            }
            try1++;
            int try2 = 0;
            int try2_net = 0;
            String id1 = initPr.p.id;
            if (session.curator != null) {
                seenPairs1 = UniquePairDao.getPicsSeen(conn, id1, "id1");
                // will need this if we add a swap rule:
                //seenPairs2 = UniquePairDao.getPicsSeen(conn, id1, "id2");
            }

            String matchRightTo = id1;
            initPr.method = "P/";
            pr2.method = "P/";
            if (up.last != null  &&  up.last.userTime < 6000  &&
                up.skew > 0.68) {  // MAGIC, 1.0 > skew > .333

                log.info("criss-cross");
                matchRightTo = ids.get(0);
                initPr.method = "Px/";
                pr2.method = "Px/";
            }
            ListHolder lh2 = PairTopDao.getPairtopAsym(conn,
                                             orient, matchRightTo,
                                             true, // l[_prev]->r
                                             tag,
                                             false, "DESC", depth, picSet);

            while (lh2.size() > 0) {
                int l2 = lh2.size();
                pr2.p = chooseFromDistribution(conn, "PH/"+tag, session, up,
                                                      lh2, -1);
                l2 -= lh2.size();
                try2_net += l2;

                if (pr2.p == null) {
                    log.info("get2ndPairtopScreen: exhausted list " +
                                            "- quicker give-up");
                    break;
                }
                try2++;
                String id2 = pr2.p.id;
                if (seenPairs1 != null  &&  seenPairs1.contains(id2)) {
                    log.info("skipping curator-seenpair..");
                    skipped_seen++;
                    continue;
                }
                if (id2.equals(id1)) {
                    continue;
                }

                initPr.method += tag + "/" + try1 + "/" + try1_net;
                pr2.method += tag + "/" + try2 + "/" + try2_net;

                List<Screen> scl = new ArrayList<>();

                scl.add(new Screen(session.browserID, 1, "v", id1,
                                                      initPr));
                scl.add(new Screen(session.browserID, 2, "v", id2,
                                                      pr2));

                log.info("get2ndPairtopScreen: t=" +
                            (System.currentTimeMillis() - t1));
                return scl;
            }
        }

        return null;
    }

    private List<Screen> handleRandomScreens(Connection conn,
                                                int viewNum, String orient,
                                                Session session, UserProfile up,
                                                boolean nn2nd)
            throws SQLException {

        log.info("handleRandomScreens orient " + orient);

        long t1 = System.currentTimeMillis();

        int skipped_decided = 0;

        for (int i=0;i<1000;i++) {
            PictureResponse pr1 = chooseRandomImage(conn, viewNum, orient,
                                                    session, false);
            if (pr1 == null) {
                break;
            }

            if (nn2nd) {
                List<Screen> ret = get2ndPairtopScreen(conn,
                                                    session, up,
                                                    viewNum, orient,
                                                    t1, null, /*tag*/
                                                    pr1, null/*picSet*/);
                if (ret != null) {
                    return ret;
                }
                log.info("handleRandomScreens/2nd=nn: failed");
                // drop thru
            }

            String id1 = pr1.p.id;

            Set<String> seenPairsId1 = null;
            Set<String> seenPairsId2 = null;
            if (session.curator != null) {
                seenPairsId1 = UniquePairDao.getPicsSeen(conn, id1, "id1");
                seenPairsId2 = UniquePairDao.getPicsSeen(conn, id1, "id2");
            }

            for (int j=0;j<20;j++) {
                PictureResponse pr2 = chooseRandomImage(conn, viewNum, orient,
                                                    session, false);
                if (pr2 == null) {
                    break;
                }
                String id2 = pr2.p.id;
                if (id1.equals(id2)) {
                    log.warn("collision on random");
                    continue;
                }

                // choose screens: will id1 fit in either slot

                String scr1 = id1;
                String scr2 = id2;
                PictureResponse p1 = pr1;
                PictureResponse p2 = pr2;

                if (session.curator != null) {

                    // maybe flip or skip

                    // biasing toward pairs in eval states in approved_pairs
                    // in order to push to 1's faster

                    ApprovedPair ap = ApprovalDao.getApprovedPair(conn,
                                                             id1, id2);
                    if (ap == null  ||  ap.status == 1  ||  ap.status == 2) {

                        // try flip

                        ap = ApprovalDao.getApprovedPair(conn, id2, id1);

                        if (ap == null) {
                            // no way for approved_pair,
                            // screen out seen pairs
                            if (seenPairsId1.contains(id2)) {
                                if (seenPairsId2.contains(id2)) {
                                    // won't fit in either slot
                                    skipped_decided++;
                                    continue;
                                }
                                // id2 fits in 0 slot - flip
                                scr1 = id2;
                                scr2 = id1;
                                p1 = pr2;
                                p2 = pr1;
                            }
                        } else if (ap.status != 1  &&  ap.status != 2) {

                            // flip

                            scr1 = id2;
                            scr2 = id1;
                            p1 = pr2;
                            p2 = pr1;
                        }
                    } else if (ap.status == 1  ||  ap.status == 2) {
                        skipped_decided++;
                        continue;
                    }
                    if (ap != null) {
                        log.info("CURATOR random ap status " + ap.status);
                    }
                    if (skipped_decided > 0) {
                        log.info("CURATOR random skipped " + skipped_decided);
                    }

                }// end curator machinations

                List<Screen> ret = new ArrayList<>();
                ret.add(new Screen(session.browserID, 1, "v",
                                                        scr1, p1));
                ret.add(new Screen(session.browserID, 2, "v",
                                                        scr2, p2));
                return ret;
            }
        }
        log.warn("Giving up on random.");
        return linearScreenSearch(conn, viewNum, orient, session, 2);
    }


    private PictureResponse tryPicsAtAngleAB(Connection conn,
                                             int viewNum, String orient,
                                             Session session,
                                             UserProfile up,
                                             int angle, Picture prev,
                                             Set<String> seenPairs)
            throws SQLException {

        ListHolder lh = PictureDao.getPicsAtAngle(conn, angle);
        log.info("Angle " + angle + " size " + lh.size());
        if (lh == null  ||  lh.size() == 0) {
            log.info("No pics at angle_ab=" + angle);
            return null;
        }

        Set<String> picSet = data.getPicSet(viewNum, orient);

        // find closest radius to prev

        int closest = lh.size() / 2;
        long diff = closest;
        for (int i=0;i<lh.value_l.size();i++) {
            long t = Math.abs(lh.value_l.get(i) - prev.dCtrAB);
            if (t < diff) {
                closest = i;
                diff = t;
            }
        }
        int target;
        boolean out = up.longerThanSecondLast;
        if (out) {
            target = closest + (int) (up.skew * (lh.size() - closest));
        } else {
            target = (int)(closest * up.skew);
        }
        SeenIds seenIds = getSeen(conn, session);
        for (int i=0;i<lh.size();i++) {
            boolean seen = false;
            int ix = target + i;
            if (ix < lh.size()) {
                seen = true;
                String id = lh.id2_l.get(ix);
                if (seenPairs != null  &&  seenPairs.contains(id)) {
                    continue;
                }
                PictureResponse pr = tryId(conn, orient, session,
                                                picSet, seenIds, id);
                if (pr != null) {
                    long dctr = lh.value_l.get(ix);
                    pr.value = dctr;
                    pr.method = "gld/6" + (out ? "/out" : "/in");
                    return pr;
                }
            }
            ix = target - i;
            if (i > 0  &&  ix > -1) {
                seen = true;
                String id = lh.id2_l.get(ix);
                if (seenPairs != null  &&  seenPairs.contains(id)) {
                    continue;
                }
                PictureResponse pr = tryId(conn, orient, session,
                                            picSet, seenIds, id);
                if (pr != null) {
                    long dctr = lh.value_l.get(ix);
                    pr.value = dctr;
                    pr.method = "gld/6" + (out ? "/out" : "/in");
                    return pr;
                }
            }
            if (!seen) {
                break;
            }
        }
        return null;
    }

    private PictureResponse tryGoldenAB(Connection conn,
                                        int viewNum, String orient,
                                        Session session, UserProfile up,
                                        Picture prev,
                                        boolean increment, // next golden
                                        Set<String> seenPairs)
            throws SQLException {

        //String lastId = "" + prev.archive + ":" + prev.sequence;

        log.info("tryGoldenAB prev dCtrAB " + prev.dCtrAB);

        int ct = 0;

        for (int delta=1;delta<5;delta++) {

            int newAngle = prev.angAB + delta;

            if (increment) {
                newAngle += 137;
            }

            if (newAngle > 180) {
                newAngle -= 360;
            }

            log.info("Angle " + prev.angAB + " -> " + newAngle +
                     (increment ? " (inc, " : " (noinc, ") +
                     "delta " + delta + ")");

            PictureResponse pr = tryPicsAtAngleAB(conn, viewNum, orient,
                                              session, up,
                                              newAngle, prev, seenPairs);
            ct++;
            if (pr != null) {
                log.info("Angle " + newAngle +
                         " dctr " + pr.value +
                         " ct " + ct);
                return pr;
            }

            if (delta == 0) {
                continue;
            }
            newAngle = prev.angAB - delta;
            if (increment) {
                newAngle += 137;
            }
            if (newAngle > 180) {
                newAngle -= 360;
            }
            pr = tryPicsAtAngleAB(conn, viewNum, orient, session, up,
                                        newAngle, prev, seenPairs);
            ct++;
            if (pr != null) {
                log.info("Angle " + newAngle +
                         " dctr " + pr.value +
                         " ct " + ct);
                return pr;
            }
        }
        log.info("AngleAB: NO GOLD");

        return chooseRandomImage(conn, viewNum, orient, session, true);
    }

    private PictureResponse tryGolden(Connection conn,
                                        int viewNum, String orient,
                                        Session session, UserProfile up,
                                        Picture prev,
                                        int option, Set<String> seenPairs)
            throws SQLException {

        String lastId = prev.id;

        boolean out = up.longerThanSecondLast;
        String angleColumn, distColumn;
        int prevD;
        switch (option) {
            case 7: // 3D
                angleColumn = "rgb_angle";// pairs tbl
                distColumn = "d_ctr_rgb"; // picture tbl
                prevD = prev.dCtrRGB;
                break;
            case 8:
                angleColumn = "b11";// pairs tbl
                distColumn = "d_ctr_27d"; // picture tbl
                prevD = prev.dCtr27D;
                break;
            case 9:
                angleColumn = "b10";// pairs tbl
                distColumn = "d_ctr_8d"; // picture tbl
                prevD = prev.dCtr8D;
                break;
            case 11:
                angleColumn = "b12";// pairs tbl
                distColumn = "d_ctr_64d"; // picture tbl
                prevD = prev.dCtr64D;
                break;
            default:
                throw new RuntimeException("INTERNAL ERROR");
        }
        log.info("tryGolden opt " + option + " " +
                  angleColumn + " " + distColumn +
                  " d " + prevD + " out " + out);

        Set<String> picSet = data.getPicSet(viewNum, orient);


        // handle missing pr.pairs_[vh]

        ListHolder lh1 = null;
        try {

            if (option == 7) {

                lh1 = PairDao.getGoldenRGB(conn, prev.id, orient,
                                          picSet);
            } else if (option == 11) {
                lh1 = PairDao.getMyAngle(conn, prev.id, orient, angleColumn,
                                          picSet);
            } else {
                lh1 = PairDao.getGolden(conn, prev.id, orient, angleColumn,
                                          picSet);
            }

        } catch(SQLException sqe) {

            if (sqe.getMessage().contains("Table does not exist")) {
                return null;
            }

            throw sqe;
        }

        if (lh1.id2_l == null  ||  lh1.size() == 0) {
            log.info("No golden for " + prev.id);
            return null;
        }
        ListHolder lh2 = PictureDao.getIntList(conn, distColumn,
                                                     lh1.id2_l, false);
        // HACK - weird logic
        Collections.reverse(lh2.id2_l);
        Collections.reverse(lh2.value_l);
//for (int i=0;i<lh2.value_l.size()-1;i++) {
//if (lh2.value_l.get(i) > lh2.value_l.get(i+1)) {
//throw new RuntimeException("NOT SORTED");
//}
//}
        int mid = lh2.value_l.size() - 1;
        for (int i=0;i<lh2.value_l.size();i++) {
            if (lh2.value_l.get(i) > prevD) {
                // get in middle of repeats
                int end = i;
                for (int j=i+1;j<lh2.value_l.size();j++) {
                    if (lh2.value_l.get(j) > lh2.value_l.get(i)) {
                        end = j;
                        break;
                    }
                }
                mid = (i + end) / 2;
                break;
            }
        }
        if (!up.longerThanSecondLast) {
            log.info("tryGolden FLIP mid");
            mid = lh2.value_l.size() - 1 - mid;
        }
        int target;
        if (out) {
            target = mid + (int) (up.skew * (lh2.value_l.size() - mid - 1));
        } else {
            target = (int) (up.skew * mid);
        }
        log.info("tryGolden size " + lh2.size() +
                  " min " + lh2.value_l.get(0) +
                  " max " + lh2.value_l.get(lh2.value_l.size()-1) +
                  " mid " + mid +
                  " skew " + up.skew +
                  " target " + target);

        SeenIds seenIds = getSeen(conn, session);

        for (int i=0;i< lh2.size();i++) {
            boolean seen = false;

            int ix = target + i;
            if (ix < lh2.size()) {
                seen = true;

                String id2 = lh2.id2_l.get(i);

                if (seenPairs != null  &&  seenPairs.contains(id2)) {
                    continue;
                }

                PictureResponse pr = tryId(conn, orient, session,
                                                picSet, seenIds, id2);
                if (pr != null) {
                    pr.value = lh2.value_l.get(ix);
                    pr.method = "gld/" + option + (out ? "/out" : "/in");
                    return pr;
                }
            }
            ix = target - i;
            if (i > 0  &&  ix > -1) {
                seen = true;

                String id2 = lh2.id2_l.get(i);

                if (seenPairs != null  &&  seenPairs.contains(id2)) {
                    continue;
                }

                PictureResponse pr = tryId(conn, orient, session,
                                                picSet, seenIds, id2);
                if (pr != null) {
                    pr.value = lh2.value_l.get(ix);
                    pr.method = "gld/" + option + (out ? "/out" : "/in");
                    return pr;
                }
            }
            if (!seen) {
                break;
            }
        }
        log.info("NO GOLD");
        return null;
    }

    private List<Screen> handleGoldenScreensAB(Connection conn,
                                                int viewNum, String orient,
                                                Session session, UserProfile up,
                                                List<String> ids, int option)
            throws SQLException {

        long t1 = System.currentTimeMillis();

        log.info("handleGoldenScreensAB");

        Picture p1 = PictureDao.getPictureById(conn, ids.get(1));

        PictureResponse pr1 = tryGoldenAB(conn, viewNum, orient, session, up,
                                                p1, false, null);
        if (pr1 == null) {
            log.info("GoldenAB init got zilch");
            return null;
        }
        String id1 = pr1.p.id;
        Set<String> seenPairsId1 = null;
        if (session.curator != null) {
            seenPairsId1 = UniquePairDao.getPicsSeen(conn, id1, "id1");
        }

        if (option < 0) {
            // nn 2nd pic
            List<Screen> ret = get2ndPairtopScreen(conn,
                                                    session, up,
                                                    viewNum, orient,
                                                    t1, null, /*tag*/
                                                    pr1, null/*picSet*/);
            if (ret != null) {
                return ret;
            }
            log.info("handleGoldenScreensAB/2nd=nn: failed");
            return null;
        }

        if (!up.longerThanSecondLastUserTime) {
            log.info("handleGoldenScreensAB inverting skew");
            up.skew = 1.0 - up.skew;
        }
        SeenIds seenIds = getSeen(conn, session);
        seenIds.exclude.add(id1);// remember to remove
        PictureResponse pr2 = tryGoldenAB(conn, viewNum, orient, session, up,
                                                pr1.p, true, seenPairsId1);
        seenIds.exclude.remove(id1);
        if (pr2 == null) {
            return null;
        }

        String id2 = pr2.p.id;

        List<Screen> ret = new ArrayList<>();
        ret.add(new Screen(session.browserID, 1, "v", id1, pr1));
        ret.add(new Screen(session.browserID, 2, "v", id2, pr2));
        return ret;
    }

    private List<Screen> handleGoldenScreens(Connection conn,
                                                int viewNum, String orient,
                                                Session session, UserProfile up,
                                                List<String> ids, int option)
            throws SQLException {

        long t1 = System.currentTimeMillis();

        log.info("handleGoldenScreens option " + option);

        boolean nn2nd = false;
        if (option < 0) {
            nn2nd = true;
            option *= -1;
        }

        // continue 'to the right' from rightmost
        String id_prev = ids.get(1);
        Picture p1 = PictureDao.getPictureById(conn, id_prev);

        Set<String> picSet = data.getPicSet(viewNum, orient);
        String colorCol = mapUPRatioToColor(up);
        ListHolder lh = PairTopDao.getPairtopSymm(conn, "col",
                                           orient, id_prev, colorCol,
                                           false, "ASC", 200, picSet);

        int target = (int) (up.skew * lh.size());
        if (target == lh.size()) {
            target = 0;
        }
        log.info("Color target " + target + " list.size " + lh.size() +
                 " skew " + up.skew);

        SeenIds seenIds = getSeen(conn, session);

        PictureResponse pr1 = new PictureResponse();

        int incr = 1;
        int max = lh.size();
        if (max > 100) {
            max = 100;
        }
        for (int i=0;i<max;i++) {

            int theone;
            if (i == 0) {
              theone = target;
            } else if (i % 2 == 1) {
              theone = target - incr;
            } else {
              theone = target + incr;
              incr++;
            }
            if (theone < 0) {
                theone = lh.size() + theone;
            } else if (theone >= lh.size()) {
                theone -= lh.size();
            }
            String id1 = lh.id2_l.get(theone);
            if (seenIds.contains(id1)) {
                continue;
            }
            pr1.p = PictureDao.getPictureById(conn, id1);
            if (pr1.p == null) {
                continue;
            }
            pr1.method = "gld/clr1/" + colorCol;

            if (nn2nd) {
                List<Screen> ret = get2ndPairtopScreen(conn,
                                                    session, up,
                                                    viewNum, orient,
                                                    t1, null, /*tag*/
                                                    pr1, picSet);
                if (ret != null) {
                    return ret;
                }
                log.info("handleGoldenScreens/2nd=nn: failed");
                return null;
            }

            Set<String> seenPairsId1 = null;
            if (session.curator != null) {
                seenPairsId1 = UniquePairDao.getPicsSeen(conn, id1, "id1");
            }
            if (!up.longerThanSecondLastUserTime) {
                log.info("handleGoldenScreens inverting skew");// e.g. >1x
                up.skew = 1.0 - up.skew;
            }
            seenIds.exclude.add(id1);// remember to remove

            // handle pr.pairs_[vh] tbl nonexistence

            PictureResponse pr2 = null;

            try {

                pr2 = tryGolden(conn, viewNum, orient, session, up,
                                      pr1.p, option, seenPairsId1);

            } catch (SQLException sqe) {

                if (sqe.getMessage().contains("Table does not exist")) {
                    return null;
                }

            } finally {
                seenIds.exclude.remove(id1);
            }
            if (pr2 == null) {
                continue;
            }

            String id2 = pr2.p.id;
            List<Screen> ret = new ArrayList<>();
            ret.add(new Screen(session.browserID, 1, "v", id1, pr1));
            ret.add(new Screen(session.browserID, 2, "v", id2, pr2));
            return ret;
        }
        log.info("Golden " + option + " nothing in default list, " + max +
                    " - trying from a random first pic");

        for (int i=0;i<10;i++) {

            pr1 = chooseRandomImage(conn, viewNum, orient, session, true);
            if (pr1 == null) {
                log.info("No random image - sucking sand.");
                break;
            }

            String id1 = pr1.p.id;
            Set<String> seenPairsId1 = null;
            if (session.curator != null) {
                seenPairsId1 = UniquePairDao.getPicsSeen(conn, id1, "id1");
            }
            seenIds.exclude.add(id1);// remember to remove

            PictureResponse pr2 = tryGolden(conn,
                                            viewNum, orient, session, up,
                                            pr1.p, option, seenPairsId1);
            seenIds.exclude.remove(id1);

            if (pr2 == null) {
                continue;
            }

            String id2 = pr2.p.id;

            List<Screen> ret = new ArrayList<>();
            ret.add(new Screen(session.browserID, 1, "v", id1, pr1));
            ret.add(new Screen(session.browserID, 2, "v", id2, pr2));
            return ret;
        }

        log.info("Gold fall back to pure random");
        return handleRandomScreens(conn, viewNum, orient, session, up);
    }


    private Picture tryAngle(Connection conn,
                                    int viewNum,
                                    Session session, UserProfile up,
                                    Set<String> picSet,
                                    SeenIds seenIds,
                                    ListHolder lh, int target)
            throws SQLException {

        String id2 = lh.id2_l.get(target);
        if (seenIds.contains(id2)) {
            return null;
        }
        if (!picSet.contains(id2)) {
            return null;
        }

        // CONST like crazy
        int center = lh.size() / 2;
        int cdist = Math.abs(target - center);
        int val = center + cdist + 2;// how many 'layers' we'll need
        double exponent = 0.0;
        if (center > 0) {
            exponent = 3.0 + 2.0 * (double) cdist / center;
        }

        log.info("LH " + lh.size() + " target " + target + " " + id2 +
                 " levels " + val + " exponent " + exponent +
                 " max " + ((int) Math.pow(val, exponent)));

        ListHolder lh2 = new ListHolder();

        lh2.id2_l.add(id2);
        lh2.value_l.add((long)Math.pow(val, exponent));// 2^0 if only 1 elt

        int offset = 1;
        while (true) {

            val--;

            if (val == 0) {
                log.error("WRONG guess");
                break;
            }

            int tmptarget = target - offset;
            int valid = 0;
            if (tmptarget > -1) {
                valid++;
                id2 = lh.id2_l.get(tmptarget);
                lh2.id2_l.add(id2);
                lh2.value_l.add((long) Math.pow(val, exponent));
            }
            tmptarget = target + offset;
            if (tmptarget < lh.size()) {
                valid++;
                id2 = lh.id2_l.get(tmptarget);
                lh2.id2_l.add(id2);
                lh2.value_l.add((long) Math.pow(val, exponent));
            }
            if (valid == 0) {
                break;
            }
            offset++;
        }

        return chooseFromDistribution(conn, "DRAW", session, up, lh2, -1);
    }

    private PictureResponse drewOnPic(Connection conn,
                                        int viewNum, String orient,
                                        Session session, UserProfile up,
                                        ShowingPair last,
                                        String prevId,
                                        Set<String> prefSet)
            throws SQLException {

        double actualDist = Math.sqrt(last.dotDist);
        double factor1 = Math.sqrt( actualDist / last.dotCount );
        int size = (int) (500.0 * factor1);// CONST
        if (last.dotCount > size) {
            size = last.dotCount;
        }
        ListHolder lh = PairDao.getPairsAtAngle(conn, prevId, orient,
                                             last.dotVecAng, prefSet, size);

        log.info("MM " + last.dotVecAng +
                             " ct " + last.dotCount +
                             " dd " + actualDist +
                             " RAT " + factor1 +
                             " sz " + size +
                             " LH " + lh.size());

        Set<String> picSet = data.getPicSet(viewNum, orient);

        SeenIds seenIds = getSeen(conn, session);

        PictureResponse pr = new PictureResponse();

        if (lh.size() > 0) {
            int target;
            if (last.dotCount < 20) { // CONST
                target = last.dotCount;
                if (target >= lh.size()) {
                    target = target % lh.size();
                }
            } else {

                double factor2;
                if (up.maxDot == last.dotCount) {
                    factor2 = 1.0 - 1.0 / last.dotCount;
                } else {
                    factor2 = (double) last.dotCount / up.maxDot;
                }
                target = (int) (factor2 * lh.size());
            }
            if (target == lh.size()) {
                target = 0;
            }

            pr.p = tryAngle(conn, viewNum, session, up,
                                    picSet, seenIds, lh, target);
            if (pr.p != null) {
                pr.method = "pck/dA/ct_pd0";
                return pr;
            }
        }

        log.info("try wrapping");
        int newAngle;
        if (last.dotVecAng == 180) {
            newAngle = 0;
        } else if (last.dotVecAng == 0) {
            newAngle = 180;
        } else if (last.dotVecAng < 0) {
            newAngle = -179 - last.dotVecAng;
        } else {
            newAngle = 180 - last.dotVecAng;
        }
        lh = PairDao.getPairsAtAngle(conn, prevId, orient,
                                           newAngle, prefSet, size);
        if (lh.size() > 0) {
            int target;
            if (last.dotCount < 20) { // CONST
                target = last.dotCount;
                if (target >= lh.size()) {
                    target = target % lh.size();
                    target = lh.size() - target;
                }
            } else {

                double factor2;
                if (up.maxDot == last.dotCount) {
                    factor2 = 1.0 - 1.0 / last.dotCount;
                } else {
                    factor2 = (double) last.dotCount / up.maxDot;
                }
                factor2 = 1.0 - factor2;
                target = (int) (factor2 * lh.size());
                if (target < 0) target = 0;
            }
            if (target == lh.size()) {
                target = 0;
            }

            pr.p = tryAngle(conn, viewNum, session, up,
                                picSet, seenIds, lh, target);
            if (pr.p != null) {
                pr.method = "pck/dAw/pd0";
                return pr;
            }
        }
        log.info("nothing on Angle");
        return null;
    }

    /*
    **  getD0Match was the fallback, now that pr.pairs_[vh] tables
    **      are built later, this in turn falls back to color vectors.
    */
    private PictureResponse getD0Match(Connection conn,
                                        int viewNum, String orient,
                                        Session session, UserProfile up,
                                        int screenId,   // side of pic
                                        String matchId, // pic next to it
                                        String prevId,  // pic being replaced
                                        Set<String> picSet)
            throws SQLException {



        log.info("getD0Match on screen " + screenId + " for neighbor " + matchId);

        int limit = session.curator == null ? 500 : -1;

        String method = "";
        ListHolder lh = null;

        if (PairDao.hasD0(orient)) {

            method = "d0";
            lh = PairDao.getPosPairs(conn, orient, session.curator, "a_d0",
                                                 matchId,
                                                 (screenId==1), // get_left
                                                 null, // has kwd
                                                 true,  // best
                                                 limit, picSet);
        }

        if (lh == null) {

            String func = (up.restlessMouse || up.skew > 0.68) ? "cosine" : "cartes";
            int[] dims = {256, 1728, 1984 };
            int guess = (int)(up.skew * (double)dims.length);
            if (guess >= dims.length) {
                guess = dims.length - 1;
            }
            int dim = dims[ guess ];
            func = func + ".1." + dim;

            method += ">" + func + dim;

            log.info("getD0Match: falling back " + method + " vectors for " + matchId);
            lh = PictureDao.matchVector(conn, orient,
                                    screenId, matchId,
                                    func,
                                    data.getViewArchives(viewNum),
                                    limit, picSet);
        }

        PictureResponse pr = new PictureResponse();
        pr.p = chooseFromDistribution(conn, method, session, up, lh, -1, orient);
        if (pr.p != null) {
            pr.method = method;
            return pr;
        }
        log.info("getD0Match: return null (d0 and color vectors)");
        return null;
    }


    /*
    **  vectorDistance - convert array of pics to list weighted
    **      by distance func to matchVec, skipping 1st (?)
    **      Minimum distance winds up highest value for
    **      uniform weighted average application.
    **      NOTE: list isn't ordered by distance.
    */
    private ListHolder vectorDistance(int screen,
                                            String func,
                                            double[] matchVec,
                                            Picture[] pics)
            throws Exception {

        ListHolder lh2 = new ListHolder();

        double[] vals = new double[pics.length];
        double max = -99999999.0;
        double min = 99999999.0;

        final double NO_VAL = -999888.777666;

        for (int i=1;i<pics.length;i++) {

            double[] vecs = (screen == 1 ? pics[i].vec_l : pics[i].vec_r);
            if (vecs == null) {
                // recalc may be in progress
                //      TODO calc vectors when importing instead of
                //      adding in proj/update/
                log.warn("vectorDistance: vec" + (screen == 1 ? "_l": "_r") +
                            " NULL for Picture " + pics[i].id);
                vals[i] = NO_VAL;
                continue;
            }
            vals[i] = MathUtil.vec_compare(func, matchVec, vecs, true /* ret 0 on NaN */);
            if (vals[i] > max) max = vals[i];
            if (vals[i] < min) min = vals[i];
        }
        log.info("RANGE " + min + " .. " + max);

        // scale

        double mag = 1.0;

        if (max < 1.0) {
            mag = 10000;
        } else if (max < 10.0) {
            mag = 1000;
        } else if (max < 100.0) {
            mag = 100.0;
        } else if (max < 1000.0) {
            mag = 10.0;
        }

        for (int i=1;i<pics.length;i++) {

            if (vals[i] == NO_VAL) {
                continue;
            }

            lh2.id2_l.add(pics[i].id);
            double flip = max - vals[i];
            double prod = flip * mag;
            long val = (long) (prod);
            //log.info("iscream flip " + flip + " prod " + prod + " =int " + val);
            lh2.value_l.add(val);
        }

        return lh2;
    }

    /*
    **  closestVector - return pic from array w/ vector
    **      'closest' to target, i.e. minimum distance.
    **      Not weighted random, where pic-pic values are
    **      massaged to make 'good' cases maximal.
    */
    private PictureResponse closestVector(Connection conn,
                                            Session session,
                                            int screen,
                                            double[] matchVec,
                                            Picture[] pics)
            throws SQLException {

        String func = MathUtil.compFuncs[
                        rand.nextInt(MathUtil.compFuncs.length)];

        log.info("closestVector: using function " + func);

        return closestVector(conn, session, screen, func, matchVec, pics);

    }

    private PictureResponse closestVector(Connection conn,
                                            Session session,
                                            int screen,
                                            String func,
                                            double[] matchVec,
                                            Picture[] pics)
                throws SQLException {

        SeenIds seenIds = getSeen(conn, session);

        try {

            int ix_closest = -1;
            double val_closest = -1.0;

            for (int i=1;i<pics.length;i++) {

                if (seenIds.contains(pics[i].id)) {
                    continue;
                }
                double val = MathUtil.vec_compare(func, matchVec, pics[i].vec_r);
                if (ix_closest == -1  ||  val < val_closest) {
                    val_closest = val;
                    ix_closest = i;
                }
            }
            if (ix_closest == -1) {
                log.info("All pics seen: " + pics.length);
                return null;
            }

            PictureResponse pr = new PictureResponse();

            pr.p = pics[ix_closest];
            pr.method = (screen == 1 ? "lsl" : "rsr") + "." + func;
                                      // lsl=left->left w/ select

            log.info("closestVec: id " + pr.p.id + " at " + ix_closest + "/" + pics.length +
                                " = " + val_closest);

            return pr;

        } catch (Exception e) {
            log.error("closestVector: " + e);
        }
        return null;
    }

    private int colorDim(int[] locspec) {

        int top = locspec[0];
        int bottom = locspec[1];
        int y = locspec[5];

        int y0 = y - top;
        int height = bottom - top;
        int third = height / 3;

        if (y0 < third) {
            return 256;
        }
        if (y < 2*third) {
            return 1728;
        }
        return 1984;
    }

    private int mlvecDim(int[] locspec) {

        int top = locspec[0];
        int bottom = locspec[1];
        int y = locspec[5];

        int y0 = y - top;
        int height = bottom - top;
        int quarter = height / 4;
        int half = height / 2;

        if (y0 < quarter) {
            return 2;
        }
        if (y < half) {
            return 3;
        }
        if (y < half+quarter) {
            return 5;
        }
        return 12;
    }

    // loc func is based on X

    private String locFunc(int screen, int[] locspec) {

        int left = locspec[2];
        int right = locspec[3];
        int x = locspec[4];

        int x0 = x - left;
        int width = right - left;
        int edge = width / 6;

        if (screen == 1) {
            if (x0 < edge) {
                return "cartes";
            }
            return "cosine";
        }
        // screen 2
        if (x > right - edge) {
            return "cartes";
        }
        return "cosine";
    }

    /*
    **  replacePicOnClick: find another match to the neighbor, based on where
    **                  the click lands, for curate.html
    **
    **      TODO - use click coords to id pic content clicked
    */

    public PictureResponse replacePicOnClick(Connection conn,
                                    int viewNum, String orient,
                                    Session session,
                                    ShowingPair current,
                                    int screen,   // side of clicked pic
                                    String clickLoc,
                                    int[] locspec,
                                    String matchId, // pic next to it
                                    String clickedId)
            throws SQLException {

        log.info("replacePicOnClick: " + clickedId +
                    " on screen " + screen +
                    " nbr " + matchId +
                    " clickLoc " + clickLoc);

        browserLastList.remove(session.browserID);// clear Plus history

        long t1 = System.currentTimeMillis();

        UserProfile up = new UserProfile(conn, session.browserID, 2/*screens*/,
                                         null /* last */);

        Set<String> picSet = data.getPicSet(viewNum, orient);
        SeenIds seenIds = getSeen(conn, session);
        //seenIds.exclude.add(prevId); // remember to remove?

        PictureResponse pr = null;

        if (clickLoc.endsWith("c")) {

            // center - originally keyword match

            pr = getD0Match(conn, viewNum, orient,
                                    session, up,
                                    screen, matchId,
                                    clickedId, picSet);
            if (pr != null) {
                return pr;
            }

            log.info("center getD0Match failed, drop thru");

        }

        ListHolder lh = null;

        String func = locFunc(screen, locspec);
        int dim = 0;

// TODO - add imagenet

        if (screen == 1) {  // left pic

            if (clickLoc.startsWith("L")) {  // left side of left pic, 'outer'
                // histograms: dim in [256,1728,1984]
                dim = colorDim(locspec);
                func = func + ".1." + dim;
            } else {
                // pair vecs: dim in 2, 3, 5, 12
                dim = mlvecDim(locspec);
                func = func + ".3." + dim;
            }

        } else {

            if (clickLoc.startsWith("L")) {  // left side of right pic, 'inner'
                // pair vecs: dim in 2, 3, 5, 12
                dim = mlvecDim(locspec);
                func = func + ".3." + dim;
            } else {
                // histograms: dim in [256,1728,1984]
                dim = colorDim(locspec);
                func = func + ".1." + dim;
            }
        }

        log.info("replacePicOnClick: match pic using " +
                        func + "  screen " + screen);

        lh = PictureDao.matchVector(conn, orient,
                                    screen, clickedId,
                                    func,
                                    data.getViewArchives(viewNum),
                                    50, picSet);

        for (int i=0;i<5;i++) {

            pr = tryLhP(conn, session, orient, current, lh);
            if (pr == null) {
                log.warn("ISNULL " + i + orient);
                break;
            }

            if (!pr.p.id.equals(matchId)) {
                pr.method = (screen == 1 ? "l.l" : "r.r");
                return pr;
            }
            log.info("skipping match == nbr " + matchId);
        }

        log.warn("replacePicOnClick: falling back on random");

        return chooseRandomImage(conn, viewNum, orient, session, true);
    }


    private PictureResponse roundRobin(Connection conn, Session session,
                                       String method, List<List<String>> ids,
                                       Set<String> prefSet, int start, int end,
                                       String orient)
            throws SQLException {

        SeenIds seenIds = getSeen(conn, session);

        int order = start;
        while (true) {
            boolean valid = false;
            int listId = 0;
            for (List<String> idl : ids) {
                listId++;
                if (idl.size() <= order) {
                    continue;
                }
                valid = true;

                String id = idl.get(order);
                if (prefSet != null  &&  !prefSet.contains(id)) {
                    continue;
                }
                if (orient != null  &&
                    "v".equals(orient) != data.verticals.contains(id)) {
                    continue;
                }
                //int as[] = ListHolder.getArchSeq(id);
                if (end == 0) {
                    if (seenIds.contains(id)) {
                        continue;
                    }
                }
                PictureResponse pr = new PictureResponse();
                pr.p = PictureDao.getPictureById(conn, id);
                pr.method = "h_" + method + "/rr/" + listId;
                if (prefSet != null) {
                    pr.method += "/ps";
                }
                return pr;
            }
            if (!valid) {
                break;
            }
            order++;
            if (end > 0  &&  order >= end) {
                break;
            }
        }
        return null;
    }


    private PictureResponse chooseBestPairByColors(Connection conn,
                                        int viewNum, String orient,
                                        Session session,
                                        int rating, ShowingPair last,
                                        String prevId)
            throws SQLException {

        browserLastList.remove(session.browserID);// clear Plus history

        long t1 = System.currentTimeMillis();

        Set<String> picSet = data.getPicSet(viewNum, orient);

        String type = null;
        String column = null;
        ListHolder lh = null;
        Boolean kwd = null;
        switch (rating) {
            case 5:
                type = "rgb_1";
                column = "b0";
                break;
            case 6:
                type = "lab1_1";
                column = "b1";
                break;
            case 7:
                type = "lab2_1";
                column = "b2";
                break;
            case 8:
                //type = "gist_1";
                //column = "b3";
                //break;
            case 9:
                type = "grey_1";
                column = "b4";
                break;
            case 10:
                type = "hs24_1";
                column = "b5";
                break;
            case 11:
                type = "hs48_1";
                column = "b6";
                break;
            case 12:
                type = "combo";
                column = "b3 b2";// gist + lab2k
                break;
            case 13:
                type = "rgb12_1";
                column = "b7";
                break;
            case 14:
                type = "rgb24_1";
                column = "b8";
                break;
            case 15:
                type = "rgb32_1";
                column = "b9";
                break;
            case 16:  // top quality - keep number constant for convenience
                type = "merge";
                column = "b3 b9 b2 b6";
                break;
            case 17:  // top plus kwd
                type = "merge";
                column = "b3 b9 b2 b6";
                kwd = true;
                break;
            default:
                throw new RuntimeException("Browser " + session.browserID +
                                           ": Unexpected option: " + rating);
        }

        log.info("chooseBestPairByColors: " + type + "/" + column +
                 ": Last: " + prevId);

        if ("combo".equals(type)) {
            String meth = column.replaceAll(" ", "");
            String cols[] = column.split(" ");
            if (cols.length != 2) {
                throw new RuntimeException("FIXME: " + column);
            }

            lh = PairTopDao.getPairtopSymm(conn, "col",
                                    orient, prevId, cols[0],
                                    false,  // don't bother inverting
                                    "ASC", 200, picSet);
            Set<String> target = new HashSet<>();
            target.addAll(lh.id2_l);
            // random sanity check
            if (target.size() != lh.size()) {
                throw new RuntimeException("Dupes in list " + prevId);
            }

            ListHolder lh2 = PairTopDao.getPairtopSymm(conn, "col",
                                    orient, prevId, cols[1],
                                    false,  // don't bother inverting
                                    "ASC", 1000, picSet);

            SeenIds seenIds = getSeen(conn, session);

            int valid = 0;
            for (int i=0;i<lh2.size();i++) {
                String id2 = lh2.id2_l.get(i);
                if (!target.contains(id2)) {
                    continue;
                }
                if (seenIds.contains(id2)) {
                    continue;
                }
                valid++;
                log.info("combo set " + target.size() + " saw: " + i +
                                  " valid: " + valid);
                PictureResponse pr = new PictureResponse();
                pr.p = PictureDao.getPictureById(conn, id2);
                int ix = lh.id2_l.indexOf(id2);
                log.info("Combo hash " + ix + " " + lh.value_l.get(ix) +
                                 " list " + i + " " + lh2.value_l.get(i));
                pr.value = lh.value_l.get(ix);
                pr.method = "com_" + meth;
                return pr;
            }
            log.info("combo no luck comparing " + target.size() + " with " +
                     lh.size());
            return null;

        }
        if ("merge".equals(type)) {
            // here we go again
            String cols[] = column.split(" ");
            String meth = column.replaceAll(" ", "").replaceAll("b", "");
            if (kwd != null) {
                meth += "/k";
            }
            List<List<String>> ids = new ArrayList<>();
            for (String c : cols) {
                lh = PairTopDao.getPairtopSymm(conn, "col",
                                        orient, prevId, c,
                                        false, // don't bother inverting
                                        "ASC", 500, picSet);
                ids.add(lh.id2_l);
            }

            int sweep = 30 + last.orderInSession / 50;

            HashCount hc = new HashCount();
            for (List<String> idl : ids) {
                for (int i=0;i<sweep && i<idl.size();i++) {
                    hc.add(idl.get(i));
                }
            }
            List<Set<String>> max_ids = hc.getSetsInOrder();
            if (max_ids == null  ||  max_ids.size() == 1) {
                log.info("No special case out of " + hc.size());
                int newSweep = sweep * 2;
                for (List<String> idl : ids) {
                    if (idl.size() > sweep) {
                        for (int i=sweep;i<newSweep && i<idl.size();i++) {
                            hc.add(idl.get(i));
                        }
                        sweep = newSweep;// ok to keep hitting it
                    }
                }
                max_ids = hc.getSetsInOrder();
                if (max_ids == null  ||  max_ids.size() == 1) {
                    log.info("Still no special case out of " + hc.size());
                }
            }
            log.info("Sweep: " + sweep + " Sets: " +
                      (max_ids == null ? "null" : max_ids.size()));
            if (max_ids == null  ||  max_ids.size() == 1) {
                return roundRobin(conn, session, meth, ids, null, 0, 0, orient);
            }
            SeenIds seenIds = getSeen(conn, session);
            for (Set<String> prefSet : max_ids) {
                Set<String> validSet = new HashSet<>();
                for (String id : prefSet) {
                    if (seenIds.contains(id)) {
                        continue;
                    }
                    validSet.add(id);
                }
                log.info("SET " + prefSet.size() + " => " + validSet.size());
                if (validSet.size() == 0) {
                    continue;
                }
                if (validSet.size() == 1) {
                    // no need to prioritize by list order
                    log.info("GOT 1 max");
                    Object oo[] = validSet.toArray();
                    String id = (String) oo[0];
                    PictureResponse pr = new PictureResponse();
                    pr.p = PictureDao.getPictureById(conn, id);
                    pr.method = "h_" + meth;
                    return pr;
                }
                // check for highest prio in set, in given column order
                PictureResponse pr = roundRobin(conn, session, meth, ids,
                                                      validSet, 0, sweep,
                                                      orient);
                if (pr != null) {
                    return pr;
                }
            }
            // last resort
            log.info("last resort rr");
            meth += "fb";
            return roundRobin(conn, session, meth, ids, null, sweep, 0,
                              orient);
        }

        lh = PairTopDao.getPairtopSymm(conn, "col",
                                orient, prevId, column,
                                false, "ASC", 500, picSet);

        // give best match, unfiltered

        t1 = System.currentTimeMillis();
        logIt(lh, false);
        PictureResponse pr = pickFirstOk(conn, viewNum, orient, session,
                                                lh, type);
        if (pr != null) {
            log.info(type + " dt: " + (System.currentTimeMillis()-t1) +
                     " val " + pr.value);
            return pr;
        }
        return null;
    }

    private String mapUPRatioToColor(UserProfile up) {

        // MAGIC
        double ratio = 1.0;
        if (up.last != null) {
            ratio = (double) Math.abs(up.last.userTime - up.avgUserTime)
                                         / up.maxDeltaUserTime;
        }
        String dir = "none";
        String colCol;
        if (up.longerThanLastUserTime) {
            dir = "longer";
            if (ratio > 0.8) {
                colCol = "b9";
            } else if (ratio > 0.7) {
                colCol = "b8";
            } else if (ratio > 0.6) {
                colCol = "b8";
            } else if (ratio > 0.5) {
                colCol = "b7";
            } else if (ratio > 0.4) {
                colCol = "b7";
            } else if (ratio > 0.35) {
                colCol = "b9";
            } else if (ratio > 0.3) {
                colCol = "b6";
            } else if (ratio > 0.26) {
                colCol = "b4";
            } else if (ratio > 0.24) {
                colCol = "b9";
            } else if (ratio > 0.22) {
                colCol = "b9";
            } else if (ratio > 0.2) {
                colCol = "b8";
            } else if (ratio > 0.18) {
                colCol = "b4";
            } else if (ratio > 0.16) {
                colCol = "b7";
            } else if (ratio > 0.1) {
                colCol = "b5";
            } else {
                colCol = "b5";
            }
        } else {
            dir = "shorter";
            if (ratio > 0.8) {
                colCol = "b2";
            } else if (ratio > 0.7) {
                colCol = "b2";
            } else if (ratio > 0.6) {
                colCol = "b4";
            } else if (ratio > 0.5) {
                colCol = "b4"; // accelerate
            } else if (ratio > 0.4) {
                colCol = "b6";
            } else if (ratio > 0.35) {
                colCol = "b7";
            } else if (ratio > 0.3) {
                colCol = "b8";
            } else if (ratio > 0.26) {
                colCol = "b9";
            } else if (ratio > 0.24) {
                colCol = "b9";
            } else if (ratio > 0.22) {
                colCol = "b4";
            } else if (ratio > 0.2) {
                colCol = "b4";
            } else if (ratio > 0.18) {
                colCol = "b4";
            } else if (ratio > 0.16) {
                colCol = "b4";
            } else if (ratio > 0.1) {
                colCol = "b4";
            } else {
                colCol = "b0";
            }
        }
        log.info("Color choice: " + colCol + " (" + dir + ", " + ratio + ")");
        return colCol;
    }

    private boolean seenPics(Connection conn, Session session, ApprovedPair ap,
                                              SeenIds seenIds)
            throws SQLException {
        if (seenIds.seen == null) {
            throw new RuntimeException("I told you to make sure seen not null");
        }
        if (seenIds.contains(ap.otherId)) {
            return true;
        }
        return false;
    }

    private List<Screen> checkApprovedPair(Connection conn, Session session,
                                           ApprovedPair ap, Boolean hasKwd,
                                           String initId,
                                           PictureResponse initPr,
                                           String orient,
                                           SeenIds seenIds,
                                           int[] debug)
            throws SQLException {

        if (seenIds.contains(ap.otherId)) {
            debug[0]++;
            return null;
        }
        if (ShowingPairDao.checkSeen(conn, session.browserID, ap.id1, ap.id2)) {
            debug[1]++;
            return null;
        }
        PictureResponse pr = new PictureResponse();
        pr.p = PictureDao.getPictureById(conn, ap.otherId);
        if (pr.p == null) {
            debug[2]++;
            log.info("No version for unseen seq " + ap.otherId);
            return null;
        }

        pr.method = "app/" + (hasKwd == null ? "all" :
                                      (hasKwd ? "kwd" : "nokwd"));
        List<Screen> ret = new ArrayList<>();
        if (ap.otherId.equals(ap.id1)) {
            ret.add(new Screen(session.browserID, 1, "v", ap.otherId, pr));
            ret.add(new Screen(session.browserID, 2, "v", initId, initPr));
        } else {
            ret.add(new Screen(session.browserID, 1, "v", initId, initPr));
            ret.add(new Screen(session.browserID, 2, "v", ap.otherId, pr));
        }
        log.info("checkApprovedPair ok: " + screenSummary(ret));
        return ret;
    }


    private List<Screen> tryForApprovedPair(Connection conn,
                                            int viewNum, String orient,
                                            Session session,
                                            Set<String> picSet,
                                            String id, PictureResponse initPr,
                                            Boolean hasKwd, double ratio)
            throws SQLException {

        List<ApprovedPair> approvedPairs = ApprovalDao.getApprovedPairsWithPic(
                                                    conn, id, // hasKwd,
                                                    picSet);
        if (approvedPairs == null  ||  approvedPairs.size() == 0) {
            log.info("tryForApprovedPair (No approved pairs in set size: " +
                            (picSet == null ? "0" : picSet.size()) + ": " + id);
            return null;
        }
        int target = (int) (ratio * approvedPairs.size());
        if (target < 0  ||  target > approvedPairs.size()) {
            int r = rand.nextInt(approvedPairs.size());
            log.info("Adjust target " + target + " to " + r);
            target = r;
        }
        int offset = 0;

        SeenIds seenIds = getSeen(conn, session);

        //log.info("tryForApprovedPair pairs: " + approvedPairs.size() +
                 //" seenIds.seen " + (seenIds.seen == null ? "null" : "yes"));
        int[] debug = new int[3];
        List<List<Screen>> bak = new ArrayList<>();
        while (true) {

            boolean tried = false;

            int ix = target + offset;
            if (ix < approvedPairs.size()) {
                ApprovedPair ap = approvedPairs.get(ix);
                List<Screen> scl = checkApprovedPair(conn, session, ap,
                                                     hasKwd, id, initPr,
                                                     orient, seenIds, debug);
                if (scl != null) {
                    return scl;
                }
                tried = true;
            }
            ix = target - offset;
            if (ix != target  &&  ix > -1) {
                ApprovedPair ap = approvedPairs.get(ix);
                List<Screen> scl = checkApprovedPair(conn, session, ap,
                                                     hasKwd, id, initPr,
                                                     orient, seenIds, debug);
                if (scl != null) {
                    return scl;
                }
                tried = true;
            }
            if (!tried) {
                break;
            }
            offset++;
        }
        log.info("(No unseen approved pairs out of " + approvedPairs.size() +
                 " seen pics " +
                   (seenIds == null ? "null" : seenIds.seen.size()) +
                 " debug " + Arrays.toString(debug));
        return null;
    }

    private List<Screen> getColorScreensPic2(Connection conn,
                                            int viewNum, String orient,
                                            Session session, UserProfile up,
                                            int option, int n, int depth,
                                            String secondColorCol,
                                            PictureResponse initPr)
            throws SQLException {

        // TODO - rethink for opposite w/ n>2

        String id = initPr.p.id;

        Set<String> picSet = data.getPicSet(viewNum, orient);

        log.info("Got first (opt " + option + "): " + id + " " + initPr.method +
                 " orient " + orient + " depth " + depth);

        ListHolder lh;

        // lists are searched in order, weights not used, so invert=false
        if (option == -1) {
            // TODO - move to use-weights code
            // a_d0 is down
            log.warn("Dude, yr still usin color code for NN's?");
            lh = PairDao.getPosPairs(conn, orient, session.curator, "a_d0", id,
                                          false, // get_left => l->r
                                          null, // hasKwd
                                          true, // == "DESC"
                                          depth, picSet);
        } else if (option == 0) { //  ||  option == 13  ||  option == 14) {
            // furthest from first in color space, closest in phi space
            log.error("No negs supported in pairtop_col, returning junk");
            lh = PairTopDao.getPairtopSymm(conn, "col",
                                    orient, id, secondColorCol,
                                    false, "DESC", depth, picSet);
        } else {
            // closest to first in color space
            lh = PairTopDao.getPairtopSymm(conn, "col",
                                    orient, id, secondColorCol,
                                    false, "ASC", depth, picSet);
        }

        if (lh == null  ||  lh.size() < n-1) {
            return null;
        }

        log.info("size " + lh.size());

        int skipped_seen = 0;
        Set<String> seenPairs1 = null;
        Set<String> seenPairs2 = null;
        if (session.curator != null) {
            seenPairs1 = UniquePairDao.getPicsSeen(conn, id, "id1");
            seenPairs2 = UniquePairDao.getPicsSeen(conn, id, "id2");
        }

        SeenIds seenIds = getSeen(conn, session);

        List<Screen> ret = new ArrayList<>();

        int triedThisTime = 0;
        int sci = 1;
        int i;
        for (i=0;i<lh.size()  &&  ret.size() != n;i++) {
            String id2 = lh.id2_l.get(i);

            if (seenIds.contains(id2)) {
                continue;
            }
            triedThisTime++;

            PictureResponse pr = new PictureResponse();
            pr.p = PictureDao.getPictureById(conn, id2);
            if (pr.p == null) {
                log.info("No version for unseen seq " + id2);
                continue;
            }
            if (option == 0) {
                pr.method = "opp/" + secondColorCol;
            } else if (option == 13  ||  option == 14) {
                pr.method = "phi/" + secondColorCol;
            } else {
                pr.method = "clr/" + secondColorCol;
            }

            // swap screens to preserve order if pics within 5 of each other
            if (ret.size() == 0) {
                if (initPr.p.archive != pr.p.archive  ||
                    initPr.p.sequence < pr.p.sequence  ||
                    initPr.p.sequence > pr.p.sequence + 5) {
                    //                             screen
                    if (seenPairs1 != null  &&  seenPairs1.contains(id2)) {
                        log.info("skipping..");
                        skipped_seen++;
                        continue;
                    }
                    ret.add(new Screen(session.browserID, 1, "v",
                                                              id, initPr));
                    ret.add(new Screen(session.browserID, 2, "v",
                                                             id2, pr));
                } else {
                    log.info("Swapping for seq closeness");
                    if (seenPairs2 != null  &&  seenPairs2.contains(id2)) {
                        log.info("skipping..");
                        skipped_seen++;
                        continue;
                    }
                    // swap methods for reporting
                    String m = pr.method;
                    pr.method = initPr.method;
                    initPr.method = m;
                    ret.add(new Screen(session.browserID, 1, "v",
                                                             id2, pr));
                    ret.add(new Screen(session.browserID, 2, "v",
                                                              id, initPr));
                }
                sci = 2;
            } else {
                // sci may be wring since screens changed 0,1 -> 1,2
                ret.add(new Screen(session.browserID, sci++, "v",
                                                           id2, pr));
            }
        }
        if (ret.size() == n) {
            log.info("Color N " + up.last.orderInSession +
                     " i " + i +
                     " skipped_seen " + skipped_seen +
                     " " + screenSummary(ret));
            return ret;
        }
        log.info("(getColorScreensPic2 Failed: TRIED " + triedThisTime +
                 " skipped_seen " + skipped_seen);
        if (triedThisTime == 0) {
            return null;
        }
        ret.clear();
        return ret;
    }

    private List<Screen> getColorComboScreensPic2(Connection conn,
                                         int viewNum, String orient,
                                         Session session, UserProfile up,
                                         int option, int n, int depth,
                                         String secondColorCol, String colCol,
                                         PictureResponse initPr)
            throws SQLException {

        // TODO - rethink for opposite w/ n>2

        String id = initPr.p.id;

        Set<String> picSet = data.getPicSet(viewNum, orient);

        log.info("getColorComboScreensPic2: (opt " + option + "): match " +
                 id + " " + initPr.method +
                 " orient " + orient + " depth " + depth);

        ListHolder lh, lh2;

        if (option == 0) {

            // furthest from first==left in color space

            log.error("Pairtop DESC not supported, returning junk");
            lh = PairTopDao.getPairtopSymm(conn, "col",
                                    orient, id, secondColorCol,
                                    false, "DESC", depth, picSet);
            lh2 = PairTopDao.getPairtopSymm(conn, "col",
                                     orient, id, colCol,
                                     false, "DESC", depth, picSet);
        } else {

            // closest to first==left in color space

            lh = PairTopDao.getPairtopSymm(conn, "col",
                                    orient, id, secondColorCol,
                                    false, "ASC", depth, picSet);
            lh2 = PairTopDao.getPairtopSymm(conn, "col",
                                     orient, id, colCol,
                                     false, "ASC", depth, picSet);
        }

        if (lh == null  ||  lh.size() < n-1) {
            return null;
        }

        log.info("raw sizes " + lh.size() + " " + lh2.size());

        Set<String> target = new HashSet<>();
        target.addAll(lh.id2_l);

        int skipped_seen = 0;
        Set<String> seenPairs1 = null;
        Set<String> seenPairs2 = null;
        if (session.curator != null) {
            seenPairs1 = UniquePairDao.getPicsSeen(conn, id, "id1");
            seenPairs2 = UniquePairDao.getPicsSeen(conn, id, "id2");
        }

        SeenIds seenIds = getSeen(conn, session);

        ListHolder ok_lh = new ListHolder();
        ListHolder bak_lh = new ListHolder();

        int triedThisTime = 0;
        int sci = -1;
        int i;

        for (i=0;i<lh2.size();i++) {

            String id2 = lh2.id2_l.get(i);

            if (seenIds.contains(id2)) {
                continue;
            }
            if (target.contains(id2)) {

                ok_lh.id2_l.add(id2);
                ok_lh.value_l.add(lh2.value_l.get(i));

            } else {

                bak_lh.id2_l.add(id2);
                bak_lh.value_l.add(lh2.value_l.get(i));
            }
        }
        log.info("sorted sizes " + ok_lh.size() + " " +
                                   bak_lh.size());

        PictureResponse pr = new PictureResponse();
        if (option == 0) {
            pr.method = "copp/" + secondColorCol + "/" + colCol;
        } else {
            pr.method = "cclr/" + secondColorCol + "/" + colCol;
        }

        List<Screen> ret = new ArrayList<>();

        if (ok_lh.size() > 0) {

            ok_lh.value_l = invertDistribution(ok_lh.value_l);
            pr.lh = ok_lh;
            while (ok_lh.size() > 0) {
                pr.p = chooseFromDistribution(conn, "COMBO", session, up, ok_lh, -1,
                                                         orient);
                if (pr.p == null) {
                    continue;
                }

                String id2 = pr.p.id;

                triedThisTime++;

                // swap screens to preserve order if pics within 5 of each other
                if (initPr.p.archive != pr.p.archive  ||
                    initPr.p.sequence < pr.p.sequence  ||
                    initPr.p.sequence > pr.p.sequence + 5) {
                    //                             screen
                    if (seenPairs1 != null  &&  seenPairs1.contains(id2)) {
                        log.info("skipping..");
                        skipped_seen++;
                        continue;
                    }
                    ret.add(new Screen(session.browserID, 1, "v", id, initPr));
                    ret.add(new Screen(session.browserID, 2, "v", id2, pr));
                } else {
                    log.info("Swapping for seq closeness");
                    if (seenPairs2 != null  &&  seenPairs2.contains(id2)) {
                        log.info("skipping..");
                        skipped_seen++;
                        continue;
                    }
                    // swap methods for reporting
                    String m = pr.method;
                    pr.method = initPr.method;
                    initPr.method = m;
                    ret.add(new Screen(session.browserID, 1, "v", id2, pr));
                    ret.add(new Screen(session.browserID, 2, "v", id, initPr));
                }
                break;
            }
        }

        if (ret.size() == n) {

            log.info("CColor N " + up.last.orderInSession +
                     " skipped_seen " + skipped_seen +
                     " " + screenSummary(ret));
            return ret;
        }

        // try the bak list

        pr.method += "/bk";
        pr.lh = bak_lh;
        bak_lh.value_l = invertDistribution(bak_lh.value_l);

        while (bak_lh.size() > 0) {

            pr.p = chooseFromDistribution(conn, "COMBO/bak", session, up,
                                                bak_lh, -1, orient);
            if (pr.p == null) {
                continue;
            }
            String id2 = pr.p.id;

            triedThisTime++;

            // swap screens to preserve order if pics within 5 of each other
            if (initPr.p.archive != pr.p.archive  ||
                initPr.p.sequence < pr.p.sequence  ||
                initPr.p.sequence > pr.p.sequence + 5) {
                //                             screen
                if (seenPairs1 != null  &&  seenPairs1.contains(id2)) {
                    log.info("skipping..");
                    skipped_seen++;
                    continue;
                }
                ret.add(new Screen(session.browserID, 1, "v", id, initPr));
                ret.add(new Screen(session.browserID, 2, "v", id2, pr));

            } else {

                log.info("Swapping for seq closeness");
                if (seenPairs2 != null  &&  seenPairs2.contains(id2)) {
                    log.info("skipping..");
                    skipped_seen++;
                    continue;
                }
                // swap methods for reporting
                String m = pr.method;
                pr.method = initPr.method;
                initPr.method = m;
                ret.add(new Screen(session.browserID, 1, "v", id2, pr));
                ret.add(new Screen(session.browserID, 2, "v", id, initPr));
            }
            break;
        }
        if (ret.size() == n) {
            log.info("CColor bak N " + up.last.orderInSession +
                     " skipped_seen " + skipped_seen +
                     " " + screenSummary(ret));
            return ret;
        }

        log.info("(getCColorScreens Failed: TRIED " + triedThisTime +
                 " skipped_seen " + skipped_seen);

        if (triedThisTime == 0) {
            return null;
        }
        ret.clear();
        return ret;
    }

    private String modelType(String type) {

        if ("1".equals(type)) {
            return "color";
        }
        if ("2".equals(type)) {
            return "imagenet";
        }
        if ("3".equals(type)) {
            return "pairnet";
        }
        return null;
    }

    private String chooseVector2(int ndots) {

        if (ndots % 3 == 0) {
            return ".2.vgg16_2"; // avg/fold - indexed
        } else if (ndots % 2 == 0) {
            return ".2.dense_2"; // avg/fold - indexed
        }
        return ".2.mob_2"; // avg/fold - indexed
    }

    private String nDotsToVectorColumn(int ndots) {

        // choose vector type for matching a drawn-on pic:
        //      color histogram,
        //      averaged/folded imagenet vector,
        //      pairml vector.

        if (ndots < 10) {
            return chooseVector2(ndots);
        }

        if (ndots < 13) {
            return ".2.nnl_3"; // avg/fold - indexed
        }

        if (ndots < 24) {
            if (ndots % 2 == 0) {
                return ".2.vgg16_4"; // avg/fold - indexed
            } else {
                return ".2.dense_4"; // avg/fold - indexed
            }
        }

        if (ndots < 28) {
            return ".2.mob_5"; // avg/fold - indexed
        }
        if (ndots < 32) {
            return ".2.nnl_7"; // avg/fold - indexed
        }
        if (ndots < 40) {
            return ".2.mob_10"; // avg/fold - indexed
        }
        if (ndots < 50) {
            return ".2.vgg16_16"; // avg/fold - indexed
        }
        if (ndots < 60) {
            return ".2.nnl_21"; // avg/fold - indexed
        }
        if (ndots < 65) {
            return ".2.vgg16_64"; // avg/fold - indexed
        }

        return ".1.histo_gss"; // greyscale/sat, 256
    }

    /*
    **  handleVectorsInParallel2 - w/ dots
    */
    private List<Screen> handleVectorsInParallel2(Connection conn,
                                            int viewNum, String orient,
                                            Session session, UserProfile up,
                                            List<String> ids)
            throws Exception {

        ShowingPair last = up.last;  // should match w/ ids
        DotHistory dh = last.dotHistory;

        // for each pic,
        //      choose a distance function and vector to match
        //
        // funcDim:
        //
        //      <cos|poi>.<1|2|3>.<name>

        String[] funcDims = new String[ids.size()];

        for (int i=0; i<ids.size(); i++) {

            funcDims[i] = null;

            int ndots;
            if (i==0) {
                ndots = dh.dots1;
            } else {
                ndots = dh.dots2;
            }
            if (ndots > 0) {

                // side has dots!
                //  base it on user behavior in-pic alone
                //  TODO add pic, pic:drawing info

                if (ndots % 2 == 0) {
                    funcDims[i] = "cos";
                } else {
                    funcDims[i] = "poi"; // distance, orig poincare
                }

                funcDims[i] += nDotsToVectorColumn(ndots);

            } else {

                // no user behavior, so base it on characteristics
                //      of the ignored picture, either alone or
                //      in comparison w/ the one that got touched.
                //
                //      Immediate issue on using the pic alone is
                //      building in a determination of valid/useful
                //      cutoff(s).
                //
                //  ideas:
                //
                //      1: PictureDao always loads e.g. vgg16_16
                //          and use the vector here somehow
                //          - candidate for next-step
                //              decision tree / forest experiment
                //              which could include the other tabular data
                //      2+:
                //      pics in same archive?
                //      other 1st-level, ~meaningless but easy things to compare
                //          to make a cutoff for or compare pics on:
                //        public int         density;
                //        public int         dCtrRGB;
                //        public int         rgbRadius;
                //        public int         ll;
                //        public int         aa;
                //        public int         bb;
                //        public int         labRadius;
                //        public int         labContrast;
                //        public int         dCtrAB;
                //        public int         angAB;
                //        public int         dCtr8D;
                //        public int         dCtr27D;
                //        public int         dCtr64D;

                //          trying labRadius
                Picture p1 = PictureDao.getPictureById(conn, last.id1);
                Picture p2 = PictureDao.getPictureById(conn, last.id2);
                if (i==0) {
                    if (p1.labRadius > p2.labRadius) {
                        funcDims[i] = "cos";
                    } else {
                        funcDims[i] = "poi";
                    }
                } else {
                    if (p1.labRadius > p2.labRadius) {
                        funcDims[i] = "poi";
                    } else {
                        funcDims[i] = "cos";
                    }
                }

                // choose vector type for matching ignored pic:
                //      color histogram,
                //      averaged/folded imagenet vector,
                //      pairml vector.
                //TODO - loosest==greyscale/sat for now
                //       maybe try impatience flags in UserProfile?
                funcDims[i] += ".1.histo_gss";
            }
        }

        log.info("Per-pic vector matching: " + Arrays.toString(funcDims));

        // get geom nbr lists for each pic

        List<String> picList = data.getPicList(viewNum, orient);
        int effectiveSize = picList.size();
        Set<String> picSet = data.getPicSet(viewNum, orient);

        ListHolder lhs[] =
            PictureDao.matchVectors(conn, orient, ids, funcDims,
                                    data.getViewArchives(viewNum),
                                    50, picSet);

        //log.info("lhs: " + lhs[0].size() + " " + lhs[1].size());

        // check
        for (int i=0; i<lhs.length; i++) {
            if (lhs[i].size() == 0) {
                log.warn("no list on " + i + " - returning null");
                return null;
            }
        }

        // twoStageFunc to match pic1 choice against pic2's list
        //      Not needing indexed vectors here since only
        //      calcing/sorting

        String twoStageFunc = null;
        if (up.lastCrossings > 0) {
            String func = "poi";
            if (up.speedUp  ||  up.restlessMouse) {
                func = "cos";
            }
            switch (up.lastCrossings) {
            case 0: break;
            case 1:
                twoStageFunc = func + ".2.mob_40";
                break;
            case 2:
                twoStageFunc = func + ".2.nnl_21";
                break;
            case 3:
                twoStageFunc = func + ".2.nnl_7";
                break;
            case 4:
                twoStageFunc = func + ".2.vgg16_16";
                break;
            default:
                twoStageFunc = func + ".1.histo_gss";
                break;
            }
        }

        //SeenIds seenIds = getSeen(conn, session);

        List<Screen> scl = null;
        if (twoStageFunc == null) {
            scl = oneStageVectorMatch(conn, viewNum, orient, session, up.last, lhs, funcDims);
        } else {
            scl = twoStageVectorMatch(conn, viewNum, orient, session, up.last, lhs, funcDims, twoStageFunc);
        }
        if (scl != null) {
            return scl;
        }

        log.error("handleVectorsInParallel2 was called --- how could this fail?");
        return null;
    }

    private List<Screen> handleVectorsInParallel(Connection conn,
                                            int viewNum, String orient,
                                            Session session, UserProfile up,
                                            int n, List<String> ids,
                                            String firstFunc,
                                            String twoStageFunc)
            throws Exception {

        String[] firstFunx = firstFunc.split("\\.");// trust the funx
        if (firstFunx.length != 3) {
            throw new Exception("handleVectorsInParallel: not a.b.c: [" + firstFunc + "]: " + firstFunx.length);
        }
        log.info("handleVectorsInParallel " + viewNum + orient + ": " +
                    firstFunc + (twoStageFunc == null ? " " : "/" + twoStageFunc) + ": " +
                    ids.get(0) + " " + ids.get(1));

        long t1 = System.currentTimeMillis();

        int skipped_seen = 0;// for LOCAL

        int seen = 0;
        if (up.last != null) {
            seen = up.last.orderInSession;
        } else {
            seen = ShowingPairDao.countShowings(conn, session.browserID, orient);
            log.info("Showings counted: " + seen + " browser " + session.browserID);
        }

        List<String> picList = data.getPicList(viewNum, orient);
        int effectiveSize = picList.size();
        Set<String> picSet = data.getPicSet(viewNum, orient);

        // get geom nbr lists for each pic
        ListHolder lhs[] = null;

        // dynamic: 2 vector matches
        //      get the simple {func, dim} for basic l=>l', r->r' lists
        //          - cartesian is used for poincare since in pgvector

        String func = firstFunc.startsWith("cos") ? "cosine" :
                        firstFunc.startsWith("poi") ? "cartes" : null;
        if (func == null) {
            log.error("Unexpected func (using cosine): " + firstFunc);
            func = "cosine";
        }

        String type = firstFunx[1];
        String model = firstFunx[2];

        // dim or die
        int dim = PictureDao.modelDimension(type, model);
        type = modelType(type);

        if (type == null  ||  dim == -1) {
            log.error("Unexpected firstFunc type/model/dim: " + firstFunc);

            type = "histogram";
            dim = 1984;
        }

        log.info("VEX " + firstFunc + " -> " + func + "." + type + "." + dim);

        lhs = PictureDao.matchVectors(conn, orient, ids, func, type, dim,
                                    data.getViewArchives(viewNum),
                                    50, picSet);

        //log.info("lhs: " + lhs[0].size() + " " + lhs[1].size());

        if (lhs[0].size() == 0  ||  lhs[1].size() == 0) {

            log.warn("no list on one or both - returning null");

            return null;
        }

        //SeenIds seenIds = getSeen(conn, session);

        String[] funcDims = new String[] { firstFunc, firstFunc };

        List<Screen> scl = null;
        if (twoStageFunc == null) {
            scl = oneStageVectorMatch(conn, viewNum, orient, session, up.last, lhs, funcDims);
        } else {
            scl = twoStageVectorMatch(conn, viewNum, orient, session, up.last, lhs, funcDims, twoStageFunc);
        }
        if (scl != null) {
            return scl;
        }

        log.warn("handleVectorsInParallel: returning null");

        return null;
    }

    private List<Screen> handlePrPosScreens(Connection conn,
                                            int viewNum, String orient,
                                            Session session, UserProfile up,
                                            String posColumn,
                                            List<String> ids, int which_id)
            throws SQLException {

        if (!PairDao.hasD0(orient)) {

            log.info("handlePrPosScreens: no pairs_" + orient +
                        ".d0 - defaulting to vectors");

            List<Screen> ret = null;

            try {
                ret = handleVectorsInParallel(conn, viewNum, orient,
                                            session, up,
                                            2, ids,
                                            "cos.3.5", // (func.type.dim)
                                            "poi.1.hist_gss");// l2rFunc
            } catch (Exception e) {
                throw new SQLException("wrapping e", e);
            }
            if (ret != null) {
                log.info("VEXX ok /cos5");
                return ret;
            }

            return handleColorScreens(conn, viewNum, orient,
                                            session, up,
                                            "d0def", 2, ids);
        }

        String stack = MiscUtil.getStack(7);
        log.warn("CALLING SLOW METHOD:\n" + stack);

        long t1 = System.currentTimeMillis();

        boolean parallel = false;

        if (which_id == -1) {
            //parallel = true;
            which_id = 0;
            if (up.dots1 < up.dots2  &&  ids.size() > 1) {
                log.info("crossings: Using dots.2 for plain pos");
                which_id = 1;
            }
        }
        String id = ids.get(which_id);

        Set<String> picSet = data.getPicSet(viewNum, orient);


        int skipped_seen = 0;// for LOCAL

        PictureResponse initPr = new PictureResponse();

        int limit = session.curator == null ? 500 : -1;


        if (posColumn == null) {
            List<List<String>> pairs_cols = data.getPairsNNCols(
                                                            "v".equals(orient));
            List<String> cols = pairs_cols.get(1);// a_
            int n = (int)(up.skew * cols.size());
            if (n < 0  ||  n >= cols.size()) n = up.scaleInt(n);
            if (n < 0  ||  n >= cols.size()) n = rand.nextInt(cols.size());
            posColumn = cols.get(n);
            log.info("Default column: " + posColumn);
        }


        // TODO - rename to include the neg column

        boolean desc = true;// averaged, e.g. a_d0,a_x,a_kwd  => bigger is better
        if (posColumn.startsWith("n_")) {
            // percent of nets voting pair 'bad'               => smaller is better
            log.warn("Handling a neg column w/ pos handler," +
                     " so flipping search");
            desc = false;
        }
        ListHolder lh;

        if (parallel) {
            // try more-extensive parallel
            lh = PairDao.getPosPairsParallel(conn, orient, session.curator, posColumn,
                                                    ids, true, 200, picSet);
        } else {
            // best ==guess; TODO need to go non-full-opposite when false
            boolean best = up.lastCrossings < 2;
            //limit *= 2;
            lh = PairDao.getPosPairs(conn, orient, session.curator, "a_d0", id,
                                           which_id == 0 ? true : false, //left
                                           null, // hasKwd
                                           best, // DESC==best
                                           50, picSet);
        }

        if (lh.size() == 0) {
            log.info("handlePrPosScreens/" + posColumn + " id " + id +
                    " Exhausted for now - random");
            return handleRandomScreens(conn, viewNum, orient, session, up);
        }

        if (lh.size() > 100  &&  up.speedUp) {

            double skew = up.skew;
            if (skew > 1.0) skew -= Math.floor(skew);
            int mid = (int)(skew * (lh.size()-1));

            log.info("speedup: 'triangle' fold somewhere in middle: " + mid);

            long val = 10 + lh.size() * 2;

            ListHolder lhx = new ListHolder();

            lhx.id2_l.add(lh.id2_l.get(mid));
            lhx.value_l.add(val);
            val -= 2;

            int offset = 0;
            while (lhx.size() < lh.size()) {
                offset++;
                int index = mid - offset;
                if (index > -1) {
                    lhx.id2_l.add(lh.id2_l.get(index));
                    lhx.value_l.add(val);
                    val -= 2;
                }
                index = mid + offset;
                if (index < lh.size()) {
                    lhx.id2_l.add(lh.id2_l.get(index));
                    lhx.value_l.add(val);
                    val -= 2;
                }
            }
            lh = lhx;
        }

        // up.last => use dot-derived histogram
        createDblL(up.last, lh, null);

        log.info("handlePrPosScreens/" + posColumn +
                 ": Matching " + id +
                 " limit: " + limit +
                 " Got: " + lh.size());

        int try1 = 0;
        int try1_net = 0;
        int try1_seen = 0;

        while (lh.size() > 0) {

            int l1 = lh.size();
            initPr.p = chooseFromDistribution(conn, posColumn + "/",
                                                    session, up, lh, -2);
            l1 -= lh.size();
            try1_net += l1;
            if (initPr.p == null) {
                log.info("Exhausted 1st list");
                break;
            }
            try1++;
            log.info("PrPos 1st " + initPr.method);
            List<Screen> ret = get2ndPrPosScreen(conn,
                                                 session, up, viewNum, orient,
                                                 picSet, posColumn, initPr);
            if (ret != null) {
                return ret;
            }

        }

        log.info("handlePrPosScreens/" + posColumn +
                 " Exhausted for now - random");

        return handleRandomScreens(conn, viewNum, orient, session, up);
    }

    private List<Screen> tryNeighborsCache(Connection conn,
                                                int viewNum, String orient,
                                                Session session,
                                                List<String> ids, int which,
                                                int circle)

            throws SQLException {

        // just d0 to start

        Map<Long, NeighborsCache> map = null;
        switch (circle) {
            case 0:
                map = d0NbrCache;
                break;
            case 1:
                map = d0NextNbrCache;
                break;
            default:
                log.error("Unhandled  circle: " + circle);
                return null;
        }

        //log.info("try fast Neighbors cache, which=" + which);
        // TODO - remove, or get+modify?
        NeighborsCache cache = map.remove(session.browserID);
        if (cache == null) {
            log.info("Nothing in cache circle " + circle + " size " + map.size() +
                        " browser " + session.browserID);
            return null;
        }
        if (cache.upList.size() + cache.downList.size() == 0) {
            log.error("cache circle " + circle + " has no entries. " + session.browserID);
            return null;
        }

        log.info("tryNeighborsCache cached circle " + circle + " browsers: " + map.size() +
                        " browserID " + session.browserID + " up/down sizes: " +
                        cache.upList.size() + " " +
                        cache.downList.size());

        Pair p = null;
        if (cache.upList.size() > 0) {
            p = getWhich(cache.upList, which);
        } else if (cache.downList.size() > 0) {
            p = getWhich(cache.downList, which);
        }

        if (p == null) {
            log.info("Neighb circle " + circle + " entries tried, nada");
            return null;
        }

        log.info("Neighbors: circle " + circle + " " + p.id1 + " " + p.id2);

        PictureResponse pr1 = new PictureResponse();
        pr1.p = PictureDao.getPictureById(conn, p.id1);
        PictureResponse pr2 = new PictureResponse();
        pr2.p = PictureDao.getPictureById(conn, p.id2);

        if (pr1.p == null  ||  pr2.p == null) {
            log.error("tryNeighborsCache circle " + circle + " err/ pic not there");
            return null;
        }
        if (pr1.p.vertical != pr2.p.vertical) {
            log.error("tryNeighborsCache circle " + circle + " err/ verticals !=");
            return null;
        }
        if (pr1.p.vertical != "v".equals(orient)) {
            log.error("tryNeighborsCache circle " + circle + " err/ not oriented: " + orient);
            return null;
        }

        log.info("tryNeighborsCache circle " + circle + " ok " + p.values[0]);
        pr1.method = "d0c/" + circle;
        pr2.method = "d0c/" + circle;

        List<Screen> scl = new ArrayList<>();

        scl.add(new Screen(session.browserID,
                                   1, "v", p.id1,
                                   pr1));
        scl.add(new Screen(session.browserID,
                                   2, "v", p.id2,
                                   pr2));
        return scl;
    }

    private List<Screen> handleNeuralNegScreens(Connection conn,
                                            int viewNum, String orient,
                                            Session session, UserProfile up,
                                            List<String> ids)
            throws SQLException {

        if (!PairDao.hasD0(orient)) {
            log.info("Neg and no pairs table: TODO something with vecs");
            return handleRandomScreens(conn, viewNum, orient, session, up);
        }

        String NEG_COL = "a_d0";
        Set<String> picSet = data.getPicSet(viewNum, orient);

        Set<String> seenPairs1 = null;
        Set<String> seenPairs2 = null;

        int skipped_seen = 0;// for LOCAL

        PictureResponse initPr = new PictureResponse();
        PictureResponse pr2 = new PictureResponse();

        // choose baseid w/out considering user
        int first = rand.nextInt(2);
        int second = (first + 1) % 2;
        String baseid = ids.get(first);
        String otherbaseid = ids.get(second);

        int limit = session.curator == null ? 500 : -1;

        String negtag = NEG_COL.replace("a_", "");

        //String column = rand.nextBoolean() ? "a_d0" : "a_kwd";

        log.info("handleNeuralNegScreens " + orient +
                 " " + negtag +
                 " " + baseid);
        ListHolder lh = PairDao.getPosPairs(conn, orient, session.curator, NEG_COL, baseid,
                                                  true, // get left
                                                  //false, // hasKwd
                                                  null, // hasKwd
                                                  false, // ASC == bottom
                                                  limit, picSet);

        if (lh.size() == 0) {
            log.error("handleNeuralNegScreens No pairs - going random");
            return handleRandomScreens(conn, viewNum, orient, session, up);
        }

        if (up.last == null  ||  up.last.dotCount == 0) {
            // bias to 1st
            long val = lh.size() + 2;
            for (int i=0;i<lh.size();i++) {
                lh.value_l.set(i, (val*val));
                val--;
            }
        }
        // up.last => use dot-derived histogram
        createDblL(up.last, lh, null);
        log.info("handleNeuralNegScreens: Matching " + baseid +
                 " limit: " + limit +
                 " Got: " + lh.size() +
                 " Range: " + lh.value_l.get(0) +
                   ".." + lh.value_l.get(lh.value_l.size()-1));

        int try1 = 0;
        int try1_net = 0;
        int try1_seen = 0;

        while (lh.size() > 0) {
            int l1 = lh.size();
            initPr.p = chooseFromDistribution(conn, "-/" + negtag, session, up,
                                                    lh, -2);
            l1 -= lh.size();
            try1_net += l1;
            if (initPr.p == null) {
                log.info("Exhausted 1st list");
                break;
            }
            String id1 = initPr.p.id;
            String searchid = id1;
            if (try1 == 0  &&  rand.nextInt(10) > 3) {
                searchid = otherbaseid;
            }
            try1++;

            if (session.curator != null) {
                seenPairs1 = UniquePairDao.getPicsSeen(conn, searchid, "id1");
                log.info("got seen0 TODO - cache?");
                // will need this if we add a swap rule:
                //seenPairs2 = UniquePairDao.getPicsSeen(conn, searchid, "id2");
            }

            ListHolder lh2 = PairDao.getPosPairs(conn, orient, session.curator, NEG_COL, id1,
                                                  false, // get left
                                                  null, // hasKwd
                                                  false, // ASC == bottom
                                                  limit, picSet);

            if (up.last == null  ||  up.last.dotCount == 0) {

                int val = lh2.size() + 2;
                for (int i=0;i<lh2.size();i++) {
                    lh2.value_l.set(i, (long)val*val);
                    val--;
                }
            }
            // up.last => use dot-derived histogram
            createDblL(up.last, lh2, null);
            int try2 = 0;
            int try2_net = 0;
            while (lh2.size() > 0) {
                int l2 = lh2.size();
                pr2.p = chooseFromDistribution(conn,
                                                "-/" + negtag + "/" + try2,
                                                session, up, lh2, -2);
                l2 -= lh2.size();
                try2_net += l2;

                if (pr2.p == null) {
                    break;
                }

                try2++;

                String id2 = pr2.p.id;
                log.info("handleNeuralNegScreens GOT " + id1 + " " + id2);
                if (seenPairs1 != null  &&  seenPairs1.contains(id2)) {
                    log.info("skipping curator-seenpair..");
                    skipped_seen++;
                    continue;
                }
                if (id2.equals(id1)) {
                    continue;
                }

                initPr.method = "-" + negtag + "/" + try1;
                pr2.method = "-/" + negtag + "/" + try2;

                List<Screen> scl = new ArrayList<>();

                scl.add(new Screen(session.browserID, 1, "v", id1,
                                                      initPr));
                scl.add(new Screen(session.browserID, 2, "v", id2,
                                                      pr2));
                if (session.curator == null  &&
                        DEBUG_SLEEP_O_KINGLY_BUG == 0) {
                    // make it a litle disquieting
                    long elapsed = System.currentTimeMillis() - up.create;
                    log.info("D0Neg elapsed " + elapsed);
                    if (elapsed < 150) {
                        int disquiet = rand.nextInt(25);// MAGIC
                        log.info("D0Neg disquiet " + disquiet +
                             " on elapsed " + elapsed);
                        try { Thread.sleep(disquiet);
                        } catch (Exception ignore) {}
                    }
                }
                return scl;
            }

        }
        log.info("handleNeuralNegScreens Exhausted for now - random");
        return handleRandomScreens(conn, viewNum, orient, session, up);
    }

    // LIMITED_PAIRTOP = false: [2022_02]
    //      added pairtop N applies for same-arch as well as all
    //      enables using pairtop_nn in any view
    //      almost doubles raw ununiqued lines: v: 2330700 -> 4320292
    //      same calc time in python (v on i9: 17:37, 543M pairs)

    final boolean LIMITED_PAIRTOP = false;

    private List<Screen> handlePhiScreens(Connection conn,
                                            int viewNum, String orient,
                                            Session session, UserProfile up,
                                            String opt,
                                            int n, List<String> ids,
                                            int which)
            throws SQLException {

        long t1 = System.currentTimeMillis();

        log.info("handlePhiScreens: opt " + opt + " view " + viewNum +
                                                    " which " + which);

        if (LIMITED_PAIRTOP) {
            List<Screen> scl = tryNeighborsCache(conn, viewNum, orient, session, ids, which, 0);
            if (scl != null) {
                return scl;
            }
            if (viewNum != 0  &&  viewNum != 1) {  // all or Bill
                log.info("Switching to pairs tbl for viewNum not 0 or 1");
                return handlePrPosScreens(conn, viewNum, orient,
                                                session, up,
                                                "a_d0", ids, which);
            }
        }

        String[] ss = opt.split(" ");
        if (ss.length < 2) {
            log.error("handlePhiScreens: bad opt: " + opt);
            return null;
        }
        if (!"p".equals(ss[0])  &&  !"pt".equals(ss[0])) {
            log.error("handlePhiScreens: bad opt, expected 'p' or 'pt': " +
                                                                opt);
            return null;
        }

        if ("p".equals(ss[0])) {

            log.info("Phi->pairs tbl: " + opt);

            if (ss.length == 2) { // a_, p_
                // maybe already tried
                List<Screen> scl = tryNeighborsCache(conn, viewNum, orient, session, ids, which, 0);
                if (scl != null) {
                    return scl;
                }
                // way too slow
                return handlePrPosScreens(conn, viewNum, orient,
                                                    session, up,
                                                    ss[1], ids, which);
            }
            log.error("handlePhiScreens: bad opt, expected 'p' " +
                                " to have 2 fields w/ a_ or p_: " + opt);
            return null;

        }

        // on with pairtop, ss[0] == "pt", ss[1] == tag

        String tag = ss[1];

        Set<String> picSet = data.getPicSet(viewNum, orient);

        int len = 400;// using 50 in pairtop_nn TODO: rmv depth tinkering?

        if (up.lastCrossings > 2) {
            log.info("handlePhiScreens: doubling len for crossings>2");
            len *= 2;
        }

        String id;
        if (which == -1) {
            if (up.dots1 > up.dots2) {
                id = ids.get(0);
            } else {
                id = ids.get(1);
            }
        } else {
            // 1,2 -> 0,1 for List
            which--;
            if (which < 0  ||  which > ids.size()) {
                which = 0;
            }
            id = ids.get(which);
        }

        log.info("handlePhiScreens/" + orient + ": Try close/1st " + opt +
                 " len " + len + " match id " + id);

        ListHolder lh = null;
        try {
            lh = PairTopDao.getPairtopAsym(conn,
                                        orient, id, false,
                                        tag,
                                        false, "DESC", len, picSet);
        } catch (Exception e) {
            log.error("PairTopDao.getPairtopAsym: " + e);

            List<Screen> ret = null;

            try {
                ret = handleVectorsInParallel(conn, viewNum, orient,
                                                 session, up,
                                                 2, ids,
                                                 "cos.3.5", // (func + dim)
                                                 "poi.1.hist_gss");// l2rFunc
            } catch (Exception ee) {
                log.error("Fallback failed too: " + ee);
                throw e;
            }
            return ret;
        }
        log.info("pairtop: " + tag + " " + id + " size " + lh.size());

        if (up.lastCrossings > 2) {
            log.info("handlePhiScreens: " +
                     "reverse list for crossings>2: " +
                     up.lastCrossings);
            lh = reverseList(lh, up.skew, false);
        }

        List<Screen> ret = get2ndPairtopScreen(conn,
                                                session, up, viewNum, orient,
                                                t1, ids, tag, lh, picSet);
        if (ret != null) {
            return ret;
        }

        // no match w/ right pic - work off left pic

        lh = PairTopDao.getPairtopAsym(conn,
                                        orient, ids.get(0), false,// (pick left)
                                        tag,
                                        false, "DESC", len, picSet);
        if (lh.size() == 0) {
            log.warn("NOTHING - NOTHING!!");
        }
        log.info("Try match with left pic (" + ids.get(0) + ") " +
                 " tag " + tag +
                 " -> len " + lh.size());

        ret = get2ndPairtopScreen(conn, session, up, viewNum, orient,
                                                t1, ids, tag, lh, picSet);
        if (ret != null) {
            return ret;
        }

        log.warn("handlePhiScreens: Exhausted this tag - " + tag +
                        " caller try another, or color vectors t=" +
                        (System.currentTimeMillis() - t1));
        return null;
    }



    private List<Screen> handleColorScreens(Connection conn,
                                            int viewNum, String orient,
                                            Session session, UserProfile up,
                                            String caller,
                                                // caller not used, was a pic1->pic2 func (d0 or color),
                                                //      like l2rfunc below
                                            int n, List<String> ids)
            throws SQLException {

        long t0 = System.currentTimeMillis();

        // new vector-based version

        try {

            // pgvector func can be chosen for Sigma1
            //      via Phob->Parallel Descent->

            String func = "cos";
            double dice = rand.nextDouble();
            if (dice < 0.5) {
                func = "poi";// poincare;but pgvector is only cartesian for now
            }

            // histograms are treated as symmetric color distances

            int dim = 1984;
            dice = rand.nextDouble();
            if (dice < 0.2) {
                dim = 1984;
            } else if (dice < 0.6) {
                dim = 256;
            } else {
                dim = 1728;
            }

            List<Screen> ret = handleVectorsInParallel(conn, viewNum, orient,
                                            session, up,
                                            n, ids,
                                            (func + "." + "1." + dim), //
                                            null);// l2rFunc
            if (ret != null) {
                log.info("VEXX ok " + (func + dim));
                return ret;
            }

        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException(e);
        }
        log.warn("handleColorScreens: just can't: going linear");

        return linearScreenSearch(conn, viewNum, orient, session, n);
    }

    private List<Screen> OLDhandleColorScreens(Connection conn,
                                            int viewNum, String orient,
                                            Session session, UserProfile up,
                                            int option,
                                            int n, List<String> ids)
            throws SQLException {

        long t0 = System.currentTimeMillis();

        // old version, no pairtops loaded by now so delete
        //      (or repurpose the logic)
        //      when committed to the new .. TODO

        // make these different since initOppColumn is reused
        // for first try in the hybrid case
        String initOppColumn = "b9";
        String secondColorCol = "b8";

        double ratio = -1.0;
        if (up.last != null) {
            // MAGIC
            ratio = (double) Math.abs(up.last.userTime - up.avgUserTime)
                                         / up.maxDeltaUserTime;
        }
        log.info("colorscreens ratio " + ratio);

        if (up.last != null) {
            if (up.longerThanSecondLastUserTime) {
                if (ratio > 0.8) {
                    initOppColumn = "b9";
                    secondColorCol = "b8";
                } else if (ratio > 0.7) {
                    initOppColumn = "b8";
                    secondColorCol = "b9";
                } else if (ratio > 0.6) {
                    initOppColumn = "b7";
                    secondColorCol = "b8";
                } else if (ratio > 0.5) {
                    initOppColumn = "b7";
                    secondColorCol = "b9";
                } else if (ratio > 0.4) {
                    initOppColumn = "b6";
                    secondColorCol = "b7";
                } else if (ratio > 0.35) {
                    initOppColumn = "b6";
                    secondColorCol = "b5";
                } else if (ratio > 0.3) {
                    initOppColumn = "b5";
                    secondColorCol = "b6";
                } else if (ratio > 0.26) {
                    initOppColumn = "b0";
                    secondColorCol = "b4";
                } else if (ratio > 0.24) {
                    initOppColumn = "b1";
                    secondColorCol = "b5";
                } else if (ratio > 0.22) {
                    initOppColumn = "b1";
                    secondColorCol = "b6";
                } else if (ratio > 0.2) {
                    initOppColumn = "b5";
                    secondColorCol = "b8";
                } else if (ratio > 0.18) {
                    initOppColumn = "b2";
                    secondColorCol = "b4";
                } else if (ratio > 0.16) {
                    initOppColumn = "b4";
                    secondColorCol = "b7";
                } else if (ratio > 0.1) {
                    initOppColumn = "b5";
                    secondColorCol = "b9";
                } else {
                    initOppColumn = "b4";
                    secondColorCol = "b5";
                }
            } else {
                if (ratio > 0.8) {
                    initOppColumn = "b1";
                    secondColorCol = "b2";
                } else if (ratio > 0.7) {
                    initOppColumn = "b2";
                    secondColorCol = "b4";
                } else if (ratio > 0.6) {
                    initOppColumn = "b2";
                    secondColorCol = "b4";
                } else if (ratio > 0.5) {
                    initOppColumn = "b0";
                    secondColorCol = "b4"; // accelerate
                } else if (ratio > 0.4) {
                    initOppColumn = "b5";
                    secondColorCol = "b6";
                } else if (ratio > 0.35) {
                    initOppColumn = "b6";
                    secondColorCol = "b7";
                } else if (ratio > 0.3) {
                    initOppColumn = "b0";
                    secondColorCol = "b8";
                } else if (ratio > 0.26) {
                    initOppColumn = "b1";
                    secondColorCol = "b9";
                } else if (ratio > 0.24) {
                    initOppColumn = "b9";
                    secondColorCol = "b5";
                } else if (ratio > 0.22) {
                    initOppColumn = "b8";
                    secondColorCol = "b6";
                } else if (ratio > 0.2) {
                    initOppColumn = "b7";
                    secondColorCol = "b4";
                } else if (ratio > 0.18) {
                    initOppColumn = "b6";
                    secondColorCol = "b4";
                } else if (ratio > 0.16) {
                    initOppColumn = "b5";
                    secondColorCol = "b4";
                } else if (ratio > 0.14) {
                    initOppColumn = "b1";
                    secondColorCol = "b9";
                } else if (ratio > 0.13) {
                    initOppColumn = "b2";
                    secondColorCol = "b6";
                } else if (ratio > 0.12) {
                    initOppColumn = "b1";
                    secondColorCol = "b4";
                } else if (ratio > 0.11) {
                    initOppColumn = "b0";
                    secondColorCol = "b7";
                } else if (ratio > 0.1) {
                    initOppColumn = "b8";
                    secondColorCol = "b5";
                } else {
                    initOppColumn = "b0";
                    secondColorCol = "b9";
                }
            }
        }

        if (option == -1) { // TODO - switch to approach that uses weights
            log.info("Color, d0");
            secondColorCol = "a_d0";
        }

        if (ratio < 0.0) {
            log.warn("ADJUST ratio to 1.0");
            ratio = 1.0;
        }
        log.info("Color " + (option == 0 ? "opposite" : "same") +
                  " " + ratio + " " +
                  initOppColumn + " " + secondColorCol);

        int skipped_seen = 0;// for LOCAL

        final String try_ord[] = { "b9", "b8", "b7", "b6", "b5",
                             "b4", "b2", "b1", "b0"  };
        String try_col[] = new String[try_ord.length];
        try_col[0] = initOppColumn;
        int next_col = 1;
        for (int i=0;i<try_ord.length && next_col<try_col.length;i++) {
            if (try_ord[i].equals(initOppColumn)) {
                continue;
            }
            try_col[next_col++] = try_ord[i];
        }

        PictureResponse pr = new PictureResponse();

        List<String> picList = data.getPicList(viewNum, orient);
        int effectiveSize = picList.size();
        int seen = 0;
        if (up.last != null) {
            seen = up.last.orderInSession;
        } else {
            seen = ShowingPairDao.countShowings(conn, session.browserID, orient);
        }

        int len = 400;
        if (seen > 0.8 * effectiveSize) { // MAGIC
            len = 1600;
        } else if (seen > 0.6 * effectiveSize) {
            len = 800;
        }
        log.info("effective " + effectiveSize + " seen " + seen + " len " + len);

        Set<String> picSet = data.getPicSet(viewNum, orient);

        int failedWithNoTry = 0;

        // fall back on far-from-left one

        failedWithNoTry = 0;

        int depth = 200;
        if (orient != null) {
            depth *= 2;
        }
        if (option == 0) { // opposite
            depth *= 2;
        }
        if (seen > 0.8 * effectiveSize) { // MAGIC
            depth += depth / 2;
        }

        SeenIds seenIds = getSeen(conn, session);

        for (String column : try_col) {
            ListHolder lh = null;
            if ("np".equals(column)) {
                log.info("Try far d0 on id1: " + ids.get(1));
                lh = PairDao.getPairs(conn, ids.get(1), orient, "n_d0",
                                            true, "ASC", len, null, picSet);
            } else {
                log.info("Try far " + column + " len " + len +
                         " depth " + depth);
                lh = PairTopDao.getPairtopSymm(conn, "col",
                                                orient, ids.get(0), column,
                                                false, "DESC", len, picSet);
            }
            if (lh != null) {
                log.info("Got " + lh.size());
            } else {
                log.info("Got null");
            }

            for (String id2 : lh.id2_l) {

                if (seenIds.contains(id2)) {
                    continue;
                }

                pr.p = PictureDao.getPictureById(conn, id2);
                if (pr.p == null) {
                    log.info("No version for unseen seq " + id2);
                    continue;
                }

                pr.method = "sopp2/" + column;

                List<Screen> scl;
                if (option < 0) {
                    scl = get2ndPairtopScreen(conn,
                                                    session, up,
                                                    viewNum, orient,
                                                    t0, null, /*tag*/
                                                    pr, picSet);

                } else if (up.last.orderInSession % 2 == 0) {

                    scl = getColorComboScreensPic2(conn,
                                                viewNum, orient,
                                                session, up,
                                                option, n, depth,
                                                secondColorCol, column, pr);

                } else {

                    scl = getColorScreensPic2(conn,
                                                viewNum, orient,
                                                session, up,
                                                option, n, depth,
                                                secondColorCol, pr);
                }

                if (scl == null) {

                    failedWithNoTry++;
                    if (failedWithNoTry > 5) { // MAGIC
                        log.info("Giving up on lh");
                        break;
                    }

                } else if (scl.size() == 0) {
                    failedWithNoTry = 0;
                } else {
                    return scl;
                }
                depth *= 1.25;// MAGIC
            }

            if (failedWithNoTry > 5) { // MAGIC
                log.info("Giving up after not trying");
                break;
            }
            len *= 1.25; // MAGIC
        }
        log.info("OLDhandleColorScreens Failed - going linear");

        return linearScreenSearch(conn, viewNum, orient, session, n);
    }

    private List<Screen> checkPair(Connection conn, Session session,
                                        PictureResponse[] prs)
            throws SQLException {

        if (prs == null  ||  prs[0] == null  ||  prs[1] == null) {
            log.error("checkPair: null PictureResponse");
            return null;
        }

        if (prs[0].p.id.equals(prs[1].p.id)) {
            log.info("Same pic on both sides, retry " + prs[0].p.id);
            return null;
        }

        if (session.curator != null  &&
                ApprovalDao.settled(conn, prs[0].p.id, prs[1].p.id)) {
            return null;
        }
        if (ShowingPairDao.checkSeen(conn, session.browserID, prs[0].p.id, prs[1].p.id)) {
            return null;
        }

        // return the pair

        List<Screen> scl = new ArrayList<>();

        scl.add(new Screen(session.browserID, 1, "v", prs[0].p.id,
                                                        prs[0]));
        scl.add(new Screen(session.browserID, 2, "v", prs[1].p.id,
                                                        prs[1]));
        return scl;
    }

    /*
    **  oneStageVectorMatch - match each pic independently
    **                          from its list.
    */
    private List<Screen> oneStageVectorMatch(Connection conn,
                                int viewNum, String orient,
                                Session session,
                                ShowingPair last,
                                ListHolder[] lhs,
                                String[] funcDims)
            throws SQLException {

        PictureResponse prs[] = new PictureResponse[2];

        StringBuilder sb = new StringBuilder();

        for (int ct=0; ct<10; ct++) {

            // check input

            sb.setLength(0);
            int t = 0;
            for (int i=0; i<2; i++) {
                sb.append(i).append(" ").append(lhs[i].size()).append("   ");
                if (lhs[i].size() == 0) t++;
            }
            log.info("Lists: " + t + " on ct " + ct + " sizes\n" + sb.toString());
            if (t > 0) {
                break;
            }
            // use input: get a pair by independent matches
            for (int i=0; i<2; i++) {

                prs[i] = tryLhP(conn, session, orient, last, lhs[i]);

                if (prs[i] == null) {
                    log.warn("oneStageVectorMatch giving up on ct=" + ct + " i=" + i);
                    return null;
                }
                prs[i].method = funcDims[i] + (i==0 ? "(ll)" : "(rr)") + ct;
            }
            List<Screen> scl = checkPair(conn, session, prs);

            if (scl != null) {
                return scl;
            }
        }

        log.warn("oneStageVectorMatch giving up on ct=10");
        return null;
    }

    private Picture[] picsFromIds(Connection conn, List<String> ids)
            throws SQLException {

        Picture[] pics = new Picture[ids.size()];

        for (int i=0;i<ids.size();i++) {
            String id2 = ids.get(i);
            pics[i] = PictureDao.getPictureById(conn, id2);
        }

        return pics;
    }

    /*
    **  funcHow()
    **      cos.2.nnl_7 -> cn7
    **      poi.2.mob_40 -> dm40  [d/distance]
    */

    private String funcHow(String func) {

        String how = "";

        String[] ss = func.split("\\.");
        if (ss != null  &&  ss.length == 3) {

            String f = ss[0].substring(0, 1);
            if ("p".equals(f)) f = "d"; // poi->distance

            String model = ss[2].substring(0, 1);

            String dim = "";

            String[] sss = ss[2].split("_");
            if (sss.length == 2) {
                dim = sss[1];
            }

            how += f + model + dim;
        }
        return how;
    }

    /*
    **  twoStageVectorMatch - match one pic to a prev pic,
    **      then use the vector of the choice to pick the
    **      2nd pic from a list of neighbors of the other prev.
    */
    private List<Screen> twoStageVectorMatch(Connection conn,
                                int viewNum, String orient,
                                Session session,
                                ShowingPair last,
                                ListHolder[] lhs, // closest matches for id1, id2
                                String[] funcDims, // selects for each side
                                String func)  // second select side1->side2
            throws Exception {

        // cache lhs[1] pr.picture for .vectors (right-hand pics)

        Picture[] pics = picsFromIds(conn, lhs[1].id2_l);

        PictureResponse prs[] = new PictureResponse[2];

        for (int ct=0; ct<10; ct++) {

            // get 1st pic by simple closeness (weighted-random)

            prs[0] = tryLhP(conn, session, orient, last, lhs[0]);

            if (prs[0] == null) {
                log.warn("giving up at ct=" + ct);
                return null;
            }

            // l->r using pgvector on r list

            int screen = 2;
            String id1 = prs[0].p.id;

            ListHolder lh2 = PictureDao.orderByVectors(conn,
                                            screen, id1,
                                            func,
                                            lhs[1]);
/*
personal-pair ml
            if (prs[0].p.vec_l == null) {
                // recalc may be in progress
                //      TODO calc vectors when importing
                log.warn("twoStageVectorMatch: vec_l[eft] NULL for Picture " + prs[0].p.id);
                return null;
            }

            ListHolder lh2 = vectorDistance(screen, func, prs[0].p.vec_l, pics);
*/
            if (lh2 == null) {
                return null;
            }

            prs[1] = tryLhP(conn, session, orient, last, lh2);
            if (prs[1] == null) {
                log.warn("giving up on prs[1] at ct=" + ct);
                return null;
            }

            prs[0].method = funcHow(funcDims[0]) + "(ll)";
            prs[1].method = funcHow(funcDims[1]) + "(r)." + funcHow(func) + "(l)";

            List<Screen> scl = checkPair(conn, session, prs);

            if (scl != null) {
                return scl;
            }
        }
        log.warn("twoStageVectorMatch giving up on ct=10");
        return null;
    }


    private void checkScreens(String caller, int screenId1, int screenId2) {

        log.info("checkScreens(" + caller + ", " + screenId1 + ", " + screenId2 + ")");

        if (screenId1 == 0  &&  screenId2 == 0) {
            log.error(caller + ": Bad screenid1,2 are 0");
            new Exception("WTF ").printStackTrace();
            return;
        }

        StringBuilder sb = new StringBuilder();
        if (screenId1 != 1  &&  screenId1 != 2) {
            sb.append(caller + ": Bad screenid1: " + screenId1);
        }
        if (screenId2 != 1  &&  screenId2 != 2) {
            if (sb.length() == 0) {
                sb.append(caller + ": Bad screenid2: " + screenId2);
            } else {
                sb.append(", Bad screenid2: " + screenId2);
            }
            if (sb.length() > 0) {
                log.error(sb.toString());
            }
        }
    }

    private List<Screen> dotMappedNNs(Connection conn,
                                      int viewNum, String orient,
                                      String kwdCoder,
                                      Session session, UserProfile up,
                                      List<String> ids,
                                      int screenId1, int screenId2)
            throws SQLException {

        if (!PairDao.hasTable(orient)  &&  !PairTopDao.hasTable("nn", orient)) {
            log.error("Doing dotMap w/out NN tables");
            return null;
        }

        checkScreens("dotMappedNNs", screenId1, screenId2);

        List<String> tagL = data.getPairtopNNTags("v".equals(orient));
        int option = up.dotMap(tagL.size());
        String tag = tagL.get(option);

        log.info("dotMapNN: tags: " + tagL.size() +
                            " option " + option + "/" + tag);

        return handlePhiScreens(conn, viewNum, orient, session, up,
                                      "pt " + tag, 2, ids, -1);
    }


    private List<Screen> handleRandomPairtopNNFiles(Connection conn,
                                            int viewNum, String orient,
                                            Session session, UserProfile up,
                                            String[] pt_fname_substr,
                                            List<String> ids)
            throws SQLException {

        String method = "rpt:" + pt_fname_substr.length;

        Set<String> picSet = data.getPicSet(viewNum, orient);

        log.info("handleRandomPairtopNNFiles: n: " +
                pt_fname_substr.length + " skew " + up.skew + " " +
                " n " + (up.last == null ? "na" : up.last.orderInSession));

        SeenIds seenIds = getSeen(conn, session);

        ListHolder lh1 = PairTopDao.randomPairtopAsymByFileStr(conn, orient,
                                                ids.get(1), true, // first
                                                pt_fname_substr, picSet);

        if (lh1 == null) {
            log.info("PairTopDao.randomPairtopAsymByFileStr null on " + pt_fname_substr);
            return null;
        }

        log.info("handleRandomPairtopNNFiles: lh1: " + lh1.size());

        if (up.lastCrossings > 2) {
            log.info("handleRandomPairtopNNFiles: " +
                     "reverse list for crossings>2: " +
                     up.lastCrossings);
            lh1 = reverseList(lh1, up.skew, false);
        }

        Set<String> seenPairs1 = null;// for left/new right
        int skipped_seen = 0;
        int try1 = 0;
        int try2 = 0;
        while (lh1.size() > 0) {
            PictureResponse initPr = tryLhP(conn, session, orient, up.last, lh1);
            if (initPr == null) {
                break;
            }
            String id1 = initPr.p.id;
            try1++;
            if (session.curator != null) {
                seenPairs1 = UniquePairDao.getPicsSeen(conn, id1, "id1");
            }

            ListHolder lh2 = PairTopDao.randomPairtopAsymByFileStr(conn, orient,
                                                id1, false, // first
                                                pt_fname_substr, picSet);

            log.info("lh1/2 sizes: " + lh1.size() + " " +
                                       lh2.size());
            if (lh2.size() == 0) {
                break;
            }

            while (lh2.size() > 0) {
                PictureResponse pr2 = tryLhP(conn, session, orient, up.last, lh2);
                if (pr2 == null) {
                    log.info("lh2 done");
                    break;
                }
                try2++;
                if (seenPairs1 != null  &&  seenPairs1.contains(pr2.p.id)) {
                    int ix = lh2.id2_l.indexOf(pr2.p.id);
                    lh2.id2_l.remove(ix);
                    lh2.value_l.remove(ix);
                    lh2.dbl_l.remove(ix);
                    skipped_seen++;
                    continue;
                }
                initPr.method = "rpt" + method + "/" + try1;
                pr2.method = "rpT" + method + "/" + try2;
                log.info("handleIntersectPairtopNNTags: " +
                     " skipseen " + skipped_seen +
                     " totseen " +
                       (seenPairs1 == null ? "na" : seenPairs1.size()));
                List<Screen> scl = new ArrayList<>();

                scl.add(new Screen(session.browserID, 1, "v",
                                          initPr.p.id, initPr));
                scl.add(new Screen(session.browserID, 2, "v",
                                          pr2.p.id, pr2));
                return scl;
            }
        }

        // first pass: try for unanimous

        log.info("handleIntersectPairtopNNTags: Exhausted list for 2order pic " +
                 ids.get(1) + ", fall back to a_d0");

        List<Screen> scl = tryNeighborsCache(conn, viewNum, orient, session, ids, -1, 0);
        if (scl != null) {
            return scl;
        }
        return handlePrPosScreens(conn, viewNum, orient, session, up,
                                                                "a_d0", ids, -1);
    }

    /*
    **  doSigmaMixed - initially a fallback for doSigmaPairNet (no pairtops)
    **
    **      Goal: 5 interesting different options
    **              Use a 'strong' first
    */
    private List<Screen> doSigmaMixed(Connection conn,
                                            int viewNum, String orient,
                                            Session session, UserProfile up,
                                            int sigma, int n, List<String> ids)
                throws SQLException {

        String func1 = null;
        String func2 = null;

        switch (sigma) {
            case 1:
                func1 = "poi.2.vgg16_4";
                // no func2
                break;
            case 2:
                func1 = "poi.2.vgg16_16";
                func2 = "cos.1.256";
                break;
            case 3:
                func1 = "poi.2.vgg16_64";
                func2 = "cos.1.256";
                break;
            case 4:
                func1 = "poi.2.vgg16_256";
                func2 = "cos.1.256";
                break;
            case 5:
                func1 = "poi.2.nnl_252";
                func2 = "cos.1.256";
                break;
        }

        try {
            return handleVectorsInParallel(conn, viewNum, orient,
                                            session, up,
                                            2, ids,
                                            func1, func2);
        } catch (SQLException sqe) {
            throw sqe;
        } catch (Exception e) {
            throw new SQLException("doSigmaMixed: wrapping e", e);
        }
    }

    private List<Screen> doSigmaPairNet(Connection conn,
                                            int viewNum, String orient,
                                            Session session, UserProfile up,
                                            int sigma, int n, List<String> ids)
                throws SQLException {

        // copy for shuffle

        String siggo = data.getSigma("v".equals(orient), sigma);

        if (siggo == null) {
            log.warn("doSigmaPairNet: fallback since no phogit.properties sigma cmd " +
                        "(or missing table support) sigma=" + sigma + " " + orient);

            return doSigmaMixed(conn, viewNum, orient, session, up,
                                        sigma, n, ids);
        }

        log.info("doSigmaPairNet: " + sigma + orient + ": " + siggo);

        String sig_fields[] = siggo.split(" ");
        String cmd = sig_fields[0].trim();

        if ("p_avg".equals(cmd)) {
            // pairs_[vh]
            List<Screen> scl = tryNeighborsCache(conn, viewNum, orient, session, ids, -1, 0);
            if (scl != null) {
                return scl;
            }
            return handlePrPosScreens(conn, viewNum, orient, session, up,
                                        sig_fields[1].trim(),
                                        ids, -1);
        } else if ("pt_group".equals(cmd)) {

            // pairtop_nn_[vh]

            // select from grp

            List<String> tags = new ArrayList<>(); // TODO: precalc in data
            for (int i=1;i<sig_fields.length;i++) {
                tags.add(sig_fields[i]);
            }
            java.util.Collections.shuffle(tags);

            for (String tag : tags) {

                log.info(tags.size() > 1 ? "RAND " : "" +
                                "pairtop tag: " + tag);
                List<Screen> ret = handlePhiScreens(conn, viewNum, orient, session, up,
                                                            "pt " + tag, n, ids, -1);
                if (ret != null) {
                    return ret;
                }
            }
        } else if ("pt_match".equals(cmd)) {

            // pairtop_nn_[vh]

            if (sig_fields[1].trim().equals("fname")) {

                String[] pt_fname_substr = new String[sig_fields.length-2];
                for (int i=2;i<sig_fields.length;i++) {
                    pt_fname_substr[i-2] = sig_fields[i].trim();
                }
                return handleRandomPairtopNNFiles(conn, viewNum, orient, session, up,
                                                      pt_fname_substr, ids);
            } else {

                if (sig_fields.length == 2) {
                    log.info("match: single tag " + sig_fields[1]);
                    List<Screen> ret = handlePhiScreens(conn, viewNum, orient, session, up,
                                            "pt " + sig_fields[1], n, ids, -1);
                    if (ret != null) {
                        return ret;
                    }
                } else {

                    // iterate tags since intersect too slow

                    for (int i=1;i<sig_fields.length;i++) {
                        List<Screen> ret = handlePhiScreens(conn, viewNum, orient, session, up,
                                            "pt " + sig_fields[i], n, ids, -1);
                        if (ret != null) {
                            return ret;
                        }
                    }
                    /* too many misses on intersect
                    String[] pt_tags = new String[sig_fields.length-1];
                    for (int i=1;i<sig_fields.length;i++) {
                        pt_tags[i-1] = sig_fields[i].trim();
                    }
                    return handleIntersectPairtopNNTags(conn, viewNum, orient, session, up,
                                                        pt_tags, ids);
                    */

                }
            }
        }

        log.info("Sigma: " + sigma + ": nothing on " + siggo);

        return null;
    }

    private Pair getWhich(List<Pair> pairList, int which) {

        log.info("getWhich size " + pairList.size() + " which_side: " + which);

        if (which < 1) {
            log.error("getWhich: using first on " + which);
            return pairList.get(0);
        } else if (which > 2) {
            log.error("getWhich: using last on " + which);
            return pairList.get(pairList.size()-1);
        }

        if (which == 1) {
            for (Pair pr : pairList) {
                if (pr.values[0] > pr.values[1]) {
                    return pr;
                }
            }
        } else {
            for (Pair pr : pairList) {
                if (pr.values[1] > pr.values[0]) {
                    return pr;
                }
            }
        }
        log.warn("getWhich: no match, using last");
        return pairList.get(pairList.size()-1);
    }


    // TODO - add a bad vectors fallback

    private List<Screen> tryD0BadCache(Connection conn,
                                       int viewNum, String orient,
                                       Session session)
            throws SQLException {

        log.info("tryD0BadCache " + session.browserID);

        // TODO - periodically sweep to remove long-idle cache/browser
        D0BadCache cache = d0BadCache.get(session.browserID);
        if (cache == null) {
            log.info("tryD0BadCache no entry, cache size " + d0BadCache.size() +
                        " browser " + session.browserID);
            return null;
        }

        if (cache.viewNum != viewNum  ||  !cache.orient.equals(orient)) {
            log.info("tryD0BadCache cache new view/orient " + session.browserID);
            d0BadCache.remove(session.browserID);
            return null;
        }

        log.info("tryD0BadCache cache size: " + d0BadCache.size() +
                        " browserID " + session.browserID + " list size: " +
                        cache.list.size());

        if (cache.list.size() == 0) {
            return null;
        }

        SeenIds seenIds = getSeen(conn, session);

        Pair p = null;
        while (cache.list.size() > 0) {
            p = cache.list.remove(0);
            if (!seenIds.contains(p.id1)  &&  !seenIds.contains(p.id2)) {
                break;
            }
            p = null;
        }
        if (p == null) {
            log.info("tryD0BadCache cache list is 0");
            return null;
        }

        log.info("tryD0BadCachelist got " + p.id1 + " " + p.id2 +
                        " cache " + cache.list.size());

        PictureResponse pr1 = new PictureResponse();
        pr1.p = PictureDao.getPictureById(conn, p.id1);
        PictureResponse pr2 = new PictureResponse();
        pr2.p = PictureDao.getPictureById(conn, p.id2);

        if (pr1.p == null  ||  pr2.p == null) {
            log.error("tryD0BadCache err/ pic not there");
            return null;
        }
        if (pr1.p.vertical != pr2.p.vertical) {
            log.error("tryD0BadCache err/ verticals !=");
            return null;
        }
        if (pr1.p.vertical != "v".equals(orient)) {
            log.error("tryD0BadCache err/ not oriented: " + orient);
            return null;
        }

        pr1.method = "d0bc";
        pr2.method = "d0bc";
        List<Screen> scl = new ArrayList<>();

        scl.add(new Screen(session.browserID,
                                   1, "v", p.id1,
                                   pr1));
        scl.add(new Screen(session.browserID,
                                   2, "v", p.id2,
                                   pr2));
        return scl;
    }

    private List<Screen> flipEmDanno(Connection conn,
                                        int viewNum, String orient,
                                        Session session,
                                        List<String> ids)
            throws SQLException {

        String id1 = ids.get(1);
        String id2 = ids.get(0);
        ApprovedPair ap = ApprovalDao.getApprovedPair(conn, -1, id1, id2);
        if (ap == null) {
            log.info("flipEmDanno: no app pair: " + id1 + ", " + id2);
        } else {
            log.info("flipEmDanno: " + ap);
        }
        PictureResponse pr1 = new PictureResponse();
        pr1.p = PictureDao.getPictureById(conn, id1);
        PictureResponse pr2 = new PictureResponse();
        pr2.p = PictureDao.getPictureById(conn, id2);

        pr1.method = "flip";
        pr2.method = "flip";
        List<Screen> ret = new ArrayList<>();
        ret.add(new Screen(session.browserID, 1, "v", id1, pr1));
        ret.add(new Screen(session.browserID, 2, "v", id2, pr2));
        return ret;
    }

    // 1 - histograms
    // 2 - imagenets
    // 3 - personal pairs (_left, _right)

    private final static int[][] typedims =
        {
            { 1,  256 }, // gss
            { 1, 1728 }, // rgb12
            { 1, 1984 }, // gss+rgb12

            // unique mapping, e.g. only one type 2 with size 64

            // vgg16
            { 2,  512 }, // avgd 7x7
            { 2,  256 }, // folded
            { 2,  128 },
            { 2,   64 },
            { 2,   32 },
            { 2,   16 },
            { 2,    4 },
            { 2,    2 },

            { 2, 1008 },  // nnl   NASNetLarge 4-folded
            { 2,  252 },
            { 2,   42 },
            { 2,   21 },
            { 2,    7 },
            { 2,    3 },

            { 2, 1024 },  // dense_1024 DenseNet121

            { 2, 1280 },  // mob_1280   MobileNetV2
            { 2, 40 },    // mob_40   MobileNetV2
            { 2, 10 },    // mob_10   MobileNetV2
            { 2, 5 },     // mob_5   MobileNetV2

            // personal pair ml vecs
            { 3,    2 },
            { 3,    3 },
            { 3,    5 },
            { 3,   12 }
        };

    private int[] chooseTypeDimension(UserProfile up) {
        int i = up.dotMap(typedims.length);
        return typedims[i];
    }

    private int[] chooseTypeDimension(UserProfile up, int type) {
        int i = up.dotMap(typedims.length);
        while (typedims[i][0] != type) {
            i++;
            if (i == typedims.length) {
                i = 0;
            }
        }
        return typedims[i];
    }

    /*
    **  gestureStroke - project vectors left, right according
    **          to direction of stroke.
    **      If it's a horizontal stroke within the two centers,
    **          scale from each pic inward (can be parallel)
    **          - i.e. direction of stroke isn't used.
    **
    **  TODO: stacked pics - only thinking side-by-side here
    */

    private List<Screen> gestureStroke(Connection conn,
                                        int viewNum, String orient,
                                        Session session, UserProfile up,
                                        List<String> ids)
            throws SQLException {

        ShowingPair last = up.last;

        // gesture being straight, angleHist makes no sense
        //      TODO see how these compare, maybe make swappable
        // null to use vector pair distances
        int[] histo1 = last.dotHistory.distHist;
        int[] histo2 = last.dotHistory.velocityHist;

        // fac(tor) of 2.0 projects by the whole 2nd-1st diff
        //    varying 1..? <1 reverses the sign of the diff
        //    this can go negative, whereas numbers in averaged
        //      imagenet vecs are seen to be positive, with max
        //      increasing with the number of dimensions
        //          vgg16_512   e.g. 4.384392
        // last.dotVecAng l->r:
        //      0 is at 3 o'clock
        //      up is negative, down positive to 180

        float fac1 = 2.0f;
        float fac2 = 2.0f;

        String method = null;

        if (last.dotDist < last.dotHistory.centersDist  &&
            ((last.dotVecAng < 10  &&  last.dotVecAng > -10)  ||
            (last.dotVecAng < -170  &&  last.dotVecAng > 170))) {

            method = "in";

            // scale each toward the other
            fac1 = (float) last.dotHistory.dots1 / last.dotCount;
            fac2 = (float) last.dotHistory.dots2 / last.dotCount;

            // smaller dotDist==closer
            // normalize to fraction of screen
            float distFrac = 1.0f - (float) last.dotDist / last.dotHistory.centersDist;
            if (distFrac < 1.0f) {
                distFrac *= -1.0f;
            }

            fac1 *= distFrac;
            fac2 *= distFrac;

        } else if (last.dotVecAng > 10  &&  last.dotVecAng < 90) {

            method = "lr/down";

            // l->r, down [closer]
            fac1 = 2.0f - 0.01f * (last.dotVecAng - 10);
            fac2 = fac1;
        } else if (last.dotVecAng > 90  &&  last.dotVecAng < 170) {

            method = "rl/down";

            // r->l, down [closer]
            fac1 = 2.0f - 0.01f * (180 - last.dotVecAng);
            fac2 = fac1;

        } else if (last.dotVecAng < -10  &&  last.dotVecAng > -90) {

            method = "lr/up";

            // l->r, up  [further.. more wraps etc]
            fac1 = 2.0f - 4.0f * (20.0f + last.dotVecAng) / 80.0f;
            fac2 = fac1;

        } else if (last.dotVecAng < -90  &&  last.dotVecAng > -170) {

            method = "rl/up";

            // r->l, up  [further]
            fac1 = 2.0f + 4.0f * (90.0f + last.dotVecAng) / 80.0f;
            fac2 = fac1;

        } else {

            method = "flat";

            // 'flat'
            fac1 = 2.0f;
            fac2 = 2.0f;
        }

        Picture p1 = PictureDao.getPictureById(conn, last.id1);
        Picture p2 = PictureDao.getPictureById(conn, last.id2);

        Boolean left2right = null;
        Picture start = null;
        Picture end = null;

        String func = "cos";
        //String func = "poi";

        if (last.dotStartScreen == 1  &&  last.dotEndScreen == 2) {

            log.info("gestureStroke l->r");
            func = "cos";
            //func = "poi";

            // "in" overrides below
            left2right = true;
            start = p1;
            end = p2;

        } else if (last.dotStartScreen == 2  &&  last.dotEndScreen == 1) {

            log.info("gestureStroke r->l");
            func = "poi";
            //func = "cos";

            // "in" overrides below
            left2right = false;
            start = p2;
            end = p1;

        } else {
            log.error("gestureStroke: unexpected start/end screens: " +
                        last.dotStartScreen + "/" + last.dotEndScreen);
            return null;
        }
        if ("in".equals(method)) {
            left2right = null;
            start = p1;
            end = p2;
        }

        Set<String> picSet = data.getPicSet(viewNum, orient);

        // indexed vgg16_N:   512,64,16,4,2
        // columns loaded: vgg16_4, dense_4, mob_5, nnl_7
        //      pr=# select avg(nnl_7) from pr.picture;
        //  [0.12249159,0.11945441,0.13432811,0.1279473,
        //   0.11732886,0.117889486,0.116945505]
        final int vec_n = 7;
        final String column = "nnl_7";

        // calc end - start

        int negs = 0;
        int gt = 0;

/*
        log.info("diff:\n" + Arrays.toString(end.vgg16_4) + "\n" +
                             Arrays.toString(start.vgg16_4) + "\n" +
                             Arrays.toString(vec1));
*/

        // get first pic and list for 2nd

        PictureResponse pr = null;
        ListHolder lh2 = null;

        if ("in".equals(method)) {

            // calc diff

            float[] l = new float[vec_n];
            float[] r = new float[vec_n];

            for (int i=0;i<vec_n;i++) {

                //diff[i] = end.vgg16_4[i] - start.vgg16_4[i];
                float diff = p2.nnl_7[i] - p1.nnl_7[i];

                l[i] = p1.nnl_7[i] + fac1 * diff;
                r[i] = p2.nnl_7[i] - fac2 * diff;

                if (l[i] < 0.0f) {
                    negs++;
                    l[i] *= -1.0f;
                }
                while (l[i] > 0.2f) {
                    gt++;
                    l[i] *= 0.5f;
                }

                if (r[i] < 0.0f) {
                    negs++;
                    r[i] *= -1.0f;
                }
                while (r[i] > 0.2f) {
                    gt++;
                    r[i] *= 0.5f;
                }
            }

            log.info("VV interpolation negs/halvings: " + negs + "/" + gt + " diff:\n" +
                            "VV " + Arrays.toString(p1.nnl_7) + "\n" +
                            "VV " + Arrays.toString(p2.nnl_7) + "\n" +
                            "VV " + Arrays.toString(l) + "\n" +
                            "VV " + Arrays.toString(r));

            ListHolder lh = PictureDao.matchVector(conn, orient, l,
                                                    null, // archives TODO per-view
                                                    func, column, 51, picSet);
            if (lh == null  ||  lh.size() == 0) {
                return null;
            }
            pr = tryLhP(conn, session, orient, histo1, lh);
            if (pr == null) {
                log.info("matchVector pictureresponse 1 null");
                return null;
            }
            lh2 = PictureDao.matchVector(conn, orient, r,
                                                    null, // archives TODO per-view
                                                    func, column, 51, picSet);

        } else {

            // project sideways

            float[] vec1 = new float[vec_n];
            for (int i=0;i<vec_n;i++) {
                //vec1[i] = end.vgg16_4[i] - start.vgg16_4[i];
                vec1[i] = (fac1 * end.nnl_7[i]) - start.nnl_7[i];
                if (vec1[i] < 0.0f) {
                    negs++;
                    vec1[i] *= -1.0f;
                }
                while (vec1[i] > 0.2f) {
                    gt++;
                    vec1[i] *= 0.5f;
                }
            }

            log.info("VV projection negs/halvings: " + negs + "/" + gt + " diff:\n" +
                            "VV " + Arrays.toString(end.nnl_7) + "\n" +
                            "VV " + Arrays.toString(start.nnl_7) + "\n" +
                            "VV " + Arrays.toString(vec1));

            ListHolder lh = PictureDao.matchVector(conn, orient, vec1,
                                                    null, // archives TODO per-view
                                                    func, column, 51, picSet);

            log.info("matchVector - 1st lh got " + (lh == null ? "zilch" : lh.size()));

            if (lh == null  ||  lh.size() == 0) {
                return null;
            }

            pr = tryLhP(conn, session, orient, histo1, lh);
            if (pr == null) {
                log.info("matchVector pictureresponse 1 null");
                return null;
            }

            //negs = 0; gt = 0;

            float[] vec2 = new float[vec_n];
            for (int i=0;i<vec_n;i++) {
                //diff2[i] = pr.p.vgg16_4[i] - end.vgg16_4[i];
                vec2[i] = (fac2 * pr.p.nnl_7[i]) - end.nnl_7[i];
                if (vec2[i] < 0.0f) {
                    negs++;
                    vec2[i] *= -1.0f;
                }

                while (vec2[i] > 0.2f) {
                    gt++;
                    vec2[i] *= 0.5f;
                }
            }
/*
            log.info("diff2:\n" + Arrays.toString(pr.p.vgg16_4) + "\n" +
                                Arrays.toString(end.vgg16_4) + "\n" +
                                Arrays.toString(vec2));
*/
//log.info("VV vecang " + last.dotVecAng + " -> " + fac1 + ", " + fac2);
            log.info("VV vecang " + last.dotVecAng + " -> " + fac1 + ", " + fac2 +
                    " negs/halvings: " + negs + "/" + gt + " diff2:\n" +
                                "VV " + Arrays.toString(pr.p.nnl_7) + "\n" +
                                "VV " + Arrays.toString(end.nnl_7) + "\n" +
                                "VV " + Arrays.toString(vec2));

            lh2 = PictureDao.matchVector(conn, orient, vec2,
                                                    null, // archives TODO per-view
                                                    func, column, 51, picSet);

            log.info("matchVector - got.2nd " + (lh2 == null ? "zilch" : lh2.size()));

        }

        if (lh2 == null  ||  lh2.size() == 0) {
            log.info("matchVector: 2nd lh bunk");
            return null;
        }

        // avoid getting 1st again

        int ix = lh2.id2_l.indexOf(pr.p.id);
        if (ix != -1) {
            // 1st pic in candidates for 2nd
            lh2.id2_l.remove(ix);
            lh2.value_l.remove(ix);
            if (lh2.dbl_l != null  &&  ix < lh2.dbl_l.size()) {
                lh2.dbl_l.remove(ix);
            }
        }

        PictureResponse pr2 = tryLhP(conn, session, orient,
                                        histo2, lh2);
        if (pr2 == null) {
            log.info("matchVector pictureresponse 2 null");
            return null;
        }

        // repack responses

        PictureResponse[] arr = new PictureResponse[2];

        if (left2right == null) {

            // "in"  TODO - make ".2." adapt to non-imagenet column
            String gmethod = "gIN." + funcHow(func + ".2." + column);

            pr.method = gmethod + (histo1 != null ? ".dots" : "");
            pr2.method = gmethod + (histo2 != null ? ".dots" : "");

            arr[0] = pr;
            arr[1] = pr2;

        } else if (left2right) {

            // gLR == gesture left to right   TODO - adaptable type
            String gmethod = "gLR." + funcHow(func + ".2." + column);
            pr.method = gmethod + (histo1 != null ? ".dots" : "");
            pr2.method = gmethod + (histo2 != null ? ".dots" : "");
            arr[0] = pr;
            arr[1] = pr2;

        } else {  // !left2right

            // gRL == gesture right to left   TODO - adaptable type
            String gmethod = "gRL." + funcHow(func + ".2." + column);

            pr.method = gmethod + (histo1 != null ? ".dots" : "");
            pr2.method = gmethod + (histo2 != null ? ".dots" : "");

            arr[0] = pr2;
            arr[1] = pr;

        }


        List<Screen> scl = new ArrayList<>();

        scl.add(new Screen(session.browserID, 1, "v", arr[0].p.id, arr[0]));
        scl.add(new Screen(session.browserID, 2, "v", arr[1].p.id, arr[1]));

        return scl;
    }


    private List<Screen> dotsOnOnePic(Connection conn,
                                        int viewNum, String orient,
                                        Session session, UserProfile up,
                                        String idDrawnOn, int screen,
                                        boolean stroke)
            throws SQLException {

        ShowingPair last = up.last;  // should match w/ ids
        int ndots = last.dotCount;
        DotHistory dh = last.dotHistory;

        Set<String> picSet = data.getPicSet(viewNum, orient);
        SeenIds seenIds = getSeen(conn, session);

        // choose a func

        String func = null;

        if (ndots % 2 == 0) {
            func = "cos";
        } else {
            func = "poi"; // distance, orig poincare
        }

        //  using number of angles in histogram
        //  as a measure of intricacy

        int angles = 0;
        if (dh != null  &&  dh.angleHist != null) {
            for (int i : dh.angleHist) {
                if (i > 0) {
                    angles++;
                }
            }
        } else {
            log.error("dotsOnOnePic: missing angleHist");
        }
        if (angles == 0) {
            log.error("dotsOnOnePic: no angles, so setting=1");
            angles = 1;
        } else {
            log.info("dotsOnOnePic: angles=" + angles);
        }
        if (angles == 1) {

            //if (stroke) {
            //log.error("DUPE lost possible on stroke");
            //}
            func += nDotsToVectorColumn(ndots);
        } else if (angles < 5) {
            // 2 is shortest/fastest
            func += chooseVector2(ndots);
        } else if (angles < 10) {
            func += ".2.nnl_7"; // avg/fold - indexed
        } else {
            func += ".2.mob_10"; // avg/fold - indexed
            log.info("TODO angles >= 10, if many: GREPME " + angles);
        }

        // double the usual limit since picking two pics
        //      note: no dupes in lh

        int limit = 100;

        ListHolder lh = PictureDao.matchVector(conn, orient,
                                    screen, idDrawnOn,
                                    func,
                                    data.getViewArchives(viewNum),
                                    limit, picSet);

        if (lh == null  ||  lh.size() < 2) {

            log.error("dotsOnOnePic: not enough lh");
            return null; // out of pics in session?
        }

        String how = Integer.toString(screen) + funcHow(func);

        //log.info("dotsOnOnePic func/how " + func + " / " + how);

        String id1 = null;
        String id2 = null;
        PictureResponse pr1 = null;
        PictureResponse pr2 = null;

        final int SMALL = 20;

        if (stroke) {

            log.info("dotsOnOnePic: 'stk[AB]' ndots=" + ndots);

            // pick two from the sorted list of 100
            // for each screen (drawn, undrawn)
            //      pick a 'window' of ~10..25 on the list
            //      do weighted random
            // require drawn replacement closer to 0th
            //      of the two
            //      pick that based on number of dots:
            //          <5 dots, start at 0
            //          >50, use 50
            // pick undrawn replacemant from remainder
            //                          of list, >= 50.

            int window_size = 15;

            // replacement for drawn-on pic

            int start;
            if (ndots < 5) {
                start = 0;
            } else if (ndots > 50) {
                start = 50;
            } else {
                start = ndots;
            }

            // TODO weighted sel
            int first = start;
            if (first > lh.size()) {
                first = 0;
            }

            int ix = first;  // ix is for method
            while (first > 0) {
                lh.remove(0);
                first--;
            }

            //  first will remain constant while deleting
            //  its lh values

            while (lh.size() > 1) {

                String tId = lh.id2_l.get(first);
                lh.remove(first);
                ix++;

                pr1 = tryId(conn, orient, session,
                                  picSet, seenIds, tId);

                if (pr1 == null) {  // error maybe no longer seen?
                    log.info("No version for unseen seq " + tId);
                    continue;
                }
                pr1.method = "stk" + how + "A" + ix;
                id1 = tId;
                break;
            }

            // choose id2 from remainder of lh

            //  map angle:
            //          clock  html_degrees
            //          3      0
            //                    [l->r]  start after 1st pick
            //          9      +-180
            //                    [r->l]  use last window of 25 or less
            //
            //          mapped_angle: [(180-|html|)/180] * [list_len-window_size]
            //          up           html >0 in 0..180:  start + mapped_angle
            //          down         html <0 0..-180:    end - window - mapped_angle
            //
            //  Angle numbers from browser:
            //
            //  0: o--->      [3 o'clock]
            //          positive: clockwise (down)
            //          negative: counterclockwise (up)
            //  +-180: <---o  [9 o'clock]

            double angle = Math.abs(last.dotVecAng);
            int mapped_angle = (int)
                    ( (double) (lh.size() - window_size)
                        * (180.0-angle)/180.0
                    );

            if (mapped_angle > lh.size()) {
                log.error("mapped_angle > lh.size() " +
                            mapped_angle + " " + lh.size());
            }

            int second;
            if (last.dotVecAng > 0) {
                second = mapped_angle;
            } else {
                second = lh.size() - window_size - mapped_angle;
            }
            log.info("XXX second " + second + " lh " + lh.size() +
                    " mapped_angle " + mapped_angle);

            ix += second;
            while (second > 0) {
                lh.remove(0);
                second--;
            }

            while (lh.size() > 0) {

                String tId = lh.id2_l.get(0);  // TODO weighted
                lh.remove(0);
                ix++;

                pr2 = tryId(conn, orient, session,
                                  picSet, seenIds, tId);

                if (pr2 == null) {  // error maybe no longer seen?
                    log.info("No version for unseen seq " + tId);
                    continue;
                }
                pr2.method = "stk" + how + "B" + ix;
                id2 = tId;
                break;
            }

            if (pr1 == null  ||  pr2 == null) {

                log.error("SINGLE/pic stroke: missing pic");
                return null;
            }

        } else if (ndots < SMALL) {   // quick circle?

            // first/last - swap ends for criss-cross try

            log.info("dotsOnOnePic: 'ends[AB]' ndots=" + ndots);


            while (lh.size() > 1) {

                int ix = lh.size() - 1;

                String tId = lh.id2_l.get(ix);
                lh.remove(ix);

                pr1 = tryId(conn, orient, session,
                                  picSet, seenIds, tId);

                if (pr1 == null) {  // error maybe no longer seen?
                    log.info("No version for unseen seq " + tId);
                    continue;
                }
                pr1.method = "ends" + how + "A" + ix;
                id1 = tId;
                break;
            }

            if (pr1 == null) {
                log.error("dotsOnOnePic: not enough lh on pr1");
                return null;
            }

            while (lh.size() > 0) {

                int ix = 0;

                String tId = lh.id2_l.get(ix);
                lh.remove(ix);

                pr2 = tryId(conn, orient, session,
                                  picSet, seenIds, tId);

                if (pr2 == null) {  // error maybe no longer seen?
                    log.info("No version for unseen seq " + tId);
                    continue;
                }
                pr2.method = "ends" + how + "B" + ix;
                id2 = tId;
                break;
            }
            if (pr2 == null) {
                log.error("dotsOnOnePic: not enough lh on pr2");
                return null;
            }

        } else {  // ndots >= SMALL

            log.info("dotsOnOnePic: 'wig[AB]' ndots=" + ndots);

            // replacement for drawn-on pic

            // use ndots for 1st/closer choice like in stroke

            int skip = ndots - SMALL;
            skip = (int) Math.sqrt((double) skip);

            if (skip < 5) {
                skip = 0;
            } else if (skip > 50) {
                skip = 50;
            }

            int ix = 0;

            //log.info("dotsOnOnePic SKIP1 " + skip + " sz " + lh.size());
            while (skip > 0  &&  lh.size() > 1) {
                lh.remove(0);
                skip--;
            }

            while (lh.size() > 1) {

                String tId = lh.id2_l.get(0);
                lh.remove(0);
                ix++;

                pr1 = tryId(conn, orient, session,
                                  picSet, seenIds, tId);

                if (pr1 == null) {  // error maybe no longer seen?
                    log.info("dotsOnOnePic: No version for unseen seq " + tId);
                    continue;
                }
                pr1.method = "wig" + how + "A" + ix;
                id1 = tId;
                break;
            }

            if (pr1 == null) {
                log.error("dotsOnOnePic: not enough lh on pr1");
                return null;
            }

            // pr2 - use vector length of 1st-last dot

            // fraction to skip

            double dlr = 0.0;
            if (last.dotDist > 0  &&  last.dotVecLen > 0) {
                if (last.dotVecLen < last.dotDist) {
                    // how could it not be?
                    dlr = (double)last.dotVecLen / last.dotDist;
                } else {
                    dlr = (double)last.dotDist / last.dotVecLen;
                }
                if (dlr > 1.0) {
                    log.warn("dotsOnOnePic: 1-2 distances < 1: " + last.dotDist);
                    dlr = 0.5;
                }
                if (dlr > 0.5) {
                    dlr = 1.0 - dlr;
                }
            } else {
                log.warn("dotsOnOnePic: 1-2 distances < 1: " + last.dotDist);
                dlr = 0.5;
            }
            skip = (int) ((double) lh.size() * dlr);
            //log.info("dotsOnOnePic " + dlr + "  SKIP " + skip + " sz " + lh.size());
            if (skip < lh.size() - 5) {
                while (skip-- > 0) {
                    lh.remove(0);
                    ix++;
                }
            }

            while (lh.size() > 0) {

                String tId = lh.id2_l.get(0);
                lh.remove(0);
                ix++;

                pr2 = tryId(conn, orient, session,
                                  picSet, seenIds, tId);

                if (pr2 == null) {  // error maybe no longer seen?
                    log.info("No pr2 version for unseen seq " + tId);
                    continue;
                }
                pr2.method = "wig" + how + "B" + ix;
                id2 = tId;
                break;
            }

            if (pr2 == null) {
                log.error("dotsOnOnePic: not enough lh on pr2");
                return null;
            }
        }

        // pr1, pr2 decided

        List<Screen> ret = new ArrayList<>();
        if (screen == 1) {
            //log.info("dotsOnOnePic: " + func + " -> " + id1 + " " + id2);
            ret.add(new Screen(session.browserID, 1, "v", id1, pr1));
            ret.add(new Screen(session.browserID, 2, "v", id2, pr2));
        } else {
            // switch them? not sure if screen has a 'natural' use
            //log.info("dotsOnOnePic: " + func + " -> " + id2 + " " + id1);
            ret.add(new Screen(session.browserID, 1, "v", id2, pr2));
            ret.add(new Screen(session.browserID, 2, "v", id1, pr1));
        }

        return ret;
    }

    private List<Screen> dotsOnBothPics(Connection conn,
                                        int viewNum, String orient,
                                        Session session, UserProfile up,
                                        List<String> idsDrawnOn,
                                        boolean stroke)
            throws SQLException {

        Set<String> picSet = data.getPicSet(viewNum, orient);
        SeenIds seenIds = getSeen(conn, session);

        // parallel == search on each pic at once first

        try {

            List<Screen> ret = handleVectorsInParallel2(conn, viewNum, orient,
                                                 session, up, idsDrawnOn);
            if (ret != null) {
                return ret;
            }

        } catch (Exception e) {
            log.error("drawVectors - handleVectorsInParallel2 failed: " + e, e);
        }

        // drop thru to older code

        // choose color/imagenet/pairml type/dim
        //      color is loose match, imagenet tight

        String func = (up.skew > 0.5 ? "cos" : "poi");
        int[] typeDim = chooseTypeDimension(up);

        String funcdim = func + "." + typeDim[0] + "." + typeDim[1];

        List<Screen> ret = null;
        try {
            ret = handleVectorsInParallel(conn, viewNum, orient,
                                            session, up,
                                            2, idsDrawnOn,
                                            funcdim,
                                            null);// l2r func+dim
        } catch (Exception e) {

            log.warn("handleVectorsInParallel Exception w/ type " + typeDim[0]);
            if (typeDim[0] == 1) {
                throw new SQLException("drawVectors/retry: wrapping ", e);
            }
            log.info("handleVectorsInParallel retry with histogram: " + e);
            typeDim = chooseTypeDimension(up, 1);
            funcdim = func + "." + typeDim[0] + "." + typeDim[1];
            try {
                ret = handleVectorsInParallel(conn, viewNum, orient,
                                            session, up,
                                            2, idsDrawnOn,
                                            funcdim,
                                            null);// l2r func+dim
            } catch (Exception ee) {
                throw new SQLException("drawVectors/retry: wrapping ", ee);
            }
        }
        if (ret != null) {
            log.info("VEXX ok /" + funcdim);
        }
        return ret;
    }

    /*
    **  drawVectors - map user-drawn dots to a new pair of
    **      pics using vector matching (per-pic data).
    */
    private List<Screen> drawVectors(Connection conn,
                                        int viewNum, String orient,
                                        Session session, UserProfile up,
                                        List<String> idsDrawnOn)
            throws SQLException {

        ShowingPair last = up.last;

        // if line is straight, might
        //      treat it as a gesture for projection
        //      ... gestureStroke()

        boolean stroke = false;

        if (up.lastCrossings <= 1) {

            double dotVecLen = Math.sqrt(last.dotVecLen);
            double diff =  Math.abs(last.dotDist - dotVecLen);
            double pctDiff = 100.0 * diff / last.dotDist;
            //log.info("DDDDD " + last.dotDist + " vec " + dotVecLen +
            //                " diff " + diff + " pct " + pctDiff);
            if (pctDiff < 5.0) { // close to straight line

                stroke = true;
            }
        }

        // TODO other dot geom

        try {

            if (up.lastCrossings == 0) {

                String id = idsDrawnOn.get(last.dotEndScreen-1);

                return dotsOnOnePic(conn, viewNum, orient,
                                        session, up, id,
                                        last.dotEndScreen, stroke);

            } else if (stroke) {

                List<Screen> ret = gestureStroke(conn, viewNum, orient, session, up, idsDrawnOn);
                if (ret != null) {
                    return ret;
                }

            }

            return dotsOnBothPics(conn, viewNum, orient,
                                        session, up, idsDrawnOn, stroke);

        } catch (Exception e) {

            log.error("drawVectors: unexpected", e);
        }

        log.warn("drawVectors: returning null");

        return null;
    }

    private List<Screen> oldDrawDots(Connection conn, int viewNum, String orient,
                                            Session session, UserProfile up,
                                            int n, List<String> ids,
                                            int screenId1, int screenId2,
                                            String kwdCoder)
            throws SQLException {

        ShowingPair last = up.last;

        List<Screen> ret = null;

        int which = last.dotEndScreen; // last pic drawn on

        if (which < 1  ||  which > 2) {
            log.error("1-pic dots: Unexpected dots end screen not 1,2: " +
                                                        last.dotEndScreen +
                                        " dotStartScreen: " + last.dotStartScreen);
            which = -1;
        }

        log.info("oldDrawDots: screenIds " + screenId1 + "|" + screenId2 + " which " + which);

        // possible change of subject if draw style
        //  same for N times

        int style = last.tmpInt;
        int repeats = 0;

        for (int i=1;i<6;i++) {
            if (up.history.size() <= i) {
                break;
            }
            if (up.history.get(i).tmpInt != style) {
                break;
            }
            repeats++;
        }

        boolean triedCache = false;

        if (repeats > 3) { // MAGIC

            List<Screen> scl = tryNeighborsCache(conn, viewNum, orient, session, ids, which, 1);// circle
            if (scl != null) {
                return scl;
            }
            triedCache = true;
            log.info("oldDrawDots: on repeats/fast failed (tryNeighborsCache)");
        }

        boolean fast = (up.lastCrossings == 0  ||
                        up.longerThanAverageUserTime1Dev);
//fast=true;
        log.info("oldDrawDots: crossings: " + up.lastCrossings +
                    " longer1Dev: " + up.longerThanAverageUserTime1Dev +
                    " fast: " + fast +
                    "  dotStartScreen " + last.dotStartScreen +
                    " -> dotEndScreen " + last.dotEndScreen +
                    "  which " + which);

        if (fast  &&  !triedCache) {

            // no db delay for cached

            List<Screen> scl = tryNeighborsCache(conn, viewNum, orient, session, ids, which, 0);// circle
            if (scl != null) {
                return scl;
            }
            log.info("getScreens fast failed (tryNeighborsCache)");

            // other fast-triggered stuff below
        }

        boolean ABTried = false;
        if (up.lastCrossings > 3) {

            // Make a jump 'away' by using an arbitrary color-based mapping.
            //    AB just relies on picture table,
            //    so should be optimally-fast, TODO exploit pgvector.

            ret = handleGoldenScreensAB(conn, viewNum, orient, session, up,
                                              ids, 0);
            if (ret != null) {
                return ret;
            }
            ABTried = true;
        }

        // pairtop may be 2nd-fastest, nn best of pairtop

        if (fast  &&  PairTopDao.hasTable("nn", orient)) {

            // (already tried Nbr cache on fast, above)

            // match just one pic by the fastest method,
            //      inspired by db delay on pair table after idle

            List<String> pairtop_nn_tags = data.getPairtopNNTags(
                                                    "v".equals(orient));

            //log.info("=====%%= FAST " + Arrays.toString(pairtop_nn_tags.toArray()));
            // starting index into pairtop_nn_tags, incremented

            // init based on user drawing
            // TODO - try two pairtop_nn's then drop thru to pgvector
            //   .. not really a case for pairtop_nn's to fail?

            int tsize = pairtop_nn_tags.size();
            int t = up.dotMap(tsize);

            if (tsize > 2) tsize = 2;

            for (int i=0;i<tsize;i++) {

                String tag = pairtop_nn_tags.get(t);

                //log.info("=====%%= TRY " + t + " -> " + tag);

                ret = handlePhiScreens(conn, viewNum, orient, session, up,
                                                "pt " + tag, n, ids, which);
                if (ret != null) {
                    //log.info("=====%%= TRY->GOT " + t + " -> " + tag);
                    return ret;
                }

                t++;
                if (t == pairtop_nn_tags.size()) {
                    t = 0;
                }
            }

            log.error ("FAST/Phi FAILED tags " + pairtop_nn_tags.size());
        }

        if (fast  &&  !ABTried) {

            // last resort for fast


            // picture table query on color should be fast,
            //  tho not coherent.

            ret = handleGoldenScreensAB(conn, viewNum, orient, session, up,
                                                ids, 0);
            if (ret != null) {
                return ret;
            }
            ABTried = true;

            log.error("Fast: final AB failed");
        }

        // not fast, or trying fast failed

        switch (up.lastCrossings) {

            // > 3: tried handleGoldenScreensAB above

            case 0:   // all in one pic - in 'fast' above but backstopping code here.

                ret = tryNeighborsCache(conn, viewNum, orient, session, ids, up.last.dotEndScreen-1, 0);
                if (ret != null) {
                    return ret;
                }

                // drop thru no break;

            case 1:
            case 2:

                // default: dotMappedNNs unless excuse for d0 (dots == drawn by user)

                String d0reason = "";

                if (up.prevRating == 13) {
                    d0reason = "lastneg";
                }
                if (!"".equals(d0reason)  &&  PairDao.hasTable(orient)) {

                    log.info("GO a_d0: " + d0reason + " last " +
                            (up.last == null ? "null" : up.last.selMethod));

                    ret = tryNeighborsCache(conn, viewNum, orient, session, ids, up.last.dotEndScreen-1, 0);

                    if (ret == null) {

                        if (!ABTried) {

                            // try quick?

                            ret = handleGoldenScreensAB(conn,
                                                        viewNum, orient, session, up,
                                                        ids, 0);
                            if (ret != null) {
                                return ret;
                            }
                            ABTried = true;
                        }

                        // worst case for response time

                        ret = handlePrPosScreens(conn,
                                                viewNum, orient, session, up,
                                                "a_d0", ids, -1);
                    }
                } else if (PairTopDao.hasTable("nn", orient)) {

                    ret = dotMappedNNs(conn, viewNum, orient,
                                                kwdCoder,
                                                session, up, ids,
                                                screenId1, screenId2);
                }
                // TODO - add new best guess
                break;

            case 3:
            case 4:

                // TODO - does this matter? switched to pgvector-based
                //          parallel color match, so d0 on 2nd would be
                //          done via a general sequential method not
                //          coming to mind, passing "d0" as caller in case  (2023_09)
                //  first pic (TODO l or r, based on either l or r)
                //  is chosen by selected color histo alg/space
                //  2nd pic is nn match

                ret = handleColorScreens(conn,
                                            viewNum, orient, session, up,
                                            "d0",
                                            n, ids);

                //  case 4 same as case 3 except 2nd pic also based on color-match
                //  to 1st, can be a diff color alg
                // ret = handleColorScreens(conn,
                //                             viewNum, orient, session, up,
                //                             13, // 13=?  IGNORED, see TODO above
                //                             n, ids);

                break;

            case 5:

                //  golden angle selection by selected space
                //  2nd pic is nn match

                int[] opts = { 0, -7, -8, -9, -11 };
                int prop = up.scaleInt(opts.length);
                prop--;
                log.info("PROP " + prop);
                if (prop < 0) {
                    log.error("NEG/0");
                    prop = rand.nextInt(opts.length);
                }
                int lopt = opts[prop];

                if (lopt == 0) {
                    ret = handleGoldenScreensAB(conn,
                                                    viewNum, orient, session, up,
                                                    ids, -1);// -1=nn 2nd
                } else {
                    ret = handleGoldenScreens(conn,
                                                    viewNum, orient, session, up,
                                                    ids, lopt);
                }
                break;

            case 6:

                //  1st (l or r) chosen randomly
                //  2nd pic is nn match

                return handleRandomScreens(conn,
                                                viewNum, orient, session, up,
                                                true);// true=nn 2nd

            default: // 7+
                // both random
                return handleRandomScreens(conn,
                                                viewNum, orient, session, up);
        }

        if (ret != null) {
            return ret;
        }
        log.warn("oldDrawDots zilch");
        return null;
    }

    /*
    **  getScreens - main entry point
    */
    public List<Screen> getScreens(Connection conn,
                                    int viewNum, String orient,
                                    String kwdCoder, Session session,
                                    int option, String cmdMod,
                                    int n, List<String> ids,
                                    int screenId1, int screenId2,
                                    int lastBig, ShowingPair last)
            throws Exception {

        checkScreens("getScreens", screenId1, screenId2);

        long t0 = System.currentTimeMillis();

        Set<String> picSet = data.getPicSet(viewNum, orient);
        if (picSet == null) {
            log.error("Picset null for view " + viewNum + "." + orient);
            throw new Exception("Picset null for view " + viewNum + "." + orient);
        }

        SeenIds seenIds = getSeen(conn, session);
        seenIds.exclude.clear();
        if (ids != null  &&  ids.size() > 0) {
            seenIds.exclude.addAll(ids);
        }

        UserProfile up = new UserProfile(conn, session.browserID, 2, last);

        log.info("iscream GETSCREENS GETSCREENS GETSCREENS ===\nGETSCREENS   view " + viewNum + " orient " + orient +
                  " screenIds " + screenId1 + "|" + screenId2 +
                  " option " + option + " cmdMod " + cmdMod +
                  " total picSet " + picSet.size() +
                  " last " +
                      (up.last == null ? "null" :
                                 "" + up.last.orderInSession));

        if (option == -3) {
            // tiling change - return last 2 screens
            if (up.history.size() < 2) {
                log.error("HISTORY < 2 for option==-3");
            }
            return session.screens;
        }

        if (option == -2) {

            // init, was chosen from 'top' pairs
            return handleRandomScreens(conn, viewNum, orient, session, up);

        }

        if (option == 2) {
            // user-selected random
            log.info("user.random opt=2.... " + orient);
            return handleRandomScreens(conn, viewNum, orient, session, up);
        }

        List<String> picList = data.getPicList(viewNum, orient);
        if (!session.repeatPics) {

            int showOrient = ("v".equals(orient) ? seenIds.vertical : seenIds.horizontal);
            log.info("SEEN " + showOrient + "/" + picList.size() +
                         " view " + viewNum + "/" + orient);
        }

        //StringBuilder sum = new StringBuilder();
        //log.info("CP " + sum.toString());

        List<Screen> ret = null;

        if (cmdMod != null) {

            log.info("HIGHOPTION: " + cmdMod);

            // func,dim chosen in UI w/ option:
            //
            //      Phob -> Parallel descent
            //
            // 'type==pairnet' is used
            //      to distinguish asym cases
            //      (l,r matches on separate vecs,
            //      from ML) from sym ones (same vec
            //      used for l, r, from histograms
            //      or imagenet models):
            //

            int sigma = option % 100;

            log.info("sigma " + sigma + " func " + cmdMod);

            String[] ss = cmdMod.split("\\.");
            String type = "imagenet";
            String countertype = "histogram";
            if (ss != null  &&  ss.length > 1) {
                if ("1".equals(ss[1])) {
                    type = "histogram";
                    countertype = "imagenet";
                } else if ("2".equals(ss[1])) {
                    type = "imagenet";
                    countertype = "histogram";
                } else if ("3".equals(ss[1])) {
                    type = "pairnet";
                    countertype = "imagenet";
                }
            }

            String l2rFunc = null;

            switch (sigma) {

                case 21:    // Sigma1 -> parallel, l->l', r->r'
                    break;//            so leave l2r func null

                case 22:     // Sigma2 -> latentGeom

                    if ("imagenet".equals(countertype)) {
                        l2rFunc = "cos.2.vgg16_256";
                    } else {
                        l2rFunc = "cos.1.256";
                    }
                    break;

                case 23:     // Sigma3 -> latentGeom

                    if ("imagenet".equals(countertype)) {
                        l2rFunc = "cos.2.dense_1024";
                    } else {
                        l2rFunc = "cos.1.1728";
                    }
                    break;

                case 24:     // Sigma4

                    if ("imagenet".equals(countertype)) {
                        l2rFunc = "cos.2.mob_1280";
                    } else {
                        l2rFunc = "cos.1.1984";
                    }
                    break;

                case 25:     // Sigma5

                    if ("imagenet".equals(countertype)) {
                        l2rFunc = "cos.2.nnl_672";
                    } else {
                        //l2rFunc = "poi.1.1984";
                        l2rFunc = "cos.2.vgg16_32";
                    }
                    break;

                default:

                    log.error("BAD CASE on l->r option: " + option);
                    l2rFunc = "cos.1.1984";
                    break;
            }

            ret = handleVectorsInParallel(conn, viewNum, orient,
                                            session, up,
                                            n, ids,
                                            cmdMod, // firstFunc, from browser
                                            l2rFunc);

            if (ret != null) {
                log.info("handleVectorsInParallel Success with " + cmdMod);

                return ret;
            }
        }

        if (option > 100) {

            log.info("fallback [?] - strip 100's codes from vector match func: " + option);
            option %= 100;

        }

        switch (option) {

          case 0:

            ret = tryD0BadCache(conn, viewNum, orient, session);
            if (ret == null) {
                ret = handleNeuralNegScreens(conn, viewNum, orient,
                                            session, up, ids);
            }
            break;

          case 1:   // yellow '+'

            if (ret == null) {
                ret = handleColorScreens(conn, viewNum, orient,
                                            session, up,
                                            "yplus", n, ids);
            }
            break;

          case 3:  // green '+', see KEYWORDS boolean - TODO-ish keywords not used

            ret = handleKeywordScreens(conn, viewNum, orient,
                                            session, up, n, ids);
            if (ret == null) {
                log.info("Kwds no - fallback a_d0");
                ret = handlePrPosScreens(conn, viewNum, orient, session, up,
                                               "a_d0", ids, -1);
            }
            break;

          case 6:    // grey '2'

            ret = handleGoldenScreensAB(conn, viewNum, orient, session, up,
                                                ids, option);
            break;

          case 7:    // grey '3'
          case 8:    // grey '8'
          case 9:    // grey '27'
          case 11:   // grey '32K'

            ret = handleGoldenScreens(conn, viewNum, orient, session, up,
                                            ids, option);
            break;

          case 10:

            // drew dots on pair: this case is the attempt at personality

            ret = drawVectors(conn, viewNum, orient, session, up, ids);
            if (ret != null) {
                return ret;
            }
            log.warn("DOTS/10: vectors failed and the past takes over");

            ret = oldDrawDots(conn, viewNum, orient, session, up, n, ids,
                                screenId1, screenId2, kwdCoder);
            break;

          case 12:

            // not used? replaces old in-memory kwd-based join
            // ret = handleApprovedPair12(conn,
            //                              viewNum, orient, session, up,

            ret = handleApprovedPair0(conn, "case12",
                                            viewNum, orient, session, up,
                                            ids);
            break;

          case 13:

            ret = handleNeuralNegScreens(conn,
                                            viewNum, orient, session, up,
                                            ids);
            break;

          case 14:
          case 15:
          case 16:
          case 17:
          case 18:
          case 19:

            throw new RuntimeException("Reload page");
            //break;

          case 20:

            // Sigma 0

            ret = tryNeighborsCache(conn, viewNum, orient, session, ids, up.last.dotEndScreen-1, 1);
            if (ret == null) {
                // slow
                ret = handlePrPosScreens(conn,
                                            viewNum, orient, session, up,
                                            "a_d0", ids, -1);
            }
            break;

          case 21:
          case 22:
          case 23:
          case 24:
          case 25:

            // Sigma 1-5, default/PairNet since cmdMod is null.
            //              If no PairNet support, falls back to
            //              histo/imagenet vectors

            int sigma = option - 20;
            ret = doSigmaPairNet(conn, viewNum, orient,
                                       session, up,
                                       sigma, n, ids);
            break;

          case 26:

            // Sigma x = random PairNet/'NN'

            ret = biasedNNScreens(conn, viewNum, orient, kwdCoder,
                                        session, up,
                                        ids, rand.nextDouble());
            break;

          case 30:

            // flip if unseen, else randomNN

            ret = flipEmDanno(conn, viewNum, orient, session, ids);
            if (ret == null) {
                ret = biasedNNScreens(conn, viewNum, orient, kwdCoder,
                                            session, up,
                                            ids, rand.nextDouble());
            }
            break;


          default:

            throw new RuntimeException("Unknown option: " + option);

        }

        if (ret == null) {
            // likely already done
            ret = tryNeighborsCache(conn, viewNum, orient, session, ids, up.last.dotEndScreen-1, 0);
            if (ret == null) {
                ret = handlePrPosScreens(conn,
                                            viewNum, orient, session, up,
                                            "a_d0", ids, -1);
            }
        }

        if (ret == null  ||  ret.size() == 0) {
            return ret;
        }

        log.info("\n======== GOT " + up.skew + "\nGOT " + screenSummary(ret));
        higSleep(conn, session, option, ids, t0, up, ret);

        if (KEYWORDS) {

            // log any keyword match

            String pnStr = "";
            if (ids != null  &&  ids.size() > 0) {
                if (ids.size() != 2) {
                    throw new RuntimeException("IDs != 2: " + ids.size());
                }
                pnStr = "\n==>PN: " +
                    DBUtil.kwdCompare(conn, kwdCoder, ret.get(0).id_s,
                                                    ids.get(0), ids.get(1));
            }
            String lrStr = "\n==>LR: " +
                DBUtil.kwdCompare(conn, kwdCoder, ret.get(1).id_s, ret.get(0).id_s);
            log.info(pnStr + lrStr);
        }

        return ret;
    }

    // called by cacheNeighborsThread;just D0,
    //          cache vectors too if they get slow
    private void cacheNeighbors(Connection conn,  Session session,
                                                  int viewNum, String orient,
                                                  List<Screen> screens,
                                                  int circle)
            throws SQLException {

        long t0 = System.currentTimeMillis();

        Set<String> picSet = data.getPicSet(viewNum, orient);

        PictureResponse pr1 = (PictureResponse) screens.get(0).pr;
        PictureResponse pr2 = (PictureResponse) screens.get(1).pr;

        String id1 = pr1.p.id;
        String id2 = pr2.p.id;

        SeenIds seenIds = getSeen(conn, session);

        //UserProfile up = new UserProfile(conn, session.browserID, 2, last);

        NeighborsCache nbrs = new NeighborsCache(viewNum, orient);

        PairDao.getD0Neighbors(conn, orient, session.curator,
                                        picSet, seenIds,
                                        id1, id2,
                                        circle, /* 0 is centered */
                                        nbrs.upList, nbrs.downList);

        int cached = nbrs.upList.size() + nbrs.downList.size();

        if (cached > 0) {
            double upd = 0.0;
            for (Pair pr : nbrs.upList) {
                upd += PictureDao.vec_dist(conn, pr.id1, pr.id2);
            }
            if (upd > 0.0) {
                upd /= nbrs.upList.size();
            }
            double downd = 0.0;
            for (Pair pr : nbrs.downList) {
                downd += PictureDao.vec_dist(conn, pr.id1, pr.id2);
            }
            if (downd > 0.0) {
                downd /= nbrs.downList.size();
            }

            if (circle == 0) {
                d0NbrCache.put(session.browserID, nbrs);
            } else {
                d0NextNbrCache.put(session.browserID, nbrs);
            }
            log.info("D0Nbrs circle " + circle + "  cached: " + cached +
                        "  up/avg: " + nbrs.upList.size() + "/" + upd +
                        " down/avg: " + nbrs.downList.size() + "/" + downd +
                        "  browser " + session.browserID);
        } else {
            log.info("D0Nbrs - none cached circle " + circle +
                            " " + session.browserID);
        }
    }

    final private int CACHE_EVAC_MINUTES = 1;

    public void cacheNeighborsThread(final Session session,
                                        final int viewNum, final String orient,
                                        final List<Screen> screens,
                                        final int circle) {
        if (!PairDao.hasD0(orient)) {
            return;
        }

        String wow = "";
        Map<Long, NeighborsCache> map = null;
        switch (circle) {
            case 0:
                map = d0NbrCache;
                break;
            case 1:
                map = d0NextNbrCache;
                break;
            default:
                log.error("cacheNeighborsThread: Unhandled  circle: " + circle);
                return;
        }

        NeighborsCache cache = map.get(session.browserID);

        if (cache != null) {
            // log.info("XXXX cache not null");
            if (viewNum == cache.viewNum  &&
                java.util.Objects.equals(orient, cache.orient)  &&
                cache.created <
                    System.currentTimeMillis() -
                    TimeUnit.MINUTES.toMillis(CACHE_EVAC_MINUTES)) {

                // cache is good
                // log.info("XXX Cache is good");
                return;
            }
            log.info("Evacuate cache [" + circle + "] for " + session.browserID);
            map.remove(session.browserID);
        }

        new Thread(new Runnable() {
            public void run() {
                Connection conn = null;
                try {
                    conn = DaoBase.getConn();
                    cacheNeighbors(conn, session, viewNum, orient, screens, circle);
                } catch (Exception sqe) {
                    if (sqe.getMessage().contains("pr.pairs_")  &&
                        sqe.getMessage().contains("does not exist")) {
                        log.debug("No pairs tbl to cache");
                    } else {
                        log.error("HEYYYY DUDDE cacheNeighbors th1", sqe);
                    }
                } finally {
                    DaoBase.closeSQL(conn);
                }
            }
        }).start();
    }

    /**
      *  cacheD0BadThread - not worried about making bad pairs bad in
      *     relation to the current pair, so just want to keep a few
      *     handy/replenished.
      */

    public void cacheD0BadThread(final Session session,
                                       final int viewNum, final String orient) {

        // pre-check

        final D0BadCache cache = d0BadCache.get(session.browserID);
        if (cache != null) {

            // no aging out since bad is relatively timeless

            if (cache.viewNum != viewNum  ||
                !java.util.Objects.equals(cache.orient, orient)) {

                log.info("Purging cacheD0Bad " + session.browserID);
                d0BadCache.remove(session.browserID);

            } else if (cache.list.size() > 5) {
                log.info("cacheD0Bad cache ok " + session.browserID);
                return;
            }
        }

        log.info("cacheD0Bad start thread");

        new Thread(new Runnable() {
            public void run() {
                Connection conn = null;
                try {
                    conn = DaoBase.getConn();
                    cacheD0Bad(conn, session, viewNum, orient, cache);
                } catch (Exception sqe) {
                    if (sqe.getMessage().contains("pr.pairs_")  &&
                        sqe.getMessage().contains("does not exist")) {
                        log.debug("No pairs tbl to cache");
                    } else {
                        log.error("HEYYYY DUDDE cacheD0Bad th1: " + sqe);
                    }
                } finally {
                    DaoBase.closeSQL(conn);
                }
            }
        }).start();

    }


    private void cacheD0Bad(Connection conn, Session session,
                                            int viewNum, String orient,
                                            D0BadCache cache)
            throws SQLException {

        log.info("cacheD0Bad " + session.browserID);

        long t0 = System.currentTimeMillis();

        if (cache != null) {
            if (cache.viewNum != cache.viewNum  ||  !cache.orient.equals(orient)) {
                d0BadCache.remove(session.browserID);
                cache = null;
            }
        }

        if (cache == null) {
            cache = new D0BadCache(viewNum, orient);
            d0BadCache.put(session.browserID, cache);
        }

        Set<String> picSet = data.getPicSet(viewNum, orient);
        SeenIds seenIds = getSeen(conn, session);

        PairDao.getD0Bad(conn, orient, picSet, seenIds, cache.list);
    }

    private interface IndexedScreenListGetterIF {

        public int size();

        public List<Screen> getScreens(int option, Connection conn,
                                    int viewNum, String orient,
                                    String kwdCoder,
                                    Session session, UserProfile up,
                                    List<String> ids)
            throws SQLException;
    }

    private class PairsVHHandler implements IndexedScreenListGetterIF {
        //private String orient;
        //PairsVHHandler(String orient) {
        //    this.orient = orient;
        //}

        @Override
        public int size() {
            return 3;
        }
        @Override
        public List<Screen> getScreens(int option, Connection conn,
                                        int viewNum, String orient,
                                        String kwdCoder,
                                        Session session, UserProfile up,
                                        List<String> ids)
            throws SQLException {

            List<Screen> ret = tryNeighborsCache(conn, viewNum, orient, session, ids, up.last.dotEndScreen-1, 0);
            if (ret != null) {
                return ret;
            }
            return handlePrPosScreens(conn,
                                        viewNum, orient, session, up,
                                        "a_d0", ids, -1);
        }
    }


    private final int LEN_PHI_MERGE = 200;


    private ListHolder mergePairTopAsym(Connection conn, String orient,
                                         String id,
                                         String cols[], boolean lr,
                                         SeenIds seenIds,
                                         Set<String> picSet)
            throws SQLException {

        ListHolder[] lhs = new ListHolder[cols.length];
        int max = 0;
        for (int i=0;i<cols.length;i++) {
            lhs[i] = PairTopDao.getPairtopAsym(conn,
                                        orient, id, lr, // left->right match
                                        cols[i], false, "DESC", // DESC/best
                                        LEN_PHI_MERGE, picSet);
            max += lhs[i].size();
        }
        log.info("mergePairTopAsym(" + id + "): tot: " + max);

        ListHolder ret = new ListHolder();
        Set<String> got = new HashSet<>();
        long val = max * 2;
        int skip = 0;
        for (int level=0;level<LEN_PHI_MERGE && val > 0;level++) {
            for (int i=0;i<lhs.length;i++) {
                ListHolder lh = lhs[i];
                if (lh == null) {
                    continue;
                }
                if (lh.size()-1 < level) {
                    lhs[i] = null;
                    continue;
                }
                String id2 = lh.id2_l.get(level);
                if (got.contains(id2)  ||
                    seenIds.contains(id2)) {
                    skip++;
                    continue;
                }

                got.add(id2);
                ret.id2_l.add(id2);
                ret.value_l.add(val);
                if (ret.size() > LEN_PHI_MERGE) {
                    log.info("finished quota at level " + level +
                             " val " + val);
                    val = -1;
                    break;
                }
                val--;
            }
        }
        log.info("mergePairTopAsym(" + id + "): got: " + ret.size());

        return ret;
    }


    // pairtop_nn_[vh]

    private List<Screen> handleMergeRandomAsymScreens(Connection conn,
                                            int viewNum, String orient,
                                            Session session, UserProfile up,
                                            List<String> ids)
            throws SQLException {

        if (LIMITED_PAIRTOP) {
            if (viewNum != 0  &&  viewNum != 1) {
                log.info(
                    "handleMergeRandomAsymScreens: skipping for view!=0/1");
                return null;
            }
        }

        final List<String> pairtop_nn_tags = new ArrayList(
                             data.getPairtopNNTags("v".equals(orient)));

        int n_grps = 2;
        if (up.skew > 0.5) { // MAGIC
            n_grps = 3 + rand.nextInt(pairtop_nn_tags.size()-2);
        } else if (up.skew > 0.25) { // MAGIC
            n_grps = 3 + rand.nextInt(pairtop_nn_tags.size()-3);
        }

        List<String> l = null;
        if (n_grps == pairtop_nn_tags.size()) {
            l = pairtop_nn_tags;
        } else {
            Collections.shuffle(pairtop_nn_tags);
            l = new ArrayList<>();
            for (int i=0;i<n_grps;i++) {
                l.add(pairtop_nn_tags.get(i));
            }
        }

        // iterate tags since intersect too slow
        for (String tag : l) {

            List<Screen> ret = handlePhiScreens(conn,
                                                    viewNum, orient, session, up,
                                                    "pt " + tag, 2, ids, -1);
            if (ret != null) {
                return ret;
            }
        }
        List<Screen> ret = tryNeighborsCache(conn,
                                             viewNum, orient, session,
                                             ids, 1, // which
                                             0);
        if (ret != null) {
            return ret;
        }

        return handlePrPosScreens(conn,
                                    viewNum, orient, session, up,
                                    "a_d0", ids, 1);// 1==which
    }

    private List<Screen> biasedNNScreens(Connection conn,
                                            int viewNum, String orient,
                                            String kwdCoder,
                                            Session session, UserProfile up,
                                            List<String> ids, double bias)
            throws SQLException {

        // For counting:
        // pair_[hv].[p_,n_,a_]
        final List<List<String>> pairs_nn_cols =
                                        data.getPairsNNCols("v".equals(orient));

        if (pairs_nn_cols == null) {
            return null;
        }

        // n_ * p_ + a_
        final int n_pairs_cols_opts = pairs_nn_cols.get(0).size() *
                                      pairs_nn_cols.get(1).size();


        if (LIMITED_PAIRTOP) {
            if (viewNum != 0  &&  viewNum != 1) {  // all or Bill's
                return handlePrPosScreens(conn,
                                            viewNum, orient, session, up,
                                            null, ids, -1);
            }
        }

        List<String> pairtop_nn_tags = data.getPairtopNNTags("v".equals(orient));

        // num_random_options (weight) assigned for some
        // mix of random pairtop_[vh]
        //              using same as #pairtop cols
        final int n_pairtop_tag_mix = pairtop_nn_tags.size() * 2;

        final int n_pairtop_tag_opts = pairtop_nn_tags.size() +
                                           n_pairtop_tag_mix;

        final int n_options = n_pairs_cols_opts + n_pairtop_tag_opts;

        int opt = (int) (bias * n_options);

        log.info("biasedNNScreens bias " + bias +
                 " opt " + opt + "/" + n_options +
                 " pairs opts: " + n_pairs_cols_opts +
                 " pairtop opts: " + n_pairtop_tag_opts);

        List<Screen> ret;

        int sqsize = pairs_nn_cols.get(0).size() * pairs_nn_cols.get(1).size();

        if (opt < n_pairs_cols_opts) {

            // pairs_nn_[vh]

            ret = handlePrPosScreens(conn,
                                        viewNum, orient, session, up,
                                        null, ids, -1);

        } else {

            opt -= n_pairs_cols_opts;

            // pairtop_nn_[vh]

            if (opt < pairtop_nn_tags.size()) {

                String tag = pairtop_nn_tags.get(opt);

                ret = handlePhiScreens(conn,
                                        viewNum, orient, session, up,
                                        "pt " + tag, 2, ids, -1);
            } else {

                log.info("\n== n_ptop..n-2 (random/asym): " + opt);
                // weighted jump to random pairtop hijinx
                ret = handleMergeRandomAsymScreens(conn,
                                                    viewNum, orient, session, up,
                                                    ids);
            }
        }
        return ret;
    }


    // lh is trashed
    private List<Screen> tryLh(Connection conn, Session session, String orient,
                                UserProfile up, ListHolder lh, String method)
            throws SQLException {

        int size = lh.size();

        // up.last => use dot-derived histogram
        createDblL(up.last, lh, null);

        int ct = 0;

        SeenIds seenIds = getSeen(conn, session);

        //for (int i=0;i<lh.size();i++)
        while (lh.size() > 0) {

            ct++;

            int choice = selectRandomDistributedValue(lh.dbl_l, -1);

            String pr_id2 = lh.id2_l.get(choice);
            String ids_arr[] = pr_id2.split("_");
            if (seenIds.contains(ids_arr[0])) {
                lh.id2_l.remove(choice);
                lh.value_l.remove(choice);
                lh.dbl_l.remove(choice);
                continue;
            }
            if (seenIds.contains(ids_arr[1])) {
                lh.id2_l.remove(choice);
                lh.value_l.remove(choice);
                lh.dbl_l.remove(choice);
                continue;
            }
            if (ShowingPairDao.checkSeen(conn, session.browserID,
                                               ids_arr[0], ids_arr[1])) {
                lh.id2_l.remove(choice);
                lh.value_l.remove(choice);
                lh.dbl_l.remove(choice);
                continue;
            }

            PictureResponse pr1 = new PictureResponse();
            pr1.p = PictureDao.getPictureById(conn, ids_arr[0]);

            if (pr1.p == null) {

                log.info("No version for unseen seq " + ids_arr[0]);
                lh.id2_l.remove(choice);
                lh.value_l.remove(choice);
                lh.dbl_l.remove(choice);
                continue;
            }
            pr1.method = method;

            PictureResponse pr2 = new PictureResponse();
            pr2.p = PictureDao.getPictureById(conn, ids_arr[1]);
            if (pr2.p == null) {
                log.info("No version for unseen seq " + ids_arr[1]);
                lh.id2_l.remove(choice);
                lh.value_l.remove(choice);
                lh.dbl_l.remove(choice);
                continue;
            }
            pr2.method = method;

            log.info("Got " + ct + "/" + size);

            List<Screen> scl = new ArrayList<>();

            scl.add(new Screen(session.browserID, 1, "v",
                                                        ids_arr[0], pr1));
            scl.add(new Screen(session.browserID, 2, "v",
                                                        ids_arr[1], pr2));

            return scl;
        }
        log.info("Nothing, tried " + size);
        return null;
    }

    private double conditionRatio(double ratio) {
        if (Double.isNaN(ratio)) {
            return 0.0;
        }
        if (ratio == 0.0) {
            ratio = 0.01;
        } else {
            while (Math.abs(ratio) < 0.01) {
                ratio *= 10.0;
            }
            while (Math.abs(ratio) > 10.0) {
                ratio /= 10.0;
            }
            while (Math.abs(ratio) > 1.0) {
                ratio /= 2.0;
            }
        }
        return ratio;
    }

    private List<Screen> tryTry(Connection conn,
                                            int viewNum, String orient,
                                            Session session,
                                            Set<String> picSet, double ratio,
                                            String id)
            throws SQLException {

        // id is already cleared to show

        PictureResponse pr = new PictureResponse();
        pr.p = PictureDao.getPictureById(conn, id);
        if (pr.p == null) {
            log.info("No version for unseen seq " + id);
            return null;
        }
        pr.method = "try/appr";

        List<Screen> scl = tryForApprovedPair(conn,
                                                viewNum, orient, session,
                                                picSet, id, pr, null, ratio);
        if (scl != null  &&  scl.size() > 0) {
            return scl;
        }
        //log.info("tryTry failed");
        return null;
    }

    private List<Screen> tryApprovedPair(Connection conn,
                                             int viewNum, String orient,
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

        if (ShowingPairDao.checkSeen(conn, session.browserID, ap.id1, ap.id2)) {
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

    public PictureResponse getNeuralMatch(Connection conn,
                                            int viewNum, String orient,
                                            Session session,
                                            ShowingPair last,
                                            String id, int screen)
            throws SQLException {

        Set<String> picSet = data.getPicSet(viewNum, orient);

        boolean a_d0 = false;
        String source = null;
        ListHolder lh = null;

        log.info("getNeuralMatch " + last.picClik +
                " " + last.orderInSession + " -> " +
                ((double)last.orderInSession / picSet.size()));

        if (last != null  &&
            (double) last.orderInSession / picSet.size() > 0.4) {

            a_d0 = true;
            source = "a_d0";
            lh = PairDao.getPosPairs(conn, orient, session.curator, "a_d0", id,
                                           (screen==1), // get_left
                                           null, // hasKwd
                                           true, // == "DESC"
                                           0, picSet);

        } else {

            int len = 200;
            if (last != null  &&  last.orderInSession > 1000) {
                len = 800;// in pairtop, this may be meaningless, w/ 200 per
            }
            // using just cols in both v,h
            String col = "d1";
            if (last.picClik.endsWith("c")) {
                col = "d1";
            } else if (last.picClik.startsWith("LT")) {
                col = "d2";
            } else if (last.picClik.startsWith("LB")) {
                col = "d3";
            } else if (last.picClik.startsWith("RT")) {
                col = "d4";
            } else {
                col = "d5";
            }
            source = "pt/" + col;
            lh = PairTopDao.getPairtopAsym(conn,
                                        orient, id, (screen==0), // l->r / r->l
                                        col,
                                        false, "DESC", // DESC/best
                                        len, picSet);
        }
        if (!a_d0  &&  lh.size() == 0) {
            source = "a_d0";
            // normal for small views, ~6K

            log.info("Fall back to pairs_[vh] for picSet " + picSet.size());
            lh = PairDao.getPosPairs(conn, orient, session.curator, "a_d0", id,
                                          (screen==1), // get_left
                                          null, // hasKwd
                                          true, // == "DESC"
                                          0, picSet);
            a_d0 = true;
        }
        if (lh.size() == 0) {
            log.error("No list at all for " + id + "/" + source +
                      " viewNum " + viewNum + "/" + orient +
                      ", picSet " + picSet.size());
            return null;
        }

        log.info("getNeuralMatch " + (screen==0 ? "l->r " : "r->l ") +
                  last.picClik + "->" + source + ": " + id +
                  " picSet " + picSet.size());

        // evaluate lh

        Set<String> seenPairs= null;
        if (session.curator != null) {
            if (screen == 0) {
                seenPairs = UniquePairDao.getPicsSeen(conn, id, "id1");
            } else {
                seenPairs = UniquePairDao.getPicsSeen(conn, id, "id2");
            }
        }

        Set<String> seen = new HashSet<>();
        int skipped_seen = 0;
        while (lh.size() > 0) {
            //log.info("SIZE/lh " + lh.size());
            PictureResponse pr = tryLhP(conn, session, orient, last, lh);
            if (pr == null) {
                break;
            }
            String otherid = pr.p.id;
            if (seen.contains(otherid)) log.error("CONT " + otherid);
            seen.add(otherid);
            if (seenPairs != null) {
                if (seenPairs.contains(otherid)) {
                    int ix = lh.id2_l.indexOf(otherid);
                    lh.id2_l.remove(ix);
                    lh.value_l.remove(ix);
                    lh.dbl_l.remove(ix);
                    skipped_seen++;
                    continue;
                }
            }
            pr.method = source;
            log.info("getNeuralMatch " + source + ": " + otherid +
                     " skipseen " + skipped_seen +
                     " seen " + seen.size() +
                     " totseen " +
                       (seenPairs == null ? "na" : seenPairs.size()));
            return pr;
        }
        if (a_d0) {
            log.info("getNeuralMatch: ZILCH skipseen " + skipped_seen);
            return null;
        }

        // fall back on d0

        lh = PairDao.getPosPairs(conn, orient, session.curator, "a_d0", id,
                                      (screen==1), // get_left
                                      null, // hasKwd
                                      true, // == "DESC"
                                      0, picSet);

        log.info("Fallback on all d0: " + lh.size());

        skipped_seen = 0;
        while (lh.size() > 0) {
            PictureResponse pr = tryLhP(conn, session, orient, last, lh);
            if (pr == null) {
                break;
            }
            String otherid = pr.p.id;
            if (seen.contains(otherid)) {
                continue;
            }
            seen.add(otherid);
            if (seenPairs != null) {
                if (seenPairs.contains(otherid)) {
                    int ix = lh.id2_l.indexOf(otherid);
                    lh.id2_l.remove(ix);
                    lh.value_l.remove(ix);
                    lh.dbl_l.remove(ix);
                    skipped_seen++;
                    continue;
                }
            }
            pr.method = "a_d0";
            log.info("getNeuralMatch " + source + ": " + otherid +
                     " skipseen " + skipped_seen +
                     " seen " + seen.size()
                     + " totseen " +
                       (seenPairs == null ? "na" : seenPairs.size()));
            return pr;
        }

        log.info("getNeuralMatch: ZILCH skipseen " + skipped_seen);
        return null;
    }

    private List<Screen> skewedApprovedMatch(Connection conn,
                                            Session session, UserProfile up,
                                            int viewNum, String orient,
                                            Set<String> picSet,
                                            List<ApprovedPair> l,
                                            String method)
            throws SQLException {

        if (l.size() == 0) {
            log.info("skewedApprovedMatch: no input pairs");
            return null;
        }
        SeenIds seenIds = getSeen(conn, session);
        //log.warn("HACK - USING d0 for sortCol");
        String sortCol = "a_d0";
        List<ApprovedPair> apl2 = new ArrayList<>();
        long max = 0;
        for (ApprovedPair ap : l) {
            if (seenIds.contains(ap.id1)  ||  seenIds.contains(ap.id2)) {
                continue;
            }
            // 0 == perfect match
            ap.sortVal = PairDao.getVal(conn, ap.id1, ap.id2, orient, sortCol);
            if (ap.sortVal > max) {
                max = ap.sortVal;
            }
            apl2.add(ap);
        }
        if (apl2.size() == 0) {
            log.info("skewedApprovedMatch: no remaining pairs");
            return null;
        }

        Collections.sort(apl2);// ascending for d0==raw neural

        int mid = (int)(up.skew * (double) apl2.size());
        if (mid == apl2.size()) {
            mid--;
        }
        mid = 0;

        log.info("skewedApprovedMatch: init size: " + l.size() +
                    " procd " + apl2.size() + " mid " + mid);
        l = apl2;
int x=0;
        for (int i=0;i<l.size();i++) {
            boolean seen = false;
            int ix = mid - i;
            if (ix > -1) {
                seen = true;
                ApprovedPair ap = l.get(ix);
x++;
                List<Screen> scl = tryApprovedPair(conn,
                                                    viewNum, orient, session,
                                                    picSet, ap, seenIds,
                                                    method + "/" + sortCol);
                if (scl != null) {
                    log.info("skewedApprovedMatch got " + ix + " " +
                                 screenSummary(scl));
                    return scl;
                }
            }
            if (i > 0) {
                ix = mid + i;
                if (ix < l.size()) {
                    seen = true;
x++;
                    ApprovedPair ap = l.get(ix);
                    List<Screen> scl = tryApprovedPair(conn,
                                                        viewNum, orient, session,
                                                        picSet, ap, seenIds,
                                                        method + "/" + sortCol);
                    if (scl != null) {
                        log.info("skewedApprovedMatch got " + ix + " " +
                                 screenSummary(scl));
                        return scl;
                    }
                }
            }
            if (!seen) {
                break;
            }
        }
        log.info("skewedApprovedMatch: fallback");

        List<Screen> ret = fallbackLinear(conn, viewNum, orient,
                                                session, up,
                                                up.skew);
        if (ret == null  ||  ret.size() == 0) {
            log.error("No fallback");
        }
        return ret;
    }


    private ListHolder interleaveLists(Set<String> picSet, SeenIds seenIds,
                                       ListHolder lh1, ListHolder lh2) {

        ListHolder lh_max = lh1;
        int max = lh_max.size();
        int min = lh1.size();
        if (max < min) {
            lh_max = lh1;
            int t = min;
            min = max;
            max = t;
        }

        Set<String> ids = new HashSet<>();

        ListHolder lh = new ListHolder();
        // interleave
        for (int i=0;i<min;i++) {

            String id1 = lh1.id2_l.get(i);
            if (!ids.contains(id1)  &&
                picSet.contains(id1)  &&
                !seenIds.contains(id1)) {

                ids.add(id1);

                lh.id2_l.add(id1);
                lh.value_l.add(lh1.value_l.get(i));
            }

            String id2 = lh2.id2_l.get(i);
            if (!ids.contains(id2)  &&
                picSet.contains(id2)  &&
                !seenIds.contains(id2)) {

                ids.add(id2);

                lh.id2_l.add(id2);
                lh.value_l.add(lh2.value_l.get(i));
            }
        }
        // overflow
        for (int i=min;i<max;i++) {
            String id = lh_max.id2_l.get(i);
            if (!ids.contains(id)  &&
                picSet.contains(id)  &&
                !seenIds.contains(id)) {

                ids.add(id);// just in case somehing gets added further on

                lh.id2_l.add(id);
                lh.value_l.add(lh_max.value_l.get(i));
            }
        }

        int diff = lh1.size() + lh2.size() - lh.size();

        log.info("interleaved ids lh " + lh.size() + " dupes: " + diff);

        return lh;
    }



    private List<Screen> handleApprovedPair0(Connection conn, String caller,
                                            int viewNum, String orient,
                                            Session session, UserProfile up,
                                            List<String> ids)
            throws SQLException {

        // try to avoid exposing approved prs, slow anyway
        log.warn("handleApprovedPair0 - caller " + caller);

        final boolean best = true;

        Set<String> picSet = data.getPicSet(viewNum, orient);
        SeenIds seenIds = getSeen(conn, session);

        double ratio = 0.0;
        double rat2 = 0.0;
        if (up.last != null  &&  up.avgUserTime > 0) {
            ratio = (double) up.last.userTime / up.avgUserTime;
            ratio = conditionRatio(ratio);
            rat2 = (double) up.deltaUserTime / up.avgUserTime;
            rat2 = conditionRatio(rat2);
        }

        int limit = session.curator == null ? 500 : -1;

        ListHolder lh = PairDao.getPosPairsParallel(conn, orient, session.curator, "a_d0",
                                                    ids, best, 200, picSet);

        PictureResponse pr = new PictureResponse();

        Set<String> seenAps = new HashSet<>();

        while (lh.size() > 0) {
            pr.p = chooseFromDistribution(conn, "appr/0", session, up, lh, 0,
                                                orient);
            if (pr.p == null) {
                log.info("Exhausted w/ limit " + limit);
                break;
            }
            String id = pr.p.id;

            List<ApprovedPair> apl = ApprovalDao.getApprovedPairsWithPic(
                                                    conn, id, // null,
                                                    picSet);
            if (apl.size() == 0) {
                // next pic matching one of orig pair
                continue;
            }

            int zeroct = 0;

            String method = "a_d0";

            // sometimes throw in a reverse sort
            if (up.last != null  &&  up.last.userTime < 6000  &&
                up.skew > 0.68) {  // MAGIC, 1.0 > skew > .333

                method = "a_d0";

                log.info("**************\n********\n******** Worst?");

                for (ApprovedPair ap : apl) {
                    if (seenAps.contains(ap.idP)) {
                        continue;
                    }
                    seenAps.add(ap.idP);
                    ap.sortVal = PairDao.getVal(conn, id, ap.otherId, orient,
                                                      "a_d0", true);
                    if (ap.sortVal == 0) {
                        zeroct++;
                    }
                }
                Collections.sort(apl);

            } else {

                for (ApprovedPair ap : apl) {
                    if (seenAps.contains(ap.idP)) {
                        continue;
                    }
                    seenAps.add(ap.idP);
                    ap.sortVal = PairDao.getVal(conn, id, ap.otherId, orient,
                                                      "a_d0", true /* def 0*/);
                    if (ap.sortVal == 0) {
                        zeroct++;
                    }
                }
                Collections.sort(apl, Collections.reverseOrder());
            }

            log.info("Sorted ap's: " + method + ": " +
                     apl.get(0).sortVal + ".." +
                     apl.get(apl.size()-1).sortVal +
                     " zeroes: " + zeroct);

            for (int ix=0;ix<apl.size();ix++) {

                ApprovedPair ap = apl.get(ix);

                List<Screen> scl = tryApprovedPair(conn,
                                                    viewNum, orient, session,
                                                    picSet, ap, seenIds, "ap/d0");
                if (scl != null) {
                    log.info("Got linear sorted approved pair " + ix + " " +
                                 screenSummary(scl));
                    return scl;
                }
            }
            log.info("no ap's matching " + id);
        }
        log.info("handleApprovedPair0 exhausted - go linear");
        return linearSearchApprovedPairs(conn,
                                            viewNum, orient, session, up,
                                            picSet, seenIds,
                                            ratio, rat2);
    }

    private List<Screen> linearSearchApprovedPairs(Connection conn,
                                            int viewNum, String orient,
                                            Session session, UserProfile up,
                                            Set<String> picSet, SeenIds seenIds,
                                            double ratio, double rat2)
            throws SQLException {

        //boolean first = up.longerThanSecondLastUserTime;
        //boolean forward = up.longerThanAverageUserTime;

        List<ApprovedPair> allpr = null;
        if ("v".equals(orient)) {
            allpr = data.approvedV;
        } else {
            allpr = data.approvedH;
        }

        int mid = (int)(ratio * rat2 * allpr.size());
        if (mid < 0) {
            mid *= -1;
        }
        mid = mid % allpr.size();

        log.info("handleApprovedPair0 'linear' search " + orient +
                    " size " + allpr.size() +
                    " picSet size " + (picSet == null ? 0 : picSet.size()) +
                    " seenIds.seen " + (seenIds.seen == null ? "null" : "yes") +
                    " mid " + mid);

        int x=0;
        for (int i=0;i<allpr.size();i++) {
            boolean seen = false;
            int ix = mid - i;
            if (ix > -1) {
                seen = true;
                ApprovedPair ap = allpr.get(ix);
                x++;
                List<Screen> scl = tryApprovedPair(conn,
                                                    viewNum, orient, session,
                                                    picSet, ap, seenIds, "ap0/lin/" + x);
                if (scl != null) {
                    log.info("Got linear approved pair " + ix + " " +
                                 screenSummary(scl));
                    return scl;
                }
            }
            if (i > 0) {
                ix = mid + i;
                if (ix < allpr.size()) {
                    seen = true;
                    x++;
                    ApprovedPair ap = allpr.get(ix);
                    List<Screen> scl = tryApprovedPair(conn,
                                                        viewNum, orient, session,
                                                        picSet, ap, seenIds,
                                                       "ap0/lin/" + x);
                    if (scl != null) {
                        log.info("Got linear approved pair " + ix + " " +
                                 screenSummary(scl));
                        return scl;
                    }
                }
            }
            if (!seen) {
                break;
            }
        }
        log.info("handleApprovedPair linear search done: " +
                  x + "/" + allpr.size() +
                 " signaling option done/ODONE");

        return new ArrayList<>();
    }


    private List<Screen> tryOutAP(Connection conn,
                                                String orient,
                                                Session session,
                                                int screenId,
                                                String id1,
                                                ApprovedPair ap,
                                                PictureResponse origPr,
                                                SeenIds seenIds)
            throws SQLException {

        if (screenId == 0) {
            if (!ap.id1.equals(id1)) return null;
        } else {
            if (!ap.id2.equals(id1)) return null;
        }

        if (seenIds.contains(ap.otherId)) {
            return null;
        }
        if (ShowingPairDao.checkSeen(conn, session.browserID, ap.id1, ap.id2)) {
            return null;
        }
        PictureResponse pr = new PictureResponse();
        pr.p = PictureDao.getPictureById(conn, ap.otherId);
        if (pr.p == null) {
            log.info("No version for unseen seq " + ap.otherId);
            return null;
        }
        List<Screen> ret = new ArrayList<>();

        if (screenId == 0) {
            ret.add(new Screen(session.browserID, 1, "v",
                                                      id1, origPr));
            ret.add(new Screen(session.browserID, 2, "v",
                                                      ap.otherId, pr));
        } else {
            ret.add(new Screen(session.browserID, 1, "v",
                                                      ap.otherId, pr));
            ret.add(new Screen(session.browserID, 2, "v",
                                                      id1, origPr));
        }
        return ret;
    }

    private double[] higDim(List<ShowingPair> history) {

        if (history.size() < 10) {
            return new double[] { 0.0, 0.0 };
        }

        // play w/ Higuchi dimension
        long tt1 = System.currentTimeMillis();

        int vecSize = history.size();// since pairs means double count

        Vector<Double> userTimes = new Vector<>(vecSize);
        Vector<Double> netTimes = new Vector<>(vecSize);

        //Vector<Double> clickTimes = new Vector<>(history.size());
        //Vector<Double> userTimes2 = new Vector<>(history.size());
        //Vector<Double> mouseDists = new Vector<>(history.size());
        //Vector<Double> mouseDists2 = new Vector<>(history.size());

        for (int i=history.size()-1;i>-1;i-=1) {  // put in sequential order

            ShowingPair sp = history.get(i);

            userTimes.add(new Integer( sp.userTime).doubleValue());
            netTimes.add(new Integer( sp.bigTime ).doubleValue());
                                      // -1,-2 ok

            //clickTimes.add(new Integer(sp.clickTime).doubleValue());
            //userTimes2.add(new Integer(sp.userTime2).doubleValue());
            //mouseDists.add(new Integer(sp.mouseDist).doubleValue());
            //mouseDists2.add(new Integer(sp.mouseDist2).doubleValue());
        }

        double ret[] = new double[] { 0.0, 0.0 };

        int k = userTimes.size() / 3;

        if (k == 0) {
            return ret;
        }

        Higuchi hig = new Higuchi();
        Vector<Double> L = hig.calcLengths(userTimes, k);
        double[] result = hig.calcDimension(L, 1, L.size());
        ret[0] = -1.0 * result[0];
        L = hig.calcLengths(netTimes, k);
        result = hig.calcDimension(L, 1, L.size());
        ret[1] = -1.0 * result[0];

        log.info("HIGuchi t_calc " + (System.currentTimeMillis() - tt1));
        return ret;
    }

    // named after Higuchi, which is used for history analysis
    // but has multiple components

    private void higSleep(Connection conn, Session session,
                            int option, List<String> ids,
                            long t0, UserProfile up, List<Screen> scl)
            throws SQLException {

        if (option == 13  ||  option == 30) { // D0Neg, flipEm
            log.info("HIG skip neg/flip=13,30");
            return;
        }
        if ((option == 0  ||  option == 10  ||  option == 13)  &&
                session.curator == null  &&  option == 10  &&
                DEBUG_SLEEP_O_KINGLY_BUG > 0) {

            log.info("HIG/alt: Kingly UI sleep, triggered by config: " +
                    DEBUG_SLEEP_O_KINGLY_BUG);
            try {
                Thread.sleep(DEBUG_SLEEP_O_KINGLY_BUG);
            } catch (Exception ignore) {}

            return;
        }

        if (scl.size() != 2) {
            log.info("HIG skipped for size " + scl.size());
            return;
        }
        long t1 = System.currentTimeMillis() - t0;
        if (t1 > 1200) { // MAGIC
            log.info("HIG skip for hard time cut: " + t1);
            return;
        }
        if (up.minLoadTime < (Integer.MAX_VALUE / 2)  &&
            up.minLoadTime > 100  &&
            t1 > 2 * up.minLoadTime) { // MAGIC
            log.info("HIG skip for time > 2 * minLoadTime: t1/minload" +
                      t1 + "/" + up.minLoadTime);
            return;
        }
        int netLatency = NET_LATENCY_CUT / 3;
        if (up.last != null) {
            netLatency = up.last.bigTime - up.last.bigStime;
        }

        double higDim[] = higDim(up.history);// [user, net]
        long higDiff = ((long) (higDim[0] * 1000)) % 200;// MAGIC
        higDiff += ((long) (higDim[1] * 1000)) % 100;// MAGIC

        // that's it for the Hig part, the rest of the calc
        // is pic-based

        // destination pair

        double rat = 0.0;
        if ("density".equals(PIC_DELAY)) {
            // density: avg 986, min 80, max 2826
            int a = ((PictureResponse)(scl.get(0).pr)).p.density;
            int b = ((PictureResponse)(scl.get(1).pr)).p.density;
            rat = (double)(a + b) / 5652;// 2x max
        } else if ("contrast".equals(PIC_DELAY)) {
            // contrast: avg 84, min 1, max 100
            int a = ((PictureResponse)(scl.get(0).pr)).p.labContrast;
            int b = ((PictureResponse)(scl.get(1).pr)).p.labContrast;
            int d1 = Math.abs(a - 84) + Math.abs(b - 84);
            rat = (double)(d1 * d1) / 1400.0;// MAGIC
            while (rat > 1.0) {
                //log.info("SCALE/HIG rat " + rat);
                rat /= 10.0;
            }
        } else {
            log.warn("HIG: NO PIC DELAY");
        }
        long picDiff = (long) (rat * 600);// MAGIC

        // pair/pair delta

        // ll 0..90 avg 40

        long nextL = ((PictureResponse)(scl.get(0).pr)).p.ll +
                    ((PictureResponse)(scl.get(1).pr)).p.ll;

        ListHolder lh = PictureDao.getIntList(conn, "ll", ids, false);

        long prevL = 0;
        if (lh.value_l.size() > 1) {
            prevL = lh.value_l.get(0) + lh.value_l.get(1);
        } else if (lh.value_l.size() > 0) {
            prevL = 2 * lh.value_l.get(0);// dunno
        }

        // positive == getting darker
        double rat2 = (double)(prevL - nextL) / (prevL + nextL); // MAGIC
        long deltaDiff = (long) (rat2 * PAIR_DELTA_FACTOR);// MAGIC

        long totDiff = higDiff;
        if (PIC_DELAY_UP) {
            totDiff += picDiff;
        } else {
            totDiff -= picDiff;
        }
        totDiff += deltaDiff;
        long target, final_target;
        if (BIG_DELAY_UP) {
            if (totDiff > 5 * BASE_UP_SLEEP) {
                log.info("Sqrting totDiff/HIG " + totDiff);
                totDiff = (int) Math.sqrt((double)totDiff);
            }
            target = BASE_UP_SLEEP;
            if (netLatency > NET_LATENCY_CUT) {  // MAGIC
                log.info("HIG cut target " + target);
                target /= Math.sqrt(netLatency - NET_LATENCY_CUT);
            }
            final_target = target + totDiff;// e.g. longer for denser
        } else {
            if (totDiff > BASE_DOWN_SLEEP) {
                log.info("Sqrting totDiff/HIG " + totDiff);
                totDiff = (int) Math.sqrt((double)totDiff);
            }
            target = BASE_DOWN_SLEEP;
            if (netLatency > NET_LATENCY_CUT) {  // MAGIC
                log.info("HIG cut target " + target);
                target /= Math.sqrt(netLatency - NET_LATENCY_CUT);
            }
            final_target = target - totDiff;
        }
        if (final_target < 400) {
            log.info("HIG Bump final to 400");
            // approx avg tennis response time fo boys is 354
            final_target = 400;
        } else {
            // get it near:
            // "A computational model later confirmed that these results
            //  were consistent with the idea that the motor cortex has
            //  its own internal oscillator that naturally operates at
            //  around 4 to 5 hertz." Poeppel et al
            long r4 = final_target % 250;
            long r5 = final_target % 200;
            if (r4 < r5) {
                if (r4 > 15) { // MAGIC
                    long t = (r4 - 15) / 2;
                    log.info("HIG 4ths > 15: " + final_target + " - " + t);
                    final_target -= t;
                }
            } else {
                if (r4 > 10) { // MAGIC
                    long t = (r4 - 10) / 2;
                    log.info("HIG 5ths > 10: " + final_target + " - " + t);
                    final_target -= t;
                }
            }
        }

        long t2 = System.currentTimeMillis() - t0;
        log.info("HIGUCHI target " + final_target + " t_took " + t2 +
                          " higd.user " + higDim[0] +
                          " higd.net " + higDim[1] +
                          " rat " + rat +
                          " rat2 " + rat2 +
                          " target0 " + target +
                          " higdelay " + higDiff + " picDelay " + picDiff +
                          " deltaDelay " + deltaDiff +
                          " totDelay " + totDiff +
                          (up.last == null ? "" :
                            " lastBig " + up.last.bigTime +
                            " lastBigS " + up.last.bigStime));

        final_target -= t2;// negative is normal
        if (final_target < 2) {
            log.info("HIG final_target < 2: " + final_target);
            return;
        }
        if (final_target > 1200) {
            final_target = 1200;
        }
        log.info("SLEEP/HIG " + final_target);
        try { Thread.sleep(final_target);} catch (Exception ignore) {}
    }


    private List<Screen> tryForKwdApproved(Connection conn,
                                            int viewNum, String orient,
                                            Session session, UserProfile up,
                                            String id1,
                                            int screenId, // in [0,1]
                                            ListHolder kwd_lh,
                                            ListHolder kwd_lh_fallback)
            throws SQLException {
        log.info("tryForKwdApproved " + screenId + " " + id1);

        if (true) {
            log.error("NO lh.map! kwds gone a while ago");
            return null;
        }

        Set<String> picSet = data.getPicSet(viewNum, orient);

        double ratio = 0.5;
        if (up.last != null  &&  up.avgUserTime > 0) {
            double t = (double) up.last.userTime / up.avgUserTime;
            if (t == 0.0) {
                t = 0.01;
            } else {
                while (t < 0.01) {
                    t *= 10.0;
                }
                while (t > 0.5) {
                    t /= 2.0;
                }
            }

            if (t < 0.5) {
                if (up.longerThanSecondLastUserTime) {
                    t *= -1;
                }
                ratio += t;
            }
        }

        int mid = 0;

        PictureResponse pr = new PictureResponse();

        // the keyword association is fast, since in-memory

        ListHolder lh = null;
        if (kwd_lh != null) {
            //lh = kwd_lh.map.get(id1);
        }
        if (lh == null  &&  kwd_lh_fallback != null) {
            log.warn("No list for " + id1 + " relaxing");
            //lh = kwd_lh_fallback.map.get(id1);
        }
        if (lh == null) {
            // TODO - retry w/ geom kwds
            log.error("No list for " + id1);
            List<Screen> ret = fallbackLinear(conn,
                                                viewNum, orient,
                                                session, up,
                                                ratio);
            if (ret == null  ||  ret.size() == 0) {
                log.error("No fallback after no list for " + id1);
            }
            return ret;
        }
        mid = (int)(ratio * lh.size());
        if (mid == lh.size()) {
            mid--;
        }

        log.info("handleApprovedPairSCreen kwd matches: " + lh.size() +
                 " ratio " + ratio + " mid " + mid);

        // sort by target plus keyword potential MAGIC

        ListHolder lh2 = new ListHolder();

        int incr = 1;
        long base_val = lh.value_l.get(mid);// get crazy
        for (int i=0;i<lh.size();i++) {
            int theone;
            if (i == 0) {
              theone = mid;
            } else if (i % 2 == 1) {
              theone = mid - incr;
            } else {
              theone = mid + incr;
              incr++;
            }
            if (theone < 0) {
                theone = lh.size() + theone;
            } else if (theone >= lh.size()) {
                theone -= lh.size();
            }
            lh2.id2_l.add(lh.id2_l.get(theone));
            long val = Math.abs(base_val - lh.value_l.get(theone)) % base_val;
            lh2.value_l.add(val);
        }
        if (lh2.size() != lh.size()) {
            log.error("COMPLAIN LOUDLY");
        }

        int ct = 0;
        SeenIds seenIds = getSeen(conn, session);
        List<String> tmpIds = new ArrayList<>();// TODO FIX this dubious scheme

        while (lh2.size() > 0) {

            pr.p = chooseFromDistribution(conn, "app/kwd", session, up, lh2, 0,
                                                orient);
            if (pr.p == null) {
                log.info("No first with list size " + lh2.size());
                break;
            }
            ct++;
            pr.method = "app/kwd";
            String id = pr.p.id;
            List<Screen> scl = tryForApprovedPair(conn,
                                                    viewNum, orient, session,
                                                    picSet, id, pr,
                                                    null /* hasKwd */, ratio);
            if (scl != null  &&  scl.size() > 0) {
                log.info("handleApprovedPair/kwd tried " + ct);
                seenIds.exclude.removeAll(tmpIds);
                return scl;
            }
            seenIds.exclude.add(id);// remember to remove
            tmpIds.add(id);
        }
        log.info("Got nothing");
        seenIds.exclude.removeAll(tmpIds);
        return null;
    }

    private List<Screen> fallbackLinear(Connection conn,
                                        int viewNum, String orient,
                                        Session session, UserProfile up,
                                        double ratio)

            throws SQLException {

        Set<String> picSet = data.getPicSet(viewNum, orient);

        //boolean first = up.longerThanSecondLastUserTime;
        //boolean forward = up.longerThanAverageUserTime;
        List<ApprovedPair> all = ApprovalDao.getAllApprovedPairs(conn,
                                                1, orient, null,//curatorUpper
                                                true, // d0
                                                null, null, picSet);

        int mid = (int)(ratio * (double) all.size());
        if (mid == all.size()) {
            mid--;
        }

        log.info("fallbackLinear: trying 'linear' search " +
                    " size " + all.size() + " mid " + mid);

        SeenIds seenIds = getSeen(conn, session);

        int x=0;
        for (int i=0;i<all.size();i++) {
            boolean seen = false;
            int ix = mid - i;
            if (ix > -1) {
                seen = true;
                x++;
                ApprovedPair ap = all.get(ix);
                List<Screen> scl = tryApprovedPair(conn,
                                                    viewNum, orient, session,
                                                    picSet, ap, seenIds, "fb/lin");
                if (scl != null) {
                    log.info("Got linear approved pair " + ix + " " +
                                 screenSummary(scl));
                    return scl;
                }
            }
            if (i > 0) {
                ix = mid + i;
                if (ix < all.size()) {
                    seen = true;
                    x++;
                    ApprovedPair ap = all.get(ix);
                    List<Screen> scl = tryApprovedPair(conn,
                                                        viewNum, orient, session,
                                                        picSet, ap, seenIds, "fb/lin");
                    if (scl != null) {
                        log.info("Got linear approved pair " + ix + " " +
                                 screenSummary(scl));
                        return scl;
                    }
                }
            }
            if (!seen) {
                break;
            }
        }
        log.info("handleApprovedPair linear search done: " + x + "/" + all.size() +
                 " signaling option done/ODONE");

        return new ArrayList<>();
    }


    private String screenSummary(List<Screen> scl) {
        StringBuilder sb = new StringBuilder();
        for (Screen scr : scl) {
            PictureResponse pr = (PictureResponse) scr.pr;
            sb.append(scr.id_s).append(" (").append(pr.method).append(") ");
        }
        return sb.toString();
    }


    private List<Screen> tryForApprovedPair12(Connection conn,
                                            int viewNum, String orient,
                                            Session session,
                                            Set<String> picSet,
                                            String id, PictureResponse initPr,
                                            Boolean hasKwd, double ratio)
            throws SQLException {

        List<ApprovedPair> approvedPairs = ApprovalDao.getApprovedPairsWithPic(
                                                    conn, id, // hasKwd,
                                                    picSet);
        if (approvedPairs == null  ||  approvedPairs.size() == 0) {
            //log.info("(No approved pairs");
            return null;
        }
        SeenIds seenIds = getSeen(conn, session);

        List<ApprovedPair> apl2 = new ArrayList<>();
        for (ApprovedPair ap : approvedPairs) {
            if (orient != null  &&
                "v".equals(orient) != data.verticals.contains(ap.otherId)) {
                continue;
            }
            if (seenIds.contains(ap.otherId)) {
                continue;
            }
            Picture p = PictureDao.getPictureById(conn, ap.otherId);
            ap.sortVal = p.density;
            apl2.add(ap);
        }
        if (apl2.size() == 0) {
            log.info("Nothing after filter");
            return null;
        }
        Collections.sort(apl2);
        log.info("SOrted " + apl2.get(0).sortVal + " " +
                  apl2.get(apl2.size()-1).sortVal);

        approvedPairs = apl2;

        int target = (int) (ratio * approvedPairs.size());
        int offset = 0;

        log.info("Trying approved pairs: " + approvedPairs.size() +
                   " target " + target);

        List<List<Screen>> bak = new ArrayList<>();
        int[] debug = new int[3];

        while (true) {

            boolean tried = false;

            int ix = target + offset;
            if (ix < approvedPairs.size()) {
                ApprovedPair ap = approvedPairs.get(ix);
                List<Screen> scl = checkApprovedPair(conn, session, ap,
                                                     hasKwd, id, initPr,
                                                     orient, seenIds, debug);
                if (scl != null) {
                    return scl;
                }
                tried = true;
            }
            ix = target - offset;
            if (ix != target  &&  ix > -1) {
                ApprovedPair ap = approvedPairs.get(ix);
                List<Screen> scl = checkApprovedPair(conn, session, ap,
                                                     hasKwd, id, initPr,
                                                     orient, seenIds, debug);
                if (scl != null) {
                    return scl;
                }
                tried = true;
            }
            if (!tried) {
                break;
            }
            offset++;
        }
        //log.info("(No approved pairs");
        return null;
    }

    private ListHolder filterSeen(ListHolder lh, SeenIds seenIds) {

        if (lh == null) {
            return null;
        }

        ListHolder ret = new ListHolder();
        for (int i=0;i<lh.size();i++) {
            String id = lh.id2_l.get(i);
            if (!seenIds.contains(id)) {
                ret.id2_l.add(id);
                ret.value_l.add(lh.value_l.get(i));
            }
        }
        return ret;
    }

    private List<Screen> handleKeywordScreens(Connection conn,
                                            int viewNum, String orient,
                                            Session session, UserProfile up,
                                            int n, List<String> ids)
            throws SQLException {

        Set<String> picSet = data.getPicSet(viewNum, orient);
        SeenIds seenIds = getSeen(conn, session);

        if (up.last.rateTime != null  &&  up.last.createTime != null) { // HACK
            long sysTime = up.last.rateTime.getTime() -
                           up.last.createTime.getTime();

            double diff = Math.abs(sysTime - up.last.userTime);
            if (diff > 0.1 * sysTime) {
                log.warn("DIFF IN TIMES: sys " + sysTime + " user " +
                                                 up.last.userTime);
            }
        }

        // a little sanity check for browsers that don't cache
        //   so do a second load when document["image"].src = tmpimg.src
        if (up.history.size() > 1) {
            if (up.last.callCount == up.history.get(1).callCount) {
                log.warn("DOUBLE count browser " + session.browserID +
                         " count " + up.last.callCount);
            }
        }

        StringBuilder sum = new StringBuilder("\n ");
        StringBuilder tsum = new StringBuilder(" ");


        // get matches for both of last pics interleaved for speed
        //   TODO: use 'in (x,y)' in db for speed
        int depth_per_pic = 100;
        if (up.last.orderInSession > 1000) {
            depth_per_pic *= 2;
            log.info("Bumping depth -> " + depth_per_pic);
        }
        final boolean best = true;
        ListHolder lh = PairDao.getPosPairsParallel(conn, orient, session.curator, "a_d0",
                                                    ids, best, 200, picSet);

        List<Screen> ret = new ArrayList<>();

        PictureResponse initPr = new PictureResponse();
        PictureResponse pr = new PictureResponse();

        int randTries0 = 10; // decrements

        int initPr_ct = 0;
        int pr_ct = 0;

        int skipped_seen = 0;
        int flip_skipped_seen = 0;
        int skipped_approved = 0;

        while (true) {

            initPr.p = null;

            if (lh.size() > 0) {
                initPr.method = "iKd0." + initPr_ct;
                initPr.p = chooseFromDistribution(conn, "d0Ki",
                                                        session, up, lh, -1);
            }
            if (initPr.p == null  &&  randTries0-- > 0) {
                log.info("fall back to random for first");
                initPr = chooseRandomImage(conn, viewNum, orient,
                                                    session, true);
            }
            if (initPr == null  ||  initPr.p == null) {
                log.info("getting init/0 done");
                break;
            }

            // !CONVENTION is id+initPr, id2+pr

            initPr_ct++;

            String id = initPr.p.id;

            ListHolder lh2 = PairDao.getPosPairs(conn, orient, session.curator, "a_d0",
                                                      id, false, // pic rt
                                                      true, // has kwd;ignored
                                                      best,
                                                      depth_per_pic, picSet);
            // AB,BA's mixed, not worrying which for picking
            if (lh2.size() == 0) {
                log.info("all seen for " + id);
                continue;
            }

            log.info("=initPr " + id + " matches: " + lh2.size());

            Set<String> seenPairsId1 = null;
            Set<String> seenPairsId2 = null;
            if (session.curator != null) {
                seenPairsId1 = UniquePairDao.getPicsSeen(conn, id, "id1");
                seenPairsId2 = UniquePairDao.getPicsSeen(conn, id, "id2");
            }

            while (lh2.size() > 0) {

                pr.p = chooseFromDistribution(conn, "K0", session, up,
                                                          lh2, -1);
                if (pr.p == null) {
                    break;
                }
                pr_ct++;

                pr.method = "Kd0." + pr_ct;

                String id2 = pr.p.id;

                if (session.curator != null) {
                    if (seenPairsId1.contains(id2)) {

                        skipped_seen++;

                        // flip for curator? - shouldn't happen soon
                        //      if caught up
                        if (seenPairsId1.contains(id2)) {
                            log.info("flipped seen too (curator)");
                            flip_skipped_seen++;
                            continue;
                        }
                        if (ApprovalDao.settled(conn, id2, id)) {
                            skipped_approved++;
                            continue;
                        }

                        ret.add(new Screen(session.browserID,
                                                    1, "v", id2, pr));
                        ret.add(new Screen(session.browserID,
                                                    2, "v", id, initPr));
                        log.info("Kwd/curator flip:" +
                               " skipped_seen " + skipped_seen +
                               " flip_skipped_seen " + flip_skipped_seen +
                               " skipped_approved " + skipped_approved);
                        return ret;
                    }
                    if (ApprovalDao.settled(conn, id, id2)) {
                        skipped_approved++;
                        continue;
                    }
                    ret.add(new Screen(session.browserID,
                                                    1, "v", id, initPr));
                    ret.add(new Screen(session.browserID,
                                                    2, "v", id2, pr));
                    log.info("Kwd/curator fwd:" +
                               " skipped_seen " + skipped_seen +
                               " flip_skipped_seen " + flip_skipped_seen +
                               " skipped_approved " + skipped_approved);
                    return ret;
                }

                // Both pics haven't been seen in session.
                ret.add(new Screen(session.browserID,
                                                    1, "v", id, initPr));
                ret.add(new Screen(session.browserID,
                                                    2, "v", id2, pr));
                return ret;
            }
            log.info("=l2 done");
        }

        log.info("kwd fallback to general a_d0");
        return handlePrPosScreens(conn,
                                    viewNum, orient, session, up,
                                    "a_d0", ids, -1);
    }

    private PictureResponse tryKwd(Connection conn, ListHolder lh,
                                            int viewNum, String orient,
                                            Session session,
                                            String id1, double tskew)
            throws SQLException {

        if (true) {
            log.error("KWDS need map in ListHolder or redesign");
            return null;
        }
        //lh = lh.map.get(id1);// read-only
        if (lh == null) {
            log.warn("No keyword match list for for " + id1);
            return null;
        }
        Set<String> picSet = data.getPicSet(viewNum, orient);
        ListHolder lh2 = intersectSet(lh, null, picSet);
        log.info(id1 + " List size " + lh2.size());

        int start = (int)(tskew * (double) lh2.size());
        if (start == lh2.size()) {
            start = 0;
        }

        SeenIds seenIds = getSeen(conn, session);

        PictureResponse pr = new PictureResponse();
        StringBuilder blah = new StringBuilder();
        for (int i=start;i<lh2.size();i++) {
            String id = lh2.id2_l.get(i);
            if (seenIds.contains(id)) {
                blah.append(id).append(' ');
                continue;
            }
            pr.p = PictureDao.getPictureById(conn, id);
            if (pr.p == null) {
                log.info("No version for unseen seq " + id);
                continue;
            }
            pr.method = "match";
            return pr;
        }
        for (int i=start-1;i>-1;i--) {
            String id = lh2.id2_l.get(i);
            if (seenIds.contains(id)) {
                blah.append(id).append(' ');
                continue;
            }
            pr.p = PictureDao.getPictureById(conn, id);
            if (pr.p == null) {
                log.info("No version for unseen seq " + id);
                continue;
            }
            pr.method = "match";
            return pr;
        }
        log.info(id1 + " tried: " + blah);
        return null;
    }

}
