package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  Top - Given a full or symmetric distance matrix, provide
 **             a way for multiple threads in the extending class
 **             to independently calculate and output the top cases
 **             per photo. Java parallel advantage is many threads
 **             reading the same memory.
 **
 **             Exits with err() if any problem.
 */

import org.phobrain.util.Stdio;
import org.phobrain.util.ID; // for 0kwd, version in 0001=1

import java.io.PrintStream;
//import java.io.Serializable;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Collections;

/**
 **  Top - calcs top pair distances from a full or symmetric/triangular
 **         matrix for use in 'pr.pairtop' files.
 **         For use in cmdline apps: on error, print/exit.
 **         Top is unaware of v,h and l,r distinctions.
 */
public abstract class Top extends Stdio { // implements Serializable {

    // final == set in constructor

    final List<String> ids;
    final private boolean symmetric;

    final int npics;
    final int pr_sz;

    private double[][] MAT; //RIX for distances

    /*
    **  Top - id's are used in output
    */
    public Top(List<String> ids, boolean symmetric) {

        if (ids == null  ||  ids.size() < 2) {
            err("Top: input id's null or < 2");
        }

        this.ids = ids;
        this.symmetric = symmetric;

        npics = ids.size();
        if (symmetric) {
            pr_sz = (npics*(npics-1)) / 2;
        } else {
            pr_sz = npics * npics;
        }

        if (symmetric) {

            int nids = ids.size();

            pout("- making symmetric matrix, ids: " + nids);

            MAT = new double[nids-1][];

            for (int i=0; i<MAT.length; i++) {
                MAT[i] = new double[nids-i-1];
            }

        } else {

            pout("- making asymmetric matrix, ids: " + ids.size());

            MAT = new double[npics][];
            for (int i=0; i<MAT.length; i++) {
                MAT[i] = new double[npics];
            }
        }
    }

    public List<String> getIds() {
        return ids;
    }

    public int getNPairs() {
        return pr_sz;
    }

    public synchronized double[] getRow(int i) {
        return MAT[i];
    }

    public String toString() {
        return "Top: npics: " + npics +
                " symmetric: " + symmetric +
                " npairs: " + pr_sz;
    }

    public void checkAllSet() {

        for (int i=0; i<npics-1; i++) {
            if (MAT[i] == null) {
                err("Top.checkAllSet: row " + i + " is unset");
            }
        }
    }

    public class SortVal implements Comparable {

        int order;
        double val;

        @Override
        public int compareTo(Object obj) {

            if (!(obj instanceof SortVal)) {
                throw new RuntimeException("Bad obj!");
            }
            SortVal o = (SortVal) obj;

            return Double.compare(val, o.val);
            //if (val < o.val) return -1;
            //if (val > o.val) return 1;
            //return 0;
        }
    }

    int failed_cut = 0;

    private synchronized void writeSorted(PrintStream out,
                            String tag,
                            int ipic,
                            List<String> ids,
                            SortVal[] sortVals,
                            Set<String> usedPairs,
                            int pairs_pic, int pairs_archive,
                            double trim, double mult)
            throws Exception {

        String id1 = ids.get(ipic);
        String archivePrefix = id1.substring(0, id1.indexOf("/")) + "/";

        int pic_ct = 0;
        int arch_ct = 0;

        for (int ix=0; ix<sortVals.length; ix++) {

            if (sortVals[ix].order == ipic) {
                continue;
            }

            pic_ct++;

            double val = sortVals[ix].val;

            if (Double.isNaN(val)) {

                err("Top(tag " + tag + 
                    "): NaN for sortVals[ix=" + ix + 
                    " of " + sortVals.length + "].val");

            }

            if (val > trim) {
                failed_cut++;
                continue;
                // break?
            }
            val = trim - val;
            long lval = (long) (val * mult);

//int ival = (int) val;

            String idX = ids.get(sortVals[ix].order);
            boolean sameArchive = idX.startsWith(archivePrefix);

if (lval==0  &&  sortVals[ix].val != 1.0) {
pout("lval==0: " + id1 + " " + idX + " val " + sortVals[ix].val + " mult " + mult);
//" a1,2: " + Arrays.toString(iarr) + "   " + Arrays.toString(jarr));
}
            if (sameArchive) {
                arch_ct++;
            }

            if (pic_ct > pairs_pic) {

                if (arch_ct > pairs_archive) {
                    break;
                }
                if (!sameArchive) {
                    continue;
                }
            }

            if (usedPairs.add(id1 + " " + idX)) {
                // not pout()
                out.println(id1 + "\t" + idX + "\t" + tag + "\t" + lval);
            }
        }
    }

    private void writePicTop(PrintStream out,
                            String tag,
                            int ipic,
                            SortVal[] sortVals,
                            Set<String> usedPairs,
                            int pairsPic, int pairsPicArch,
                            double trim, double mult)
            throws Exception {

        if (sortVals == null  ||  sortVals.length != npics-1) {
            err("sortVals wrong length");
        }

        String id1 = ids.get(ipic);

        double[] row = null;

        int sort_ix = 0;

        if (symmetric) {

            // columns: 0..ipic-1 - ipic

            for (int irow=0; irow<ipic; irow++) {

                row = MAT[irow];

                int rowi = ipic - irow - 1;
                if (rowi >= row.length) {
                    err("rowi " + rowi + " >l " + row.length +
                        ": ipic " + ipic + " irow " + irow);
                }
                //pout(id1 + " row[irow][column] " + irow + "[" + rowi + "] sort_ix " + sort_ix);
                if (sort_ix == sortVals.length) {
                    err("irow<ipic " + irow + "<" + ipic + ": sort_ix  " + sort_ix +
                            " length " + sortVals.length);
                }

                sortVals[sort_ix].order = sort_ix;
                sortVals[sort_ix].val = row[rowi];
                sort_ix++;
            }
            //pout("ipic " + ipic + " sort " + sort_ix);

            // row: ipic to ipic+1..n

            if (ipic < MAT.length) {

                row = MAT[ipic];
                //pout("  row " + row.length);
                for (int j=0; j<row.length; j++) {

                    sortVals[sort_ix].order = sort_ix;
                    sortVals[sort_ix].val = row[j];
                    sort_ix++;

                }
            }

        } else {

            row = MAT[ipic];

            for (int j=0; j<row.length; j++) {

                if (j == ipic) {
                    continue;
                }
                sortVals[sort_ix].order = sort_ix;
                sortVals[sort_ix].val = row[j];
                sort_ix++;

            }
        }

        //pout(id1 + " row " + ipic + " sort_ix " + sort_ix);

        if (sort_ix != sortVals.length) {
            err("ix != sortVals.length: " + sort_ix + " " + sortVals.length);
        }

        Arrays.sort(sortVals, Collections.reverseOrder());

        writeSorted(out, tag, ipic, ids,
                    sortVals, usedPairs,
                    pairsPic, pairsPicArch,
                    trim, mult);
    }

    /*
    **  writeTopPairs() - use distances calced by creator
    */
    public int writeTopPairs(PrintStream out,
                            int nprocs, List<String> ids,
                            String tag, int pairsPic, int pairsPicArch)
            throws Exception {

        long t0 = System.currentTimeMillis();

        if (nprocs < 1) {
            nprocs = MiscUtil.getProcs();
        }

        double max = MAT[0][0];
        double min = MAT[0][0];

        for (int i=0; i<MAT.length; i++) {

            double[] row = MAT[i];

            for (int j=0; j<row.length; j++) {

                double val = row[j];

                if (val < min) min = val;
                if (val > max) max = val;

            }
        }

        double range = max - min;

        double trim = max * 1.0001;
        double mult = 0.5 * (double) Long.MAX_VALUE / range;

        //pout("Top: trim " + trim + " range " + range + " mult " + mult);

        // keep track of pairs already printed

        Set<String> usedPairs = Collections.synchronizedSet(new HashSet<>());

        int perproc = npics / nprocs;

        if (nprocs == 1) {

            SortVal[] sortVals = new SortVal[npics-1];
            for (int i=0; i<sortVals.length; i++) {
                sortVals[i] = new SortVal();
            }

            try {
                for (int ipic=0; ipic<npics-1; ipic++) {
                    writePicTop(out, tag, ipic,
                                    sortVals, usedPairs,
                                    pairsPic, pairsPicArch,
                                    trim, mult);
                }
            } catch (Exception e) {
                e.printStackTrace();
                err("Top: " + e);
            }

        } else {

            nprocs--;

            Thread[] threads = new Thread[nprocs];

            int start = 0;

            for (int i=0; i<nprocs; i++) {

                // local vars used by threads need to be final

                final int start_pic = start;
                final int end_pic = (i==nprocs-1 ? npics-1 : start + perproc);

                start = end_pic + 1;

                //pout("thread / start/end " + i + " / " + start_pic + "/" + end_pic);

                threads[i] = new Thread(new Runnable() {

                    public void run() {

                        // per-thread workspace

                        SortVal[] sortVals = new SortVal[npics-1];
                        for (int i=0; i<sortVals.length; i++) {
                            sortVals[i] = new SortVal();
                        }
                        try {
                            for (int ipic=start_pic; ipic<end_pic; ipic++) {
                                writePicTop(out, tag, ipic,
                                                sortVals, usedPairs,
                                                pairsPic, pairsPicArch,
                                                trim, mult);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            err("Top: " + e);
                        }
                    }
                });
                threads[i].start();
            }
            for (Thread t : threads) {
                t.join();
            }
        }

        long dt = (System.currentTimeMillis() - t0) / 1000;

        pout("== Top.writeTopPairs(" + tag + ") done: " +
                    usedPairs.size() + " pairs in " +
                    dt + " sec, rate " +
                    (usedPairs.size()/dt) + "/sec, " +
                    (npics/dt) + " pics/sec, " +
                    " failed_cut " + failed_cut +
                    " pct_failed " + ((int)(((double)failed_cut * 100) / pr_sz)));

        return usedPairs.size();
    }
}
