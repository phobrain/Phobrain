package org.phobrain.db.record;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

// 2024_02 - putting in db.record for easy compile

/**
 **  DotHistory - Parse String from phodots.js 
 **                 summarizeDots() to ints.
 **
 **             Log and return null if not right.
 */

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import org.doube.geometry.FitCircle;

import org.phobrain.util.MiscUtil;
import org.phobrain.util.HashCount;

import org.phobrain.db.record.HistoryPair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DotHistory {

    private static final Logger log = LoggerFactory.getLogger(DotHistory.class);

    public int lastCrossings = 0;
    public int dots1 = 0;
    public int dots2 = 0;
    public int mouseUps = 0;

    // histograms sized from browser

    public int[] d2angleHist;    // 38
    public int[] d3angleHist;    // 38
    public int[] distHist;     // 51
    public int[] velocityHist; // 51

    public int notDots1; // cases==checksum
    public int[] notDotsX1; // 50
    public int[] notDotsY1; // 50
    public int[] notDotsV1; // 50

    public int notDots2; // cases==checksum
    public int[] notDotsX2; // 50
    public int[] notDotsY2; // 50
    public int[] notDotsV2; // 50

    public int[] rect1;
    public int[] rect2;

    public int[] center1;
    public int[] center2;
    public int centersDist;

    final private HistoryPair hp;
    final private String inputString;

    private int[] ints;
    private int ix = -1;  // for parsing ints[]

    final private Random rand = new Random();

    /**
     **  getArraySize - validate negative and enough room
     **     in ints[] return 0 to stop parse.
     **/
    private int getArraySize() {

        if (ix < 0  ||  ints == null  ||  ix > ints.length-1) {
            log.error("getArraySize: outlandish error");
            return 0;
        }

        if (ints[ix] > -1) {

            log.error("getArraySize: ints[" + ix + "] not negative: " +
                        ints[ix]);
            return 0;
        }

        int len = -1 * ints[ix];

        if (len + ix > ints.length-1) {

            int diff = ix + len - (ints.length-1);
            log.warn("getArraySize: ix=" + ix +
                        ": resetting to length: ints[] not big enough by " + diff + "/" + ints.length);
            len = ints.length-1-ix;
        }

        ix++;
        return len;
    }

    /**
     **  DotHistory - leave ints[] null if it didn't work.
     */
    public DotHistory(HistoryPair hp, String dotHistory, String remoteHost) {

        this.hp = hp;
        this.inputString = dotHistory;

        if ("none".equals(dotHistory)  ||  "0".equals(dotHistory)) {
            return;
        }

        try {
            //ints = MiscUtil.base64ToIntArray(dotHistory);
            // seems smaller as comma'd ascii ints
            ints = MiscUtil.parseIntList(dotHistory);
        } catch (NumberFormatException nfe) {
            log.error("DotHistory: not ints: " + dotHistory + " from " + remoteHost);
            ints = null;
            return;
        } catch (IllegalArgumentException iae) {
            log.error("DotHistory: unparseable from " + remoteHost); // exception will carry dotHistory
            ints = null;
            return;
        }

        // ints[0] == ints.length

        ix = 0;

        if (getArraySize() == 0) {
            // self-validates
            ints = null;
            return;
        }

        // FROM phodots.js.summarizeDots():
        //
        //  var dh = [];
        //
        //  dh.push(-1 * d2angleHist.length);
        //  Array.prototype.push.apply(dh, d2angleHist);
        //
        //  dh.push(-1 * d3angleHist.length);
        //  Array.prototype.push.apply(dh, d3angleHist);

        int len = getArraySize();
        if (len == 0) {
            return;
        }

        this.d2angleHist = new int[len];

        for (int i=0; i<len; i++) {
            this.d2angleHist[i] = ints[ix++];
        }

        len = getArraySize();
        if (len == 0) {
            return;
        }

        this.d3angleHist = new int[len];

        for (int i=0; i<len; i++) {
            this.d3angleHist[i] = ints[ix++];
        }

        //  dh.push(-1 * dists.length);
        //  for (var i=0; i<dists.length; i++) {
        //    dh.push(dists[i]);
        //  }
        // WHERE
        //  var D_SZ = 100;
        //  var d = Math.round(dot.dist);
        //  if (d > 0 && d < D_SZ) dists[d]++;
        //  else if (d != 0  &&  !Number.isNaN(d)) {
        //    console.log('outlier ' + d);
        //    dists[0]++;
        //  }

        len = getArraySize();
        if (len == 0) {
            log.warn("This could indicate a wrong phobasic.js version");
            return;
        }

        this.distHist = new int[len];

        for (int i=0; i<len; i++) {
            this.distHist[i] = ints[ix++];
        }

        // dh.push(-1 * velHist.length);
        // Array.prototype.push.apply(dh, velHist);

        len = getArraySize();
        if (len == 0) {
            return;
        }

        this.velocityHist = new int[len];

        for (int i=0; i<len; i++) {
            this.velocityHist[i] = ints[ix++];
        }

        //  dh.push(-2);  // else old records continuing at -8
        //  dh.push(notDots1); // cases in histo set==checksum
        //  dh.push(notDots2); // cases in histo set==checksum

        len = getArraySize();
        if (len == 0) {
            return;
        }
        if (len == 2) {

            // notDots, added 2026_02

            this.notDots1 = ints[ix++];  // cases in histo set
            this.notDots2 = ints[ix++];  // cases in histo set

            //  dh.push(-1 * notDotsX1.length);
            //  Array.prototype.push.apply(dh, notDotsX1);
            //  dh.push(-1 * notDotsY1.length);
            //  Array.prototype.push.apply(dh, notDotsY1);
            //  histogram(notDotsV1, notDotsVL1);
            //  console.log("v1 len " + notDotsV1.length);
            //  dh.push(-1 * notDotsV1.length);
            //  Array.prototype.push.apply(dh, notDotsV1);

            // [XYV]1==pre-dots mouse moves

            len = getArraySize();
            if (len == 0) {
                return;
            }
            this.notDotsX1 = new int[len];
            for (int i=0; i<len; i++) {
                this.notDotsX1[i] = ints[ix++];
            }

            len = getArraySize();
            if (len == 0) {
                return;
            }
            this.notDotsY1 = new int[len];
            for (int i=0; i<len; i++) {
                this.notDotsY1[i] = ints[ix++];
            }

            len = getArraySize();
            if (len == 0) {
                return;
            }
            this.notDotsV1 = new int[len];
            for (int i=0; i<len; i++) {
                this.notDotsV1[i] = ints[ix++];
            }

            // [XYV]2==post-dots mouse moves

            len = getArraySize();
            if (len == 0) {
                return;
            }
            this.notDotsX2 = new int[len];
            for (int i=0; i<len; i++) {
                this.notDotsX2[i] = ints[ix++];
            }

            len = getArraySize();
            if (len == 0) {
                return;
            }
            this.notDotsY2 = new int[len];
            for (int i=0; i<len; i++) {
                this.notDotsY2[i] = ints[ix++];
            }

            len = getArraySize();
            if (len == 0) {
                return;
            }
            this.notDotsV2 = new int[len];
            for (int i=0; i<len; i++) {
                this.notDotsV2[i] = ints[ix++];
            }

            // check; orig size = 50

            Set<Integer> sizes = new HashSet<>();
            sizes.add(notDotsX1.length);
            sizes.add(notDotsY1.length);
            sizes.add(notDotsV1.length);
            sizes.add(notDotsX2.length);
            sizes.add(notDotsY2.length);
            sizes.add(notDotsV2.length);

            if (sizes.size() != 1) {
                log.error("\n\nNotDots histo sizes mismatch:");
                for (int size : sizes) {
                    log.error("\n\t" + size);
                }
                throw new RuntimeException("NotDots histo sizes mismatch");
            }

        } else {

            log.warn("Pre-2026_02 records, notDots section not there. Next: checking for sync on -8 block.");

        }

        //  dh.push(-8);

        if (ints[ix] != -8) {
            log.error("Expected -8! " + ix + "->" + ints[ix] + " from " + remoteHost);
/* DEBUG note
     17 151->-171
     17 151->-24
     17 151->-63
     18 151->-105
     18 151->-39
     18 151->-66
     19 151->-27
     19 151->-81
     20 151->-108
     20 151->-45
     20 151->-57
     20 151->-84
     21 151->-30
     21 151->-48
     21 151->-51
     21 151->-60
     21 151->-75
     22 151->-111
     22 151->-21
     23 151->-54
     24 151->-72
     26 151->-42
     27 151->-33
     31 151->-36
     35 151->-6
     66 151->-3
*/
            for (int i=ix-1; i>-1; i--) {
                if (ints[i] == -8) {
                    log.error("Found -8 earlier, at " + i);
                }
            }
            for (int i=ix+1; i<ints.length; i++) {
                if (ints[i] == -8) {
                    log.error("Found -8 later, at " + i + "/" + ints.length);
                }
            }
            ints = null;
            return;
        }
        ix++;

        //  dh.push(Math.round(rect1.left)); dh.push(Math.round(rect1.right));
        //  dh.push(Math.round(rect1.top)); dh.push(Math.round(rect1.bottom));
        //  dh.push(Math.round(rect2.left)); dh.push(Math.round(rect2.right));
        //  dh.push(Math.round(rect2.top)); dh.push(Math.round(rect2.bottom));

        if (ix + 8 > ints.length - 1) {
            log.error("ran out of ints for dims from " + remoteHost);
            ints = null;
            return;
        }
        this.rect1 = new int[] { ints[ix++], ints[ix++], ints[ix++], ints[ix++] };
        this.rect2 = new int[] { ints[ix++], ints[ix++], ints[ix++], ints[ix++] };

log.info("rect1 is " + this.rect1.length);

        this.center1 = new int[] { this.rect1[0] + (this.rect1[0] - this.rect1[1]) / 2,
                                   this.rect1[2] + (this.rect1[2] - this.rect1[3]) / 2
                                 };
        this.center2 = new int[] { this.rect2[0] + (this.rect2[0] - this.rect2[1]) / 2,
                                   this.rect2[2] + (this.rect2[2] - this.rect2[3]) / 2
                                 };
        double dX = (double) this.center1[0] - this.center2[0];
        double dY = (double) this.center1[1] - this.center2[1];

        this.centersDist = (int) Math.sqrt(dX * dX + dY * dY);
log.info("WWWWW WWWW WW centersDist " + centersDist);
        //  dh.push(-1 * dotCount * 3);
        //  for (var i=0; i<dotCount; i++) {
        //    var dot = dotList[i];
        //    dh.push(Math.round(dot.x));
        //    dh.push(Math.round(dot.y));
        //    var dt = dot.time - dots_t0;
        //    ** flag mouseUp w/ negative time
        //    if (dot.mouseUp) dt *= -1;
        //    dh.push(dt);
        //  }

        // this verifies the size and
        // leaves ix poised
        len = getArraySize();
        if (len == 0) {
            ints = null;
            return;
        }
        if (len / 3 != hp.dotCount) {
            ints = null;
            return;
        }

        log.info("DOTS: d3angleHist: " + d3angleHist.length +
                      " d2angleHist: " + d2angleHist.length +
                      " distHist: " + distHist.length +
                      " velHist: " + velocityHist.length +
                      " notDotsX1: " + notDotsX1.length +
                      " notDotsY1: " + notDotsY1.length +
                      " notDotsV1: " + notDotsV1.length);

        // call analyzeDots()
        //      to use dots
    }

    /*
    **  dotIn - box[] is { left,right,top,bottom }
    */
    private String dotIn(int[] box, int dotX, int dotY, boolean logFail) {

        if (box == null) {
            log.error("dotIn: box is null");
            return null;
        }
        if (box.length != 4) {
            log.error("dotIn: box length not 4: " + box.length);
            return null;
        }

        // absolute top = 0
        if (dotX < box[0]  ||  dotX > box[1]  ||
            dotY < box[2]  ||  dotY > box[3]) {

            if (logFail) {
                log.info("dotIn=no: dotX " + dotX +
                         " l " + box[0] + " r " + box[1]);
                log.info("dotIn=no: dotY " + dotY +
                         " t " + box[2] + " b " + box[3]);
            }

//if (dotX < box[0]) log.info("dotX < box[0]");
//if (dotX > box[1]) log.info("dotX > box[1]");
//if (dotY > box[2]) log.info("dotY > box[2]");
//if (dotY < box[3]) log.info("dotY < box[3]");
            return null;
        }
        String ninth;

        int dX = box[1] - box[0];
        int dX1 = dX / 3;
        int dX2 = dX1+dX1;

        if (dotX < box[0] + dX1) {
            ninth = "0";
        } else if (dotX < box[0] + dX2) {
            ninth = "1";
        } else {
            ninth = "2";
        }

        int dY = box[3] - box[2];
        int dY1 = dY / 3;
        int dY2 = dY1+dY1;
        if (dotY < box[2] + dY1) {
            ninth += "0";
        } else if (dotY < box[2] + dY2) {
            ninth += "1";
        } else {
            ninth += "2";
        }

        return ninth;

    }

    private double dotMapFrac = -1.0;
    //private float[][] dfPoints = null;
    private double[][] dfPoints = null;
    private double distRatio = 1.0;


    /*
    **  analyzeDots - the web client provided histograms,
    **      here we do more, not sure where to do what.
    **
    **      analyzeDots() isn't done at construction
    **          (on receipt of every request) since
    **          it may not be needed.
    */

    public void analyzeDots() {

        if (ints == null) {
            log.info("analyzeDots - nope");
            return;
        }

        // copy the dot coords to { {x1,y1}, {x2,y2},...}
        //       of float for analysis

        //dfPoints = new float[hp.dotCount][];
        dfPoints = new double[hp.dotCount][];

        // come up with a well-distributed fraction
        // using dot info
        // a rough stab to start gathering training data
        // for a dynamic personality

        // do the dots

        int huh = 0;

        HashCount p1ninths = new HashCount();
        HashCount p2ninths = new HashCount();

        int lastP = -1;

        for (int i=0; i<hp.dotCount; i++) {

            int dotX = ints[ix++];
            int dotY = ints[ix++];
            int dt   = ints[ix++];

            dfPoints[i] = new double[2];
            dfPoints[i][0] = dotX;
            dfPoints[i][1] = dotY;
            //dfPoints[i] = new float[2];
            //dfPoints[i][0] = (float) dotX;
            //dfPoints[i][1] = (float) dotY;

            if (dt < 0) {
                mouseUps++;
                dt *= -1;
            }

            if (rect1 == null) {
                log.error("dotIn: rect1 is null");
            }

            String ninth = dotIn(rect1, dotX, dotY, false);
            if (ninth != null) {
//log.info("CROSS in 1");
                dots1++;
                p1ninths.add(ninth);
                if (lastP != -1  &&  lastP != 1) {
                    lastCrossings++;
                }
                lastP = 1;
            } else {

                ninth = dotIn(rect2, dotX, dotY, true);
                if (ninth == null) {
                    huh++;
                } else {
//log.info("CROSS in 2");
                    dots2++;
                    p2ninths.add(ninth);
                    if (lastP != -1  &&  lastP != 2) {
                        lastCrossings++;
                    }
                    lastP = 2;
                }
            }
        }

        if (huh > 0) {
            log.warn("analyzeDots: HUH dots: " + huh +
                          "/" + hp.dotCount +
                          " CROSS " + lastCrossings);
        }
        if (dots1 + dots2 == 0) {
            log.warn("No dots in pics: mouseUps " + mouseUps);
        }

        double pRat = 0.0;
        if (dots1 > 0  &&  dots2 > 0) {
            pRat = (double) dots1 / dots2;
            if (mouseUps > 1) {
                pRat /= mouseUps;
            }
        }
        log.info("analyzeDots: " +
                        "\n|\t  dots: " + hp.dotCount +
                        "\n|\t    p1: " + dots1 + " p2: " + dots2 +
                        "\n|\t    UPs " + mouseUps +
                            " CROSS " + lastCrossings +
                        // "\n|\t    pRat " + pRat +
                        "\n|\t  box1: " + Arrays.toString(rect1) +
                        "\n|\t  box2: " + Arrays.toString(rect2) +
                        "\n|\t  Ninths: key is [column][row]" +
                        "\n|\t  p1 ninths: " + p1ninths.toString("|\t\t") +
                        "\n|\t  p2 ninths: " + p2ninths.toString("|\t\t"));

        // chop first range

        double first_frac = 0.0;
        double next_frac = 0.0;

        // TODO - all the work on case 0: extend to others? chop?

        switch (lastCrossings) {

            case 0:

                // assuming side-by-side,
                // less compelling for v, TODO

                if (hp.dotStartScreen == 1) {

                    int far =   p1ninths.getCount0("00") +
                                p1ninths.getCount0("01") +
                                p1ninths.getCount0("02");
                    int mid =   p1ninths.getCount0("10") +
                                p1ninths.getCount0("11") +
                                p1ninths.getCount0("12");
                    int close = p1ninths.getCount0("20") +
                                p1ninths.getCount0("21") +
                                p1ninths.getCount0("22");

                    int top = -1;
                    double topRatio = -1.0;
                    if (far > mid  &&  far > close) {
                        top = 1;
                        int t = mid + close;
                        if (t > 0) {
                            topRatio = (double) far / t;
                        }
                    } else if (mid >= far) {
                        if (close > mid) {
                            top = 3;// close
                            int t = mid + far;
                            if (t > 0) {
                                topRatio = (double) close / t;
                            }
                        } else {
                            top = 2;// mid
                            int t = close + far;
                            if (t > 0) {
                                topRatio = (double) mid / t;
                            }
                        }
                    } else if (close >= far) {
                        top = 3;// close
                        int t = mid + far;
                        if (t > 0) {
                            topRatio = (double) close / t;
                        }
                    } else {
log.error("analyzeDots: HUH");
                    }
/*
                    log.info("analyzeDots: nocross startscrn1: " +
                                    " far/mid/close: " +
                                    far + "/" + mid + "/" + close +
                                    " top " + top +
                                    " topRatio " + topRatio);
*/
                    switch (top) {

                        case 1: // far

                            if (topRatio < 0.0) { // single slice
                                next_frac = 0.3;
                            } else if (topRatio > 1.0) { // tight
                                next_frac = 0.4;
                            } else { // loose
                                next_frac = 0.5;
                            }
                            break;

                        case 2: // center

                            if (topRatio < 0.0) { // single slice
                                first_frac = 1.0/3.0;
                                next_frac = 2.0/3.0;
                            } else if (topRatio > 1.0) { // tight
                                first_frac = 0.25;
                                next_frac = 0.75;
                            } else { // loose
                                first_frac = 0.2;
                                next_frac = 0.8;
                            }
                            break;

                        case 3: // inside

                            if (topRatio < 0.0) { // single slice

                                // inside: weigh rows
                                int tup = p1ninths.getCount0("20");
                                int half = p1ninths.getCount0("21");
                                int bot = p1ninths.getCount0("22");
                                int best = 1;
                                if (half > tup) {
                                    if (bot > half) {
                                        best = 3;
                                    } else {
                                        best = 2;
                                    }
                                } else if (bot > tup) {
                                    best = 3;
                                }
                                //log.info("analyzeDots: inner, best row=" + best);
                                switch (best) {
                                    case 1:
                                    case 3:
                                        first_frac = 0.33;
                                        next_frac = 1.0;
                                        break;
                                    case 2:
                                    default:
                                        first_frac = 0.5;
                                        next_frac = 1.0;
                                        break;
                                }
                            } else if (topRatio > 1.0) { // tight
                                first_frac = 0.33;
                                next_frac = 0.66;
                            } else { // loose
                                first_frac = 0.2;
                                next_frac = 0.8;
                            }
                            break;

                        default:
                            throw new
                                  RuntimeException("Why?? Why not??");
                    }
                } else {

                    int far =   p2ninths.getCount0("20") +
                                p2ninths.getCount0("21") +
                                p2ninths.getCount0("22");
                    int mid =   p2ninths.getCount0("10") +
                                p2ninths.getCount0("11") +
                                p2ninths.getCount0("12");
                    int close = p2ninths.getCount0("00") +
                                p2ninths.getCount0("01") +
                                p2ninths.getCount0("02");

                    int top = -1;
                    double topRatio = -1.0;
                    if (far > mid  &&  far > close) {
                        top = 1;
                        int t = mid + close;
                        if (t > 0) {
                            topRatio = (double) far / t;
                        }
                    } else if (mid >= far) {
                        if (close > mid) {
                            top = 3;// close
                            int t = mid + far;
                            if (t > 0) {
                                topRatio = (double) close / t;
                            }
                        } else {
                            top = 2;// mid
                            int t = close + far;
                            if (t > 0) {
                                topRatio = (double) mid / t;
                            }
                        }
                    } else if (close >= far) {
                        top = 3;// close
                        int t = mid + far;
                        if (t > 0) {
                            topRatio = (double) close / t;
                        }
                    } else {
log.error("analyzeDots: HUH2");
                    }
/*
                    log.info("analyzeDots: nocross startscrn2: " +
                                    " far/mid/close: " +
                                    far + "/" + mid + "/" + close +
                                    " top " + top +
                                    " topRatio " + topRatio);
*/
                    switch (top) {
                        case 1: // far
                            if (topRatio < 0.0) { // single slice
                                next_frac = 0.3;
                            } else if (topRatio > 1.0) { // tight
                                next_frac = 0.4;
                            } else { // loose
                                next_frac = 0.5;
                            }
                            break;
                        case 2: // center
                            if (topRatio < 0.0) { // single slice
                                first_frac = 1.0/3.0;
                                next_frac = 2.0/3.0;
                            } else if (topRatio > 1.0) { // tight
                                first_frac = 0.25;
                                next_frac = 0.75;
                            } else { // loose
                                first_frac = 0.2;
                                next_frac = 0.8;
                            }
                            break;

                        case 3: // inside

                            if (topRatio < 0.0) { // single slice

                                // inside: weigh rows
                                int tup = p2ninths.getCount0("20");
                                int half = p2ninths.getCount0("21");
                                int bot = p2ninths.getCount0("22");

                                int best = 1;
                                if (half > tup) {
                                    if (bot > half) {
                                        best = 3;
                                    } else {
                                        best = 2;
                                    }
                                } else if (bot > tup) {
                                    best = 3;
                                }
                                //log.info("analyzeDots: inner, best row=" + best);
                                switch (best) {
                                    case 1:
                                    case 3:
                                        first_frac = 0.33;
                                        next_frac = 1.0;
                                        break;
                                    case 2:
                                    default:
                                        first_frac = 0.5;
                                        next_frac = 1.0;
                                        break;
                                }
                            } else if (topRatio > 1.0) { // tight
                                first_frac = 0.33;
                                next_frac = 0.66;
                            } else { // loose
                                first_frac = 0.2;
                                next_frac = 0.8;
                            }
                            break;

                        default:
                            throw new
                                  RuntimeException("Why2?? Why not??");
                    }
                }
                break;

            case 1:  // lastcrossings

                // direction counts

                if (hp.dotStartScreen == 1) {

                    next_frac = 1.0 / 3.0;

                } else {

                    first_frac = 2.0 / 3.0;
                    next_frac = 1.0;

                }
                break;

            case 2:  // lastcrossings

                next_frac = 0.25;
                break;

            case 4:  // lastcrossings

                next_frac = 0.5;
                break;

            case 3:  // lastcrossings

                first_frac = 0.75;
                next_frac = 1.0;
                break;

            case 5:  // lastcrossings

                first_frac = 0.5;
                next_frac = 1.0;
                break;

            default: // > 5

                first_frac = 0.1;
                next_frac = 0.9;
                break;
        } // switch(lastCrossings)
/*
        log.info("analyzeDots: round 1 FRACS " +
                    first_frac + " " + next_frac);
*/
        if (next_frac - first_frac < 0.2) {
            throw
                new RuntimeException("analyzeDots round1 next-frac < 0.2 " +
                            " (f/nf): " + first_frac + " " + next_frac);
        }

        double frac = first_frac;

        // divide interval into 3

        double avail_third = (next_frac - first_frac) / 3.0;

        // Divide 'avail' into 3ds

        // 1 - net distance / full distance

        // 0.0000x .. 1
        if (hp.dotDist > 0) {
            distRatio = (double) hp.dotVecLen /   //Math.sqrt((double) hp.dotVecLen) /
                                               hp.dotDist;
        }
        //double dRat2 = Math.sqrt(dRat);// for fun?
        //log.info("analyzeDots lenrat " + distRatio);

        frac += distRatio * avail_third;

        // 2 - maxVel, maxAcc, maxJerk

        int tot = hp.dotMaxVel + hp.dotMaxAccel + hp.dotMaxJerk;
        double circIsh = Math.PI * tot * tot;
        circIsh -= (int) circIsh;
/*
        log.info("analyzeDots final vaj " + hp.dotMaxVel + " " +
                              hp.dotMaxAccel + " " +
                              hp.dotMaxJerk +
                              " -> " + circIsh);
*/
        frac += circIsh * avail_third;

        // 3 - dotCount, clickTime

        double dcCt;

        if (hp.clickTime > 0) {
            // dots/sec
            dcCt = (double) (1000*hp.dotCount) / hp.clickTime;
            log.info("analyzeDots: dots/sec: " + dcCt);
        } else if (hp.userTime > 0) {
            dcCt = (double) hp.dotCount / hp.userTime;
        } else {
            dcCt = (double) hp.dotCount / hp.dotDist;
        }
        if (dcCt > 1.0) {
            //log.info("analyzeDots flip dotCount");
            dcCt -= (int) dcCt;
        }
/*
        log.info("analyzeDots ct/time " + hp.clickTime +
                     "/" + hp.dotCount +
                     " -> " + dcCt);
*/

        frac += dcCt * avail_third;
/*
        log.info("analyzeDots clk " + hp.clickTime + " " +
                      hp.dotCount + " -> " + dcCt + " -> " + frac);

        log.info("analyzeDots: " + frac +
                 " range [" + first_frac + " .. " + next_frac + "]: " +
                 " third " + avail_third +
                 " distRatio " + distRatio +
                 " vaj " + circIsh +
                 " dots+time " + dcCt);
*/
        if (frac == Double.NaN) {
            log.error("analyzeDots frac NaN " + frac);
            frac = Math.PI * System.currentTimeMillis();;
            frac -= (int) frac;
        } else if (frac < 0.0) {
            log.error("analyzeDots frac neg " + frac);
            frac *= -1.0;
        }
        if (frac > 1.0) {
            //log.info("analyzeDots chop frac >1 " + frac);
            frac -= (int) frac;
        }
        dotMapFrac = frac;
    }

    private int selectRandomDistributedValue(int[] hist, int limit) {

        if (hist == null  ||  hist.length == 0) {
            throw new RuntimeException(
                               "selectRandomDistributedValue: no hist");
        }
        if (hist.length == 1) {
            return 0;
        }
        if (limit == -1  ||  limit > hist.length) {
            limit = hist.length;
        }
        double sum = 0.0;

        for (int i=0;i<limit;i++) {
            sum += hist[i];
        }
        double d = rand.nextDouble();
        double target = sum * d;
        int choice = 0;
        target -= hist[choice];
        while (target > 0.0) {
            choice++;
            if (choice == hist.length) {
                choice--;
                break;
            }
            target -= hist[choice];
        }
        //log.info("RANDOM (" + choice + "/" + limit + ") " +
        //                  target + "/" + sum + " " + d);
        return choice;
    }

    /*
    **  return 0..n-1
    **      Placeholder for gathering training data,
    **      then replace with models.
    */
    public int dotMap(int n) {

        if (dotMapFrac < 0.0) {
            analyzeDots();
        }

        // try histograms bigger than n first

        List<String> hists = new ArrayList<>();

        if (n < this.d3angleHist.length) {
            hists.add("d3angle");    // 38
        }
        if (n < this.d2angleHist.length) {
            hists.add("d2angle");    // 38
        }
        if (n < this.distHist.length) {
            hists.add("dist");     // 100
        }
        if (n < this.velocityHist.length) {
            hists.add("velocity"); // 100
        }

        if (hists.size() > 0) {

            //log.info("dotMap: hists: " + hists.size());
            int f = (int) (dotMapFrac * (double) hists.size());
            String h = hists.get(f);

            int[] hist = null;
            if ("d3angle".equals(h)) hist = this.d3angleHist;
            else if ("d2angle".equals(h)) hist = this.d2angleHist;
            else if ("dist".equals(h)) hist = this.distHist;
            else if ("velocity".equals(h)) hist = this.velocityHist;
            if (hist != null) {
                int choice = selectRandomDistributedValue(hist, n);

                log.info("dotMap: choice " + choice + "/" + n +
                                " from [" + h + "] histogram size " + hist.length);
                return choice;
            }
        }
/*
        if (n < 5) {
            // forgot why.. goes to 0?
            int choice = (int) (System.currentTimeMillis() % n);
            log.error("dotMap <5: " + n + " choice(t)=" + choice);
            return choice;
        }
*/

        int choice = (int)(dotMapFrac * (double) n);

        log.info("dotMap: " + choice + "/" + n +
                    " using frac " + dotMapFrac);

        return choice;
    }

    public int[] histFromDotCount() {

        switch (hp.dotCount % 4) {
        case 0:
            return d2angleHist; // 36
        case 1:
            return d3angleHist; // 36
        case 2:
            return distHist; // 51
        case 3:
        default:
            return velocityHist; // 51
        }
    }

    public boolean roundish() {

        //log.info("Circle: dots " + hp.dotVecLen + " cum.dist " + hp.dotDist);

        if (dfPoints == null) {
            analyzeDots();
        }
        if (dfPoints == null) {
            log.warn("roundish(): no dfPoints, default false");
            return false;
        }
        if (dfPoints.length < 5) {
            log.warn("roundish(): <5 dfPoints, default false");
            return false;
        }

        double[] fit = null;

        try {
            fit = FitCircle.taubinSVD(dfPoints);
        } catch (IllegalArgumentException iae) {
            log.error("FitCircle.taubinSVD: false w/points=" + dfPoints.length +
                        ": " + iae);
            return false;
        }

        double a = fit[0];
        double b = fit[1];
        double r = fit[2];

        if (r > hp.dotDist) {
            log.info("\nCircle false r>dist " + (int)r + "  " + hp.dotDist);
            return false;
        }
        double circumference = 6.28 * r; // 2pi * r
        if (hp.dotDist < 0.9 * circumference  ||
            hp.dotDist > 1.1 * circumference) {
            log.info("\nCircle false circumference~dist " + (int)circumference + "  " + hp.dotDist);
            return false;
        }
        log.info("\nCircle true [r, circumference, dist  " + (int)r + "  " + (int)circumference + "  " + hp.dotDist);
        return true;
/*
        double negV = 0.0;
        double posV = 0.0;
        double max = 0.0;

        for (double[] p : dfPoints ) {

            double da = a - (double) p[0];
            double db = b - (double) p[1];
            double dr = Math.sqrt(da*da + db*db) - r;
            if (dr < 0) negV += dr;
            else posV += dr;
            double ad = Math.abs(dr);
            if (ad > max) max = ad;

            // if (negVar + posVar > r) log.info("Circle Quit it? " + negVar + " + " + posVar + " > " + r);
        }

        double c1 = (posV + -1.0 * negV) / dfPoints.length;
        double c2 = Math.abs(negV + posV) / dfPoints.length;
        double c3 = Math.abs(negV * posV) / dfPoints.length;

        //double criterion = Math.abs(negV + posV) / dfPoints.length;
        //double rfac = r / 4;

        if (c1 < r * 0.3) {
            log.info("\nCircle true c1, r: " + (int)c1 + " " + (int)r);
            return true;
        }

        log.info("\nCircle false r/max/dist " + r + "/" + max + "/" + hp.dotDist + "   c's:  " + c1 + "  " + c2 + "  " + c3);
        return false;
*/
    }

    public String toString() {
        return "DotHistory: input [" + inputString + "]";
    }

/*
     *  tried for fun, likely my bad test, but tested ugly
     *  (e.g. straight line gave differing results); unanalyzed generative AI.
     *
     *      google search: "java" taubin circle, comments:
     *
     *      The Taubin method is an algorithm used to fit a circle to a set of
     *      2D points. It is considered an algebraic fit method and is known
     *      for its accuracy and speed, particularly with small arcs. It is
     *      often preferred over other methods like the Kasa fit, especially
     *      when dealing with noisy or incomplete data.
     *      Here's a basic outline of how the Taubin method can be implemented
     *      in Java:
     *      Data Input:
     *          The method takes an array of 2D points (x, y) as input.
     *          These points represent the data to which the circle will be fitted.
     *      Calculate Sums:
     *          Calculate the sums of various powers and products of the x and y
     *          coordinates of the input points. These sums are used in subsequent calculations.
     *      Form the Scatter Matrix:
     *          Construct a scatter matrix using the calculated sums. This matrix
     *          represents the distribution of the data points.
     *      Solve the Eigenvalue Problem:
     *          Find the eigenvector corresponding to the smallest eigenvalue of
     *          the scatter matrix. This eigenvector is related to the circle's parameters.
     *      Calculate Circle Parameters:
     *          Extract the circle's center coordinates (a, b) and radius (r) from
     *          the eigenvector. These parameters define the fitted circle.
     *      Output:
     *          The method returns the calculated circle parameters (a, b, r).
     *      Java

    public boolean taubinFit() { //

        if (isRound != null) return isRound;

        if (floatPoints == null) {
            analyzeDots();
        }

        //float[][] points = floatPoints;
        double[][] points = dfPoints;

        int n = points.length;
        double sumX = 0, sumY = 0, sumX2 = 0, sumY2 = 0, sumX3 = 0, sumY3 = 0, sumXY = 0, sumX1Y2 = 0, sumX2Y1 = 0;

        for (int i = 0; i < n; i++) {
            double x = (double) points[i][0];
            double y = (double) points[i][1];
            sumX += x;
            sumY += y;
            sumX2 += x * x;
            sumY2 += y * y;
            sumX3 += x * x * x;
            sumY3 += y * y * y;
            sumXY += x * y;
            sumX1Y2 += x * y * y;
            sumX2Y1 += x * x * y;
        }

        double Mxx = sumX2 / n - Math.pow(sumX / n, 2);
        double Myy = sumY2 / n - Math.pow(sumY / n, 2);
        double Mxy = sumXY / n - (sumX / n) * (sumY / n);
        double Mxxx = sumX3 / n - 3 * sumX2 / n * sumX / n + 2 * Math.pow(sumX / n, 3);
        double Myyy = sumY3 / n - 3 * sumY2 / n * sumY / n + 2 * Math.pow(sumY / n, 3);
        double Mxxy = sumX2Y1 / n - 2 * sumX / n * sumXY / n - sumX2 / n * sumY / n + 2 * Math.pow(sumX / n, 2) * sumY / n;
        double Myxx = sumX1Y2 / n - 2 * sumY / n * sumXY / n - sumY2 / n * sumX / n + 2 * Math.pow(sumY / n, 2) * sumX / n;

        final double a = (Myyy + Mxx * sumY / n + Mxxy + Myy * sumY / n) / (2 * (Mxx + Myy));
        final double b = (Mxxx + Myy * sumX / n + Myxx + Mxx * sumX / n) / (2 * (Mxx + Myy));
        final double r = Math.sqrt(Mxx + Myy + a * a + b * b);

        // was: return new float[] { a, b, r };

        double negVar = 0;
        double posVar = 0;

        for (double[] p : dfPoints ) {

            double da = a - (double) p[0];
            double db = b - (double) p[1];
            double dr = Math.sqrt(da*da + db*db) - r;
            if (dr < 0) negVar += dr;
            else posVar += dr;

            // if (negVar + posVar > r) log.info("Circle Quit it? " + negVar + " + " + posVar + " > " + r);
        }

        double criterion = Math.abs(negVar + posVar) / floatPoints.length;
        double rfac = r / 4;

        log.info("Circle crit " + criterion + " rfac " + rfac + " (a,b,r) " + a + "," + b + "," + r);
        return criterion < rfac;
    }
*/
}
