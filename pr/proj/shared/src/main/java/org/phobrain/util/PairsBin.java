package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  PairsBin - Load a 1D array of float32 and access it as NxN,
 **             from .pairs file written by numpy using byteswap() 
 **             to match java big-endian format:
 **
 **                 pred.byteswap().tofile(path)
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

import java.math.BigInteger;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Collections;

//////////////////////////////////
// Possibilities not used in 2023 
//    since slow
//import java.io.RandomAccessFile;
//
// since buffer size is int vs. long
//
//import java.nio.channels.FileChannel;
//import java.nio.MappedByteBuffer;
//import java.nio.FloatBuffer;
//
// nonstandard, maybe wrong, didn't debug
//import xerial.larray.mmap.MMapBuffer;
//import xerial.larray.mmap.MMapMode;

public class PairsBin extends Stdio implements Serializable {

    final double FACTOR =  1.0e7;

    // maybe someday
    //RandomAccessFile raf = null;

    // fnames are accumulated by add()
    private List<String> fnames = new ArrayList<>();

    private int npics;

    public PairsBin(String fname, int npics) {

        fnames.add(fname);
        this.npics = npics;

        try {
            init(true);
        } catch (Exception e) {
            err("PairsBin Exception: " + e);
        }
    }

    public int getNPics() {
        return npics;
    }

    public void reuse(String fname, int npics) {

        if (npics != this.npics) {
            err("PairsBin.reuse: npics differ: " + npics + "\n" +
                        toString());
        }
        fnames.clear();
        fnames.add(fname);

        try {
            init(false);
        } catch (Exception e) {
            err("PairsBin Exception: " + e);
        }
    }

    private final static String NAME_BASE = "dirPB_";

    public static String dirPBName(int size) {
        return NAME_BASE + size + ".pb";
    }
    public static int parseDirPBSize(String path) {

        if (!path.endsWith(".pb")) {
            err("PairsBin.parseDirPBSize(): File name must end with .pb");
        }

        int ix = path.lastIndexOf(NAME_BASE);
        if (ix == -1) {
            err("PairsBin.parseDirPBSize(): Expected to contain [" + NAME_BASE + "]: " + path);
        }
        ix += NAME_BASE.length();

        int ix2 = path.indexOf(".", ix);
        String number = path.substring(ix, ix2);
        pout("number " + number + " -from- " + path);
        return Integer.parseInt(number);
    }

    public void write(String fname) {

        long t0 = System.currentTimeMillis();

        File f = new File(fname);

        if (f.isDirectory()) {
            fname += "/" + dirPBName(fnames.size());
        } else if (!fname.endsWith(".pb")) {
            fname += ".pb";
        }

        pout("PairsBin.write: " + fname);

        FileOutputStream fos = null;
        ObjectOutputStream oos = null;

        try {

            fos = new FileOutputStream(fname);
            oos = new ObjectOutputStream(fos);

            oos.writeObject(this);

            long dt = (System.currentTimeMillis() - t0) / 1000;

            pout("PairsBin.write " + fname + " in " + dt + " sec");

        } catch (Exception e) {
            err("PairsBin.write " + fname + ": " + e);
        } finally {
            try {
                oos.close();
                fos.close();
            } catch (Exception e) {
                err("PairsBin.write.finally(): " + e);
            }
        }
    }

    public static PairsBin load(String fname, int npics) {

        if (!fname.endsWith(".pb")) {
            pout("PairsBin.load: Warning: fname doesn't end in .pb: " + fname);
        } else {
            pout("PairsBin.load: " + fname);
        }

        long t0 = System.currentTimeMillis();

        FileInputStream fis = null;
        ObjectInputStream ois = null;

        try {

            fis = new FileInputStream(fname);
            ois = new ObjectInputStream(fis);

            PairsBin pb = (PairsBin) ois.readObject();

            long dt = (System.currentTimeMillis() - t0) / 1000;

            pout("PairsBin.load: in " + dt + " sec: " + pb.toString());

            if (pb.npics != npics) {
                err("PairsBin.load(): " + fname + " npics is " + pb.npics +
                                    " expected " + npics);
            }
            return pb;

        } catch (Exception e) {
            err("PairsBin.load " + fname + ": " + e);
        } finally {
            try {
                ois.close();
                fis.close();
            } catch (Exception e) {
                err("PairsBin.load.finally: " + e);
            }
        }
        return null; // compiler happy

    }

    public String toString() {
        return "PairsBin: npics " + npics +
                " file(s) " + String.join(",", fnames) + 
                " factor " + FACTOR;
    }

    private double[][] d0 = null; // d0 is short for 'data'

    public void getPairVals(int i, int j, double[] ret) {

        if (i >= d0.length  ||  j >= d0.length) {
            err("d0 len " + d0.length + " i,j " + i + "," + j);
        }
        ret[0] = d0[i][j] / fnames.size();
        ret[1] = d0[j][i] / fnames.size();
    }

    /*
    **  getD0SumsLR() - TODO - matrix lib?
    **
    **      ret[0..3]:
    **          sum_d0_l
    **          sum_d0_r
    **          avg_ok_d0 
    **          avg_bad_d0 
    */

    //final int CUT_BAD = 200000;

    public void getD0SumsLR(int ipic, String id, double[] ret) {

        if (ipic >= npics) {
            err("getD0SumsLR: ipic out of range: " + 
                    ipic + " " + id + "n" +
                    toString());
        }

        ret[0] = 0.0;
        ret[1] = 0.0;
        ret[2] = 0.0; // ignoring, depends on sorted vals?
        ret[3] = 0.0; // ignoring, depends on sorted vals?

        final double self = d0[ipic][ipic]; // to subtract

        double[] row = d0[ipic];
        for (int icol=0; icol<npics; icol++) {
            ret[0] += row[icol];
        }
        ret[0] -= self;
        ret[0] /= (npics-1);

        for (int irow=0; irow<npics; irow++) {
            ret[1] += d0[irow][ipic];
        }
        ret[1] -= self;
        ret[1] /= (npics-1);
    }

    /**
     **  writeTop() - write/append top cases.
     **
     **     From predtool.py writeTopFile().
     */

    private static class SortVal implements Comparable {

        int order;
        double val;

        @Override
        public int compareTo(Object obj) {

            SortVal o = (SortVal) obj;
            //if (!(o instanceof SortVal)) {

            if (val < o.val) return -1;
            if (val > o.val) return 1;
            return 0;
        }
    }

    /*
    **  writeTop() - .. 
    */
    public int writeTop(PrintStream out, int nprocs, List<String> ids, 
                            String tag, int pairsPic, int pairsPicArch)
            throws Exception {

        long t0 = System.currentTimeMillis();

        if (nprocs < 1) {
            nprocs = MiscUtil.getProcs();
        }

        int perproc = npics / nprocs;

        pout("PairsBin.writeTop: " + npics + " pics, " + nprocs + " threads, " + perproc + " pics/thread");

        // keep track of pairs already printed

        final Set<String> usedPairs = Collections.synchronizedSet(new HashSet<>());

        Thread[] threads = new Thread[nprocs];

        for (int i=0; i<nprocs; i++) {

            final int start = i * perproc;
            final int end = (i == nprocs-1) ? ids.size() : start + perproc;

            threads[i] = new Thread(new Runnable() {

                public void run() {

                    // per-thread workspace

                    SortVal[] sortVals = new SortVal[npics];
                    for (int i=0; i<npics; i++) {
                        sortVals[i] = new SortVal();
                    }

                    try {
                        for (int ipic=start; ipic<end; ipic++) {
                            writePicTop(out, tag, ipic, ids, sortVals, usedPairs, pairsPic, pairsPicArch);
                        }
                    } catch (Exception e) {
                        err("PairsBin " + start + 
                                    ".." + end + ": " + e);
                    }
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }

        long dt = (System.currentTimeMillis() - t0) / 1000;

        pout("PairsBin.writeTop done: " + usedPairs.size() + " pairs in " + 
                dt + " sec, rate " + 
                (usedPairs.size()/dt) + "/sec, " + 
                (npics/dt) + " pics/sec");

        return usedPairs.size();
    }

    public void add(String fname) {

        PairsBin other = null;

        if (fname.endsWith(".pairs")) {
            other = new PairsBin(fname, npics); // exits if mismatch
        } else if (fname.endsWith(".pb")) {
            other = PairsBin.load(fname, npics);
        } else {
            err("PairsBin.add: unknown type: " + fname +
                            " - expected .pairs or .pb");
        }

        add(other);
    }

    public void add(PairsBin other) {

        if (other.npics != npics) {
            err("PairsBin.add: other.npics: " + other.npics + "\n" +
                    toString());
        }

        for (int ipic=0; ipic<npics; ipic++) {
            for (int jpic=0; jpic<npics; jpic++) {
                d0[ipic][jpic] += other.d0[ipic][jpic];
            }
        }

        fnames.addAll(other.fnames);
    }

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
        String archivePrefix = id1.substring(0, id1.indexOf("/")) + "/";

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
                pout("PairsBin.writeTop: divide by " + fnames.size() + " files");
            }
*/
            double div = (double) fnames.size();

            if (picLeft) {
                if (usedPairs.add(id1 + " " + idX)) {
                    out.println(id1 + "\t" + idX + "\t" + tag + "\t" +
                                    (int)(sortVals[ix].val/div));
                }
            } else {
                if (usedPairs.add(idX + " " + id1)) {
                    out.println(idX + "\t" + id1 + "\t" + tag + "\t" +
                                    (int)(sortVals[ix].val/div));
                }
            }
        }
    }

    private void writePicTop(PrintStream out, 
                            String tag, 
                            int ipic, 
                            List<String> ids, 
                            SortVal[] sortVals,
                            Set<String> usedPairs,
                            int pairsPic, int pairsPicArch)
            throws Exception {

        if (ids.size() != npics) {
            err("PairsBin.writePicTop(): expected npics=" + npics +
                        " ids, got " + ids.size() + "\n" + 
                        toString());
        }

        String id1 = ids.get(ipic);

        // for fname1 on left: i, j
        double[] row = d0[ipic];

        for (int j=0; j<npics; j++) {

            sortVals[j].order = j;

            if (j == ipic) {
                sortVals[j].val = -1 * row[j];
            } else {
                sortVals[j].val = row[j];
            }
        }
        Arrays.sort(sortVals, Collections.reverseOrder());

        writeSorted(out, tag, ipic, true, // [id1 X, val] 
                    ids, sortVals, usedPairs, pairsPic, pairsPicArch);

        // for fname1 on right: j, i
        for (int j=0; j<npics; j++) {

            sortVals[j].order = j;

            if (j == ipic) {
                sortVals[j].val = -1 * d0[j][ipic];
            } else {
                sortVals[j].val = d0[j][ipic];
            }
        }
        Arrays.sort(sortVals, Collections.reverseOrder());

        writeSorted(out, tag, ipic, false, // [X, id1, val] 
                    ids, sortVals, usedPairs, pairsPic, pairsPicArch);
    }

    private void init(boolean allocate) 
            throws Exception {

        if (fnames.size() != 1) {
            err("PairsBin.init: expect single fnames elt: " + fnames.size());
        }
        String fname = fnames.get(0); // add() accumulates fnames

        BigInteger npics_bi = new BigInteger(Long.toString(npics));
        BigInteger npics_sq = npics_bi.multiply(npics_bi);
        //  float32 == 4 bytes
        BigInteger pairBytes = npics_sq.multiply(new BigInteger("4"));

        if (pairBytes.longValue() < 0) {
            err("PairsBin.init: overflow on " + npics + " squared * 4");
        }

        // check file size/existence first
        //  float32 == 4 bytes

        File file = new File(fname);

        if (file.length() != pairBytes.longValue()) {
            err("PairsBin.init: File size for npics, fname " + npics + " " +
                        fname + ", " + 
                        ":\n\texpected: " + pairBytes.longValue() + 
                        " \n\tgot:      " + file.length());
        }

        int nprocs = MiscUtil.getProcs();
        int perproc = npics / nprocs;

        pout("PairsBin.init: reading " + 
                npics_sq + " pairs for " + 
                npics + " pics, using " + 
                nprocs + " threads, " + 
                perproc + " pics==rows/thread");

        long t0 = System.currentTimeMillis();
 
        if (allocate) {
            d0 = new double[npics][];
            pout("Allocated d0 for npics=" + npics +
                        ": " + d0.length);
        }

        Thread[] threads = new Thread[nprocs];

        for (int iproc=0; iproc<nprocs; iproc++) {

            final int startPic = iproc * perproc;
            final int endPic = (iproc == nprocs-1) ? npics : startPic + perproc;
            final int iprocc = iproc;

            threads[iproc] = new Thread(new Runnable() {

                public void run() {

                    int ct=0;

                    try {

                        DataInputStream in = new DataInputStream(
                                    new BufferedInputStream(
                                        new FileInputStream(file)));

                        long skip = (long) startPic * (4 * npics); // does math in int w/o the cast to long
                        //pout("t " + iprocc + " start/end " + startPic + "/" + endPic + "   skip " + skip);

                        int skips = 0;
                        while (skip > (long) Integer.MAX_VALUE) {

                            int ped = in.skipBytes(Integer.MAX_VALUE);
                            if (ped != Integer.MAX_VALUE) {
                                err("skipping maxint after " + skips + " skips: " + ped);
                            }
                            skip -= (long) Integer.MAX_VALUE;
                            skips++;
                        }
                        if (skip > 0) {
                            int ped = in.skipBytes((int)skip);
                            if (ped != (int)skip) {
                                err("Thread " + iprocc + ": skipping final " + skip + 
                                        " after " + skips + " skips: " + ped);
                            }
                        }

                        for (int ipic=startPic; ipic<endPic; ipic++) {

                            double[] d0ipic = (allocate ? new double[npics] : d0[ipic]);

                            for (int jpic=0; jpic<npics; jpic++) {

                                double f = (double) in.readFloat(); // TODO - readDouble()
                                ct++;

                                if (f < 0.0f  ||  f > 1.0f) {
                                    err("range error in " + fname +
                                        ", ct val: " + ct + ", " + f);
                                }

                                double td = (1.0 - (double) f) * FACTOR;

                                if (td < 0.0) {
                                    err("range error in " + fname + 
                                        ", td < 0: " + td);
                                }

                                if (td > Double.MAX_VALUE) {
                                    err("range error in " + fname +
                                        ", val too high for factor " + FACTOR + 
                                        ": " + f + "-> " + td);
                                }
                                d0ipic[jpic] = (double) td;
                            }
                            if (allocate) {
                                d0[ipic] = d0ipic;
                            }
                        }

                        in.close();

                    } catch (Exception e) {
                        err("In " + fname + " at ct " + ct + " got " + e);
                    }
                }
            });
            threads[iproc].start();
        }
        for (Thread t : threads) {
            t.join();
        }

        double mbytes = file.length() / (1024*1024);
        double dt = (System.currentTimeMillis() - t0) / 1000;

        pout("PairsBin: loaded " + mbytes + " MB with " +
                npics_sq + " pairs in " + 
                dt + " sec, rate " + ((int)(mbytes / dt)) + " MB/s");
    }

    /* 
    ** for test:

        pout("First:");
        for (int i=0; i<1; i++) {
            String id1 = pic_list.get(i).p.id;
            for (int j=0; j<5; j++) {
                String id2 = pic_list.get(j).p.id;
                double[] r = getPairVals(i, j);
                pout("- " + id1 + " " + id2 +  " " + i + " " + j + " " + r[0] + " " + r[1]);
            }
        }
    **/

}
