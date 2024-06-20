package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  TopSelector - Select/output top N. 
 **
 **    For symmetric-valued pairs. TODO: consider binary (vs ascii) in-memory 
 **         approach like PairsBin, below.
 **
 **    ML/asymmetric pairs: Switched to doing it in python, at the source of 
 **         the numbers, predtool.py -top.
 **         2023_03: Switched back to java for parallelism on common memory,
 **         method in new PairsBin.
 **
 **    NOT PARALLEL
 */


import org.phobrain.util.ID;

import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import java.io.PrintStream;

public class TopSelector extends Stdio {

    private final PrintStream tout;
    private final String tag;
    private final boolean topDown;
    private final int targetPairs;

    public TopSelector(PrintStream tout, String tag, int targetPairs, 
                                         boolean topDown) {
        this.tout = tout;
        this.tag = tag;
        this.topDown = topDown;
        this.targetPairs = targetPairs;
    }

    private HashCount seenPic = new HashCount(); // count times each pic seen
    // each pic's sortable <otherpic:distance>:
    private Map<String, List<IdValInt>> lists = new HashMap<>();

    private int picCount = -1;
    private String first = null;

    /*
     * Make sure all pics have been drained
     */
    public void check() throws Exception {
        if (lists.size() > 0) {
            Set<String> keys = lists.keySet();
            pout(Arrays.toString(keys.toArray()));
            throw new Exception("lists.size: " + lists.size());
        }
    }

    /*
     * the first pic is assumed to have all its first-place entries
     * together, so when it is done, we have the pic count, and all
     * pic-pic pairs are expected.
     */

    private int lineCount = 0;

    public void add(String id1, String id2, int val) 
            throws Exception {

        lineCount++;

        if (first == null) {
            first = id1;
        } else if (!"g".equals(first)) {
            if (!first.equals(id1)) {
                picCount = lineCount - 1;
                sortOut(first);
                first = "g";
            }
        }

        // add each to the other's list, with val

        List<IdValInt> p1l = lists.get(id1);
        if (p1l == null) {
            p1l = new ArrayList<>();
            lists.put(id1, p1l);
        }
        addAndChop(p1l, id2, val);
        //p1l.add(new IdValInt(id2, val));

        List<IdValInt> p2l = lists.get(id2);
        if (p2l == null) {
            p2l = new ArrayList<>();
            lists.put(id2, p2l);
        }
        //p2l.add(new IdValInt(id1, val));
        addAndChop(p2l, id1, val);

        // check if either pic has reached pic count -
        // first pic will happen as soon as pic count is established
        // frees memory

        if (seenPic.add(id1) == picCount) {
            sortOut(id1);
        }
        if (seenPic.add(id2) == picCount) {
            sortOut(id2);
        }
    }

    public void partials() throws Exception {

        if (lists.size() == 0) {
            return;
        }
        pout("Handling partials: " + lists.size());

        List<String> ids = new ArrayList<>();
        ids.addAll(lists.keySet());

        int ct = 0;
        for (String id : ids) {
            List<IdValInt> others = lists.get(id);
            ct += others.size();
            sortOut(id);
        }

        pout("Avg matches/id: " + (ct / ids.size()));
    }

    private void addAndChop(List<IdValInt> l, String id, int val)
            throws Exception {

        int size = l.size();

        if (size > 2 * targetPairs) {
            if (topDown) {
                Collections.sort(l, Collections.reverseOrder());
            } else {
                Collections.sort(l);
            }

            l.subList(targetPairs, size).clear();
        }

        l.add(new IdValInt(id, val));
    }

    private void sortOut(String id)
            throws Exception {

        List<IdValInt> idvals = lists.remove(id);
        if (idvals == null) {
           err("sortOut: no idvals for: " + id +
                    " lists.size(): " + lists.size());
        }

        if (topDown) {
            Collections.sort(idvals, Collections.reverseOrder());
        } else {
            Collections.sort(idvals);
        }

        int ct = 0;
        int n = targetPairs;
        if (idvals.size() < n) {
            n = idvals.size();
        }
        for (int i=0; i<n; i++) {
            IdValInt iv = idvals.get(i);

            // order the id values since symmetric
            //int otherid_as[] = IndexHolder.getArchSeq(iv.id);

            String[] oIds = ID.sortIds(id, iv.id);
            tout.println(oIds[0] + "\t" + oIds[1] + "\t" + tag + "\t" + 
                            (int)iv.value);
        }
        idvals = null;
    }

}
