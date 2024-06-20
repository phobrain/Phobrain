package org.phobrain.db.record;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  ApprovedPair
 **/

import java.util.Set;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;

public class ApprovedPair extends Pair {

    // from Pair:
    //  public String id1;
    //  public String id2;
    //  public long sortVal = -1;

    public Timestamp createTime;
    public long browserID;
    public String curator;
    public int status;
    public String idP; // id1|id2
    public boolean vertical;
    public boolean hasKwd = false; // manual kwds replaced by imagenet
    public boolean matchPeople; // pr0.people == pr1.people
    public long d0; // nn val of the month from pairs_[vh].d0

    // values averaged over pics

    public int r, g, b;
    public int ll; // L from Lab
    public int rgbRadius;
    public int labContrast;
    // public int fracDim; dropping 9/9/2020
    public int density;

    // non-db optional values

    public String otherId = null; // set by Dao when querying on an id
    public boolean otherFirst = false;

    public Set<String> noGeomKwds;
    public Set<String> geomKwds;

    public Set<String> id1Kwds;
    public Set<String> id2Kwds;

    public ApprovedPair(String id1, String id2) {
        super(id1, id2);
        this.idP = id1 + "|" + id2;
    }

    public String toString() {
        return "ApprovedPair: " + id1 + "_" + id2 + 
                " curator: " + curator +
                " status: " + status +
                " create: " + (createTime == null ?
                            "null" : 
                            new SimpleDateFormat("MM/dd/yyyy").format(createTime));
    }           
}

