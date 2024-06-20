package org.phobrain.db.record;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

import java.util.List;
import java.sql.Timestamp;

public class Session {

    // dynamic, non-db
    public int seenPct = 0;
    public String curator = null;

    // db

    public long id;
    public Timestamp createTime;
    public String tag;
    public String host;
    public long browserID;
    public int hour;
    public int tzoff;
    public String lang;
    public String platform;
    public String user;
    public String kwdChoice;
    public boolean repeatPics = false;
    public List<Screen> screens = null;

    public Screen getScreen(int id, int depth) {
        if (screens == null) {
            return null;
        }
        for (Screen s : screens) {
            if (s.id == id  &&  s.depth == depth) {
                return s;
            }
        }
        return null;
    } 
}

