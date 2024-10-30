package org.phobrain.db.record;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;

import java.sql.Timestamp;

public class Picture implements Comparable {

    public long        xid;
    public Timestamp   createTime;
    public String      id;  // archive/fileName
    public int         archive;
    public String      fileName;
    public int         sequence;
    public int         seq2; // unused
    public String      variationTag;
    public String      variationType;
    public int         sceneSequence = -1;
    public String      sceneType;
    public String      lighting; // day night flash.. (not used)
    public String      angle; // up level down (not used)
    public String      place; // mishmash (not used)
    public String      comments;
    public int         r;
    public int         g;
    public int         b;
    public long        d0Sum = -1;
    public long         d0Sum_l;
    public long         d0Sum_r;
    public long         d0Sum_avg_ok;
    public long         d0Sum_avg_bad;
    public int         dCtrRGB;
    public int         rgbRadius;
    public int         ll;
    public int         aa;
    public int         bb;
    public int         labRadius;
    public int         labContrast;
    public int         dCtrAB;
    public int         angAB;
    public int         dCtr8D;
    public int         dCtr27D;
    public int         dCtr64D;
    //public double      fracDim;
    //public double      fracDev;
    //public double      fracR2;
    public int         width;
    public int         height;
    public boolean     vertical;
    public int         density;
    public boolean     outdoors;
    public boolean     people;
    public boolean     face;
    public boolean     blur;
    public boolean     sign;
    public boolean     number;
    public boolean     blockDisplay;
    public String      blockReason;
    public boolean     pref; // version of seq

    // left/right pair-trained vecs, only valid w/ personal training
    // updated rather than inserted or populated by query(pgsql)
    public String[]   vec_l_fields = null;
    public String[]   vec_r_fields = null;
    public double[]    vec_l;  // non-pgsql
    public double[]    vec_r;  // non-pgsql

    // vgg16_4, dense_4, mob_5, nnl_7,
    public float[]  vgg16_4;
    public float[]  dense_4;
    public float[]  mob_5;
    public float[]  nnl_7;

    public float[] tmp_vec = null; // choosing imagenet vecs

    // color hists for ml: gss + rgb_12, used only for insert
    public float[]     ml_hists; 

    public Map<String, String> props;

    public boolean hasProperty(String prop) {
        return props.get(prop) != null;
    }

    public boolean equals(Object o) {
        if (!(o instanceof Picture)) {
            return false;
        }
        Picture p = (Picture) o;
        if (p.archive != archive) {
            return false;
        }
        if (!p.fileName.equals(fileName)) {
            return false;
        }
        return true;
    }

    public int hashCode() {
        String s = "" + archive + "/" + fileName;
        return s.hashCode();
    }

    //public static class PictureCompare implements Comparator {

        @Override
        public int compareTo(Object o2) {

            Picture p1 = this; //(Picture) o1;
            Picture p2 = (Picture) o2;

            if (p1.equals(p2)) {
                return 0;
            }

            if (p1.archive < p2.archive) {
                return -1;
            }

            if (p1.archive > p2.archive) {
                return 1;
            }

            if (p1.sequence < p2.sequence) {
                return -1;
            }

            if (p1.sequence > p2.sequence) {
                return 1;
            }

            if (p1.seq2 < p2.seq2) {
                return -1;
            }

            if (p1.seq2 > p2.seq2) {
                return 1;
            }

            return p1.fileName.compareTo(p2.fileName);
        }
    //}

    @Override
    public String toString() {
        return "" + archive + "/" + fileName + 
               (variationTag == null ? "" : (" (" + variationTag + ")"));
    }
}

