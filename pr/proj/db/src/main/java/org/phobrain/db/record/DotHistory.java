package org.phobrain.db.record;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

// 2024_02 - putting in db.record for easy compile

/**
 **  DotHistory - Parse String from view.html to ints.
 **
 **             view.html:
 **                 function summarizeDots(logit)
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

import org.phobrain.util.MiscUtil;
import org.phobrain.util.HashCount;

import org.phobrain.db.record.ShowingPair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DotHistory {

    private static final Logger log = LoggerFactory.getLogger(DotHistory.class);

    public int lastCrossings = 0;
    public int dots1 = 0;
    public int dots2 = 0;
    public int mouseUps = 0;

    // histograms sized from browser

    public int[] angleHist;    // 38
    public int[] distHist;     // 51
    public int[] velocityHist; // 51

    public int[] rect1;
    public int[] rect2;

    public int[] center1;
    public int[] center2;
    public int centersDist;

    final private ShowingPair sp;
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

            log.error("getArraySize: ints[] not big enough: " +
                        "len=" + len + " ix=" + ix + " ints[]=" + ints.length);
            return 0;
        }

        ix++;
        return len;
    }

    /**
     **  DotHistory - leave ints[] null if it didn't work.
     */
    public DotHistory(ShowingPair sp, String dotHistory) {

        this.sp = sp;
        this.inputString = dotHistory;

        if ("none".equals(dotHistory)  ||  "0".equals(dotHistory)) {
            return;
        }

        try {
            //ints = MiscUtil.base64ToIntArray(dotHistory);
            // seems smaller as comma'd ascii ints
            ints = MiscUtil.parseIntList(dotHistory);
        } catch (NumberFormatException nfe) {
            log.error("DotHistory: not ints: " + dotHistory);
            ints = null;
            return;
        } catch (IllegalArgumentException iae) {
            log.error("DotHistory: unparseable"); // exception will carry dotHistory
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

        // FROM view.html.summarizeDots():
        //
        //  var dh = [];
        //  dh.push(-1 * angleHist.length);
        //  for (var i=0; i<angleHist.length; i++) {
        //    dh.push(angleHist[i]);
        //  }

        int len = getArraySize();
        if (len == 0) {
            return;
        }

        this.angleHist = new int[len];

        for (int i=0; i<len; i++) {
            this.angleHist[i] = ints[ix++];
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

        //  dh.push(-1 * vels.length);
        //  for (var i=0; i<vels.length; i++) {
        //    dh.push(vels[i]);
        //  }

        len = getArraySize();
        if (len == 0) {
            return;
        }

        this.velocityHist = new int[len];

        for (int i=0; i<len; i++) {
            this.velocityHist[i] = ints[ix++];
        }

        //  dh.push(-8);

        if (ints[ix] != -8) {
            log.error("Expected -8! " + ix + "->" + ints[ix]);
            ints = null;
            return;
        }
        ix++;

        //  dh.push(Math.round(rect1.left)); dh.push(Math.round(rect1.right));
        //  dh.push(Math.round(rect1.top)); dh.push(Math.round(rect1.bottom));
        //  dh.push(Math.round(rect2.left)); dh.push(Math.round(rect2.right));
        //  dh.push(Math.round(rect2.top)); dh.push(Math.round(rect2.bottom));

        if (ix + 8 > ints.length - 1) {
            log.error("ran out of ints for dims");
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
        if (len / 3 != sp.dotCount) {
            ints = null;
            return;
        }

        log.info("DOTS: angleHist: " + angleHist.length +
                      " distHist: " + distHist.length +
                      " velHist: " + velocityHist.length);

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

        // come up with a well-distributed fraction
        // using dot info
        // a rough stab to start gathering training data
        // for a dynamic personality

        // do the dots

        int huh = 0;

        HashCount p1ninths = new HashCount();
        HashCount p2ninths = new HashCount();

        int lastP = -1;

        for (int i=0; i<sp.dotCount; i++) {

            int dotX = ints[ix++];
            int dotY = ints[ix++];
            int dt   = ints[ix++];

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
                          "/" + sp.dotCount +
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
                        "\n|\t  dots: " + sp.dotCount +
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

                if (sp.dotStartScreen == 1) {

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

                if (sp.dotStartScreen == 1) {

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
        double distRatio = 1.0;
        if (sp.dotDist > 0) {
            distRatio = Math.sqrt((double) sp.dotVecLen) /
                                               sp.dotDist;
        }
        //double dRat2 = Math.sqrt(dRat);// for fun?
        //log.info("analyzeDots lenrat " + distRatio);

        frac += distRatio * avail_third;

        // 2 - maxVel, maxAcc, maxJerk

        int tot = sp.dotMaxVel + sp.dotMaxAccel + sp.dotMaxJerk;
        double circIsh = Math.PI * tot * tot;
        circIsh -= (int) circIsh;
/*
        log.info("analyzeDots final vaj " + sp.dotMaxVel + " " +
                              sp.dotMaxAccel + " " +
                              sp.dotMaxJerk +
                              " -> " + circIsh);
*/
        frac += circIsh * avail_third;

        // 3 - dotCount, clickTime

        double dcCt;

        if (sp.clickTime > 0) {
            // dots/sec
            dcCt = (double) (1000*sp.dotCount) / sp.clickTime;
            log.info("analyzeDots: dots/sec: " + dcCt);
        } else if (sp.userTime > 0) {
            dcCt = (double) sp.dotCount / sp.userTime;
        } else {
            dcCt = (double) sp.dotCount / sp.dotDist;
        }
        if (dcCt > 1.0) {
            //log.info("analyzeDots flip dotCount");
            dcCt -= (int) dcCt;
        }
/*
        log.info("analyzeDots ct/time " + sp.clickTime +
                     "/" + sp.dotCount +
                     " -> " + dcCt);
*/

        frac += dcCt * avail_third;
/*
        log.info("analyzeDots clk " + sp.clickTime + " " +
                      sp.dotCount + " -> " + dcCt + " -> " + frac);

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
        if (this.angleHist == null) {
            log.error("No angle hist?");
return -1; // ? making exit-proof
//System.exit(1);
}

        // try histograms bigger than n first

        List<String> hists = new ArrayList<>();

        if (n < this.angleHist.length) {
            hists.add("angle");    // 38
            hists.add("dist");     // 100
            hists.add("velocity"); // 100
        } else if (n < this.distHist.length) {
            hists.add("dist");     // 100
            hists.add("velocity"); // 100
        }
        if (hists.size() > 0) {

            //log.info("dotMap: hists: " + hists.size());
            int f = (int) (dotMapFrac * (double) hists.size());
            String h = hists.get(f);
                
            int[] hist = null;
            if ("angle".equals(h)) hist = this.angleHist;
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

    public String toString() {
        return "DotHistory: input [" + inputString + "]";
    }

}
