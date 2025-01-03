package org.phobrain.db.record;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

import org.phobrain.db.record.DotHistory;
import org.phobrain.util.AtomSpec;

import java.sql.Timestamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class HistoryPair {

    private static final Logger log = LoggerFactory.getLogger(HistoryPair.class);

    public long         id;
    public Timestamp    createTime;
    public long         browserID;
    public int          screen;
    public int          callCount;
    public int          orderInSession;

    public String       id1;
    public int          archive1;
    public String       fileName1;
    public String       selMethod1;

    public String       id2;
    public int          archive2;
    public String       fileName2;
    public String       selMethod2;

    public boolean      vertical;
    public int          pairRating;
    public int          ratingScheme;
    public Timestamp    rateTime;
    public int          bigTime; // browser-side
    public int          bigStime; // server-side, including delay
    public int          userTime;
    public int          userTime2;  // time to maxV
    public int          clickTime;  // time drawing dots
    public int          watchDotsTime;
    public int          mouseDownTime; // last mouseDown
    public int          mouseDist;
    public int          mouseDist2; // dist to maxV
    public int          mouseDx;
    public int          mouseDy;
    public int          mouseVecx;
    public int          mouseVecy;
    public int          mouseMaxv;
    public int          mouseMaxa;
    public int          mouseMina;
    public int          mouseMaxj;
    public int          loadTime;
    public int          pixInPic; // mouse positions recorded while over pic
    public int          pixOutPic; // mouse positions recorded while not over pic
    public String       picClik; // area in pic clicked

    public int          dotStartScreen;
    public int          dotEndScreen;
    public int          dotCount; // pixInPic where mouse button was down
    public int          dotDist; // net distance (squared) if dotCount > 1
    public int          dotVecLen; // net distance if dotCount > 1
    public int          dotVecAng; // net direc, dotCount > 1: clockwise from 3
    public int          dotMaxVel; // fastest do-dot
    public int          dotMaxAccel; // fastest do-dot-dot
    public int          dotMaxJerk; // fastest do-dot

    public String       selMethod;
    public AtomSpec     atomImpact;
    public float        impactFactor;

    // Toggling data is collected but not used.
    //  Toggling refers to clicking next to a photo
    //  to flip back and forth with the previous,
    //  and under the photos to flip both together
    //  for criss-cross effect.

    public int          nTogs;
    public String       togSides;
    public String       toggleTStr; // raw base64
    public int[]        togTimes; // parsed toggleTStr

    public int[]        locSpec; // not stored
    public DotHistory   dotHistory; // not stored TODO

    public int          tmpInt; // not in db; initially for draw/screens pattern

    public void analyzeDots() {

        if (dotHistory == null) {
            log.info("HistoryPair.analyzeDots: no dotHistory");
            return;
        }
        if (dotStartScreen == 0) {
            log.info("HistoryPair.analyzeDots: startScreen==0");
            return;
        }

        dotHistory.analyzeDots();
    }

    public int dotMap(int n) {

        if (dotHistory == null) {
            return (int) (System.currentTimeMillis() % n);
        }
        return dotHistory.dotMap(n);
    }

    @Override
    public String toString() {
        return "HistoryPair." + id + ": " + id1 + " " + id2 + " - " + pairRating;
    }
}

