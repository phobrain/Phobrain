package org.phobrain.db.record;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **   Screen - place for a picture
 **/

import java.sql.Timestamp;

public class Screen {

    public long browserId;
    public long sessionId; // obsolete away asap
    public int id;
    public int depth = 0;
    public String orientation;
    public String id_s;
    public int archive; // indirectly set
    public String selMethod = null;
    public long showingId;
    public Timestamp time; // only set in Dao

    public Object pr = null; // convenience

    // get crazy with names

    public Screen(long bid, int id, String o, String id_s, 
                  Object pr) {
        if (id_s == null) {
            archive = -1;
        } else {
            try {
                String ss[] = id_s.split("/");
                this.archive = Integer.parseInt(ss[0]);
            } catch (Exception e) {
                throw new RuntimeException("Just unlucky I guess: " + id_s);
            }
        }
        if (pr != null) {
            PictureResponse ppr = (PictureResponse) pr;
            this.selMethod = ppr.method;
        }

        this.browserId = bid;
        this.sessionId = -1; // was sid;
        this.id = id;
        this.orientation = o;
        this.id_s = id_s;
        this.showingId = -1; // was shid;
        this.pr = pr;
    }
}

