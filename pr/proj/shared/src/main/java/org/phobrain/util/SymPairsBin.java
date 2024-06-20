package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  SymPairsBin - A triangular matrix of longs, to calculate
 **                 top matches, managed as a 1D array.
 **
 **             Exits with err() if any problem.
 */

import org.phobrain.util.Stdio;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Collections;

public class SymPairsBin extends Top implements Serializable {

    final double FACTOR =  1.0e7;

    public SymPairsBin(List<String> ids) {

        super(ids, true);

    }

    public boolean equals(SymPairsBin other) {

        if (other.npics != npics) {
            return false;
        }
        for (int i=0; i<ids.size(); i++) {
            if (!ids.get(i).equals(other.ids.get(i))) {
                return false;
            }
        }
        return true;
    }

    public void write(File f) {

        try {
            write(f.getCanonicalPath());
        } catch (Exception e) {
            e.printStackTrace();
            err("SymPairsBin.write(File): " + e);
        }
    }

    public void write(String fname) {

        checkAllSet();

        if (!fname.endsWith(".spb")) {
            pout("SymPairsBin.write: adding .spb extension");
            fname += ".spb";
        }

        long t0 = System.currentTimeMillis();

        File f = new File(fname);
        if (f.isDirectory()) {
            err("SymPairsBin.write: is dir: " + fname);
        }
        if (f.exists()) {
            pout("Overwriting " + fname + " in 5 seconds");
            try { Thread.sleep(5); } catch (Exception ignore) {}
        }

        pout("SymPairsBin.write: " + fname);

        FileOutputStream fos = null;
        ObjectOutputStream oos = null;

        try {

            fos = new FileOutputStream(fname);
            oos = new ObjectOutputStream(fos);

            oos.writeObject(this);

            long dt = (System.currentTimeMillis() - t0) / 1000;

            pout("SymPairsBin.write " + fname + " in " + dt + " sec");

        } catch (Exception e) {
            err("SymPairsBin.write " + fname + ": " + e);
        } finally {
            try {
                oos.close();
                fos.close();
            } catch (Exception e) {
                err("close: " + e);
            }
        }
    }

    public static SymPairsBin load(File f) {

        try {

            return load(f.getCanonicalPath());

        } catch (Exception e) {
            err("SymPairsBin.load(File): " + e);
        }
        return null; // compiler
    }

    public static SymPairsBin load(String fname) {

        if (!fname.endsWith(".spb")) {
            pout("SymPairsBin.load: Warning: fname doesn't end in .spb: " + fname);
        } else {
            pout("SymPairsBin.load: " + fname);
        }

        long t0 = System.currentTimeMillis();

        FileInputStream fis = null;
        ObjectInputStream ois = null;

        try {

            fis = new FileInputStream(fname);
            ois = new ObjectInputStream(fis);

            SymPairsBin spb = (SymPairsBin) ois.readObject();

            long dt = (System.currentTimeMillis() - t0) / 1000;

            pout("SymPairsBin.load: in " + dt + " sec: " + fname);

            return spb;

        } catch (Exception e) {
            err("SymPairsBin.load " + fname + ": " + e);
        } finally {
            try {
                ois.close();
                fis.close();
            } catch (Exception e) {
                err("close: " + e);
            }
        }
        return null; // compiler happy

    }

    public String toString() {
        return "SymPairsBin: npics " + npics +
                " npairs " + pr_sz;
    }

    /*
    **  getSum() - not used, but fun.
    */
    public double getSum(int ipic, String id) {

        if (ipic >= npics) {
            err("SymPairsBin.getSum: ipic out of range: " +
                    ipic + " " + id + "n" +
                    toString());
        }

        double sum = 0.0;

        // row
        double[] row = getRow(ipic);
        for (double val : row) {
            sum += (double) val;
        }

        // column
        for (int irow=0; irow<ipic; irow++) {

            row = getRow(ipic);

            int rowi = ipic - irow - 1;

            if (rowi >= row.length) {
                err("rowi " + rowi + " >l " + row.length +
                        ": ipic " + ipic + " irow " + irow);
            }

            sum += (double) row[rowi];
        }

        return sum;
    }

/*
    public int writeTopPairs(PrintStream out, int nprocs,
                            String tag, int pairsPic, int pairsPicArch)
            throws Exception {

        long t0 = System.currentTimeMillis();

        if (nprocs < 1) {
            nprocs = MiscUtil.getProcs();
        }

        int perproc = npics / nprocs;

        pout("SymPairsBin.writeTopPairs: " +
                    npics + " pics, " +
                    nprocs + " threads, " +
                    perproc + " pics/thread");

        // keep track of pairs already printed

        final Set<String> usedPairs = Collections.synchronizedSet(new HashSet<>());

        Thread[] threads = new Thread[nprocs];

        int start = 0;

        for (int i=0; i<nprocs; i++) {

            // local vars used by threads need to be final

            final int start_pic = start;
            final int end_pic = (i == nprocs-1) ? ids.size()-1 : start_pic + perproc;

            start = end_pic + 1;

            // pout("thread / start/end " + i + " / " + start_pic + "/" + end_pic);

            threads[i] = new Thread(new Runnable() {

                public void run() {

                    // per-thread workspace

                    SortVal[] sortVals = new SortVal[npics-1];
                    for (int i=0; i<sortVals.length; i++) {
                        sortVals[i] = new SortVal();
                    }

                    try {
                        for (int ipic=start_pic; ipic<end_pic; ipic++) {
                            writePicTop(out, sortVals, tag, ipic, ids, usedPairs, pairsPic, pairsPicArch);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        err("SymPairsBin.writeTopPairs: " + e);
                    }
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }

        long dt = (System.currentTimeMillis() - t0) / 1000;

        pout("SymPairsBin.writeTop done: " + usedPairs.size() + " pairs in " +
                dt + " sec, rate " +
                (usedPairs.size()/dt) + "/sec, " +
                (npics/dt) + " pics/sec");

        return usedPairs.size();
    }
*/

    private void writeSorted(PrintStream out,
                            String tag,
                            int ipic,
                            boolean picLeft,
                            List<String> ids,
                            SortVal[] sortVals,
                            Set<String> usedPairs,
                            int pairs_pic, int pairs_archive)
            throws Exception {

        String id1 = ids.get(ipic);
        String ss[] = id1.split("/");
        String archivePrefix = ss[0] + "/"; // id1.substring(0, id1.indexOf("/")) + "/";
//err("==> " + id1);
        int pic_ct = 0;
        int arch_ct = 0;

        for (int ix=0; ix<sortVals.length; ix++) {

            if (sortVals[ix].order == ipic) {
                continue;
            }

            pic_ct++;

            String idX = ids.get(sortVals[ix].order);
            boolean sameArchive = idX.startsWith(archivePrefix);

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
/*
            if (fnames.size() > 1) {
                pout("SymPairsBin.writeTop: divide by " + fnames.size() + " files");
            }
*/
            if (usedPairs.add(id1 + " " + idX)) {
                out.println(id1 + "\t" + idX + "\t" + tag + "\t" +
                                    (int)(sortVals[ix].val));
            }
        }
    }
/*
    private void writePicTop(PrintStream out,
                            SortVal[] sortVals,
                            String tag,
                            int ipic,
                            List<String> ids,
                            Set<String> usedPairs,
                            int pairsPic, int pairsPicArch)
            throws Exception {

        if (ids.size() != npics) {
            err("SymPairsBin.writePicTop(): expected npics=" + npics +
                        " ids, got " + ids.size() + "\n" +
                        toString());
        }

        String id1 = ids.get(ipic);

        double[] row = getRow(ipic);

        int sort_ix = 0;

        // row
        for (int j=0; j<row.length; j++) {

            sortVals[sort_ix].order = sort_ix;
            sortVals[sort_ix].val = row[j];
            sort_ix++;

        }

        // column
        for (int irow=0; irow<ipic; irow++) {

            row = getRow(ipic);
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
        if (sort_ix != sortVals.length) {
            err("sort_ix != sortVals.length: " + sort_ix + " " + sortVals.length);
        }

        Arrays.sort(sortVals, Collections.reverseOrder());

        writeSorted(out, tag, ipic, true, // [id1 X, val]
                    ids, sortVals, usedPairs, pairsPic, pairsPicArch);

    }
*/


    /*
    ** for test:

        pout("First:");
        for (int i=0; i<1; i++) {
            String id1 = pic_list.get(i).p.id;
            for (int j=0; j<5; j++) {
                String id2 = pic_list.get(j).p.id;
                float[] r = getPairVals(i, j);
                pout("- " + id1 + " " + id2 +  " " + i + " " + j + " " + r[0] + " " + r[1]);
            }
        }
    **/

}
