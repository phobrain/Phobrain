package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  ListHolder: a holder for lists of ids, vals.
 **
 **     It could be a list of [id, val] objects, except the vals
 **     are useful to have in their own list for randomized auctions.
 **
 */

import java.io.Serializable;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

public class ListHolder extends Lists implements Serializable {

    public List<String> id2_l = new ArrayList<>();
    public List<Long> value_l = new ArrayList<>();
    public List<Double> dbl_l = null;

    public int size() { return id2_l.size(); }

    public ListHolder copy() {

        ListHolder ret = new ListHolder();
        ret.id2_l = new ArrayList(this.id2_l);
        ret.value_l = new ArrayList(this.value_l);
        return ret;
    }

    public void add(ListHolder l) {

        for (int i=0; i<l.id2_l.size(); i++) {

            String id = l.id2_l.get(i);

            if (!this.id2_l.contains(id)) {
                this.id2_l.add(id);
                long val = l.value_l.get(i);
                this.value_l.add(val);
            }
        }
    }

    public String remove(int i) {

        if (i > this.size()-1) {
            return null;
        }
        String s = this.id2_l.remove(i);
        this.value_l.remove(i);
        if (this.dbl_l != null) {
            this.dbl_l.remove(i);
        }
        return s;
    }

    @Override
    public String toString() {
        return "ListHolder ids: " + Arrays.toString(this.id2_l.toArray());
    }

}
/*

    private static String[] sortIt(int[] as1, int[] as2, String[] ret) {

        if (as1[0] == as2[0]) {
            if (as1[1] < as2[1]) {
                // in order
            } else { // don't worry about ==
                String t = ret[0];
                ret[0] = ret[1];
                ret[1] = t;
            }
        } else if (as1[0] < as2[0]) {
            // in order
        } else { // as1[0] > as2[0]
            String t = ret[0];
            ret[0] = ret[1];
            ret[1] = t;
        }

        return ret;
    }
*/
