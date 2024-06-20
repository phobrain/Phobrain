package org.phobrain.db.record;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

public class Pair implements Comparable {

    public String id1;
    public String id2;

    public long sortVal = -1;

    public int kwdVal = -1;
    public int[]  values;

    public boolean idsMatch(String id1, String id2) {
        return this.id1.equals(id1) && this.id2.equals(id2);
    }

    public Pair(String id1, String id2) {
        this.id1 = id1;
        this.id2 = id2;
    }

    @Override
    public int compareTo(Object oo) {
        Pair ap = (Pair) oo;
        if (this.sortVal > ap.sortVal) return 1;
        if (this.sortVal < ap.sortVal) return -1;
        return 0;
    }
}

