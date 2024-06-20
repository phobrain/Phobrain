package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  NNVectors - Load NN vectors, treat as points for
 **                 calcing triangular matrixes of
 **                 distances, to calculate top matches.
 **
 **             Exits with err() if any problem.
 */

import org.phobrain.util.Stdio;
import org.phobrain.util.FileRec; // for jpgs, has "ID id;'
import org.phobrain.util.ID; // for 0kwd, version in 0001=1

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.IOException;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Collections;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NNVectors extends Top implements Serializable {

    // veclen is the dimension, set in readVecs()

    private int veclen = -1;

    // func for distances, set in calcDistances

    private String func;

    // veclen and func determine how these are chosen from the above in writeTopPairs()

    private double trim, mult;

    // set in constructor

    final private Set<String> idset;
    final private String fname;
    //final private int npics;
    //final private int pr_sz;

    // per-pic vector arrays, filled by reading vector file,
    //         used for distance calc to rows in Top.MAT//rix

    double[][] left;
    double[][] right;

    double[][] side_arr;  // set to left|right in caclDistances()

    /*
    **  NNVectors - id's determine v|h
    */
    public NNVectors(List<String> ids, String fname) {

        super(ids, true);

        this.fname = fname;

        File f = new File(fname);
        if (!f.canRead()) {
            err("NNVectors: Unreadable: " + fname);
        }

        idset = new HashSet<String>(ids);

        if (idset.size() != ids.size()) {
            err("Duplicate id(s) in list: unique: " + idset.size());
        }

        String dir = null;
        String vf = null;
        int ix = fname.lastIndexOf("/");
        if (ix != -1) {
            ix++;
            dir = fname.substring(0, ix);
            vf = fname.substring(ix);
        }

        pout("======= NNVectors: Reading vecs for " + ids.size() + " photo ids," +
                "\n\tExpecting " + (2*ids.size()) +
                " lines [id <r|l> <v0 v1 v2 ..>] from\n" +
                (dir == null ?  "\n\t" + fname :
                    "\n\t" + dir + "\n\t" + vf) + "\n");

        try {

            readVecs(f);

        } catch (Exception e) {

            e.printStackTrace();
            err("NNVectors: " + e.toString());

        }
    }

    public static boolean isVecsFile(String fname) {

        File f = new File(fname);
        if (!f.canRead()) {
            //pout("isVecsFile: Unreadable: " + fname);
            return false;
        }
        if (!fname.endsWith(".vecs")) {
            //pout("isVecsFile: not w/ .vecs: " + fname);
            return false;
        }
        return true;
    }

    private void readVecs(File f) throws Exception {

        BufferedReader reader = new BufferedReader(new FileReader(f));

        int linect = 0;
        int skipped = 0;

        left = new double[npics][];
        right = new double[npics][];

        int ipic = -1;
        int tot_pic = 0;

        Set<String> check_idset = new HashSet<>(ids);

        while (true) {

            String line = reader.readLine();
            if (line == null) {
                //pout("\033[2K\rEOF at line " + linect);
                break;
            }

            linect++;
            if (linect % 2 == 0) {
                tot_pic++;
            }

            // id  l|r vector..
            //
            // 1:1a l 5.7845793 5.761648 ...

            String ss[] = line.split(" ");

            if (linect == 1) {

                veclen = ss.length - 2;
                if (veclen < 2) {
                    err("NNVectors.readVecs: Veclen < 2: " + line + " in " + f);
                }
                //pout("-- vec len is " + veclen);

            } else if (ss.length-2 != veclen) {
                err("NNVectors.readVecs: Veclen != at line " + linect + ": " + line + " in " + f);
            }

            String[] xid = ss[0].split(":");
            int archive = Integer.parseInt(xid[0]);
            ID iid = new ID(archive, xid[1]);
            String id = iid.id;
//if("5:7644A".equals(ss[0]))
//err("bad id? " + ss[0] + "->" + id);
            if (id.startsWith("37/") && Character.isAlphabetic(id.charAt(3)))
                err("NNVectors.readVecs: " + ss[0]+"->"+id);

            if (!idset.contains(id)) {
                skipped++;
                continue;
            }

            check_idset.remove(id);

            if (linect % 2 == 1) {
                ipic++;
//if (ipic == left.length)
//err("ipic==length " + line);

            }

            double[] arr = new double[veclen];
            for (int i=2; i<ss.length; i++) {
                arr[i-2] = Double.parseDouble(ss[i]);
            }
            if (ss[1].equals("l")) {
                left[ipic] = arr;
            } else {
                right[ipic] = arr;
            }
            //map.put(ss[0] + "|" + ss[1], arr);

        }
        if (ipic != npics-1) {
            err("NNVectors.readVecs: ipic != npics-1: " + ipic +
                        " expect " + (npics-1) +
                        " skipped " + skipped +
                        "\nmissing " + check_idset.toString() +
                        "\n\t  in " + f);
        }

        pout("-- loaded " + left.length + " * r,l vectors of size " + veclen +
                " (all lines " + linect + ", skipped " + skipped + ")");

        if (ipic != ids.size()-1) {
            err("File is missing " + (ids.size()-ipic-1) + " ids");
        }

    }

    /*
    **  calcDistances: func from MathUtil:
    **      { "cosine", "poinca", "hellin", "cartes" };
    */

    public boolean notFunc(String func) {
        return MathUtil.notFunc(func);
    }

    private void calcDistances(boolean isLeft, String func) {

        this.func = func;

        side_arr = (isLeft ? left : right);

        int nprocs = MiscUtil.getProcs();
        int perproc = npics / nprocs;

        String funcTag = func + "(" + veclen + ")";

        pout("-- NNVectors: calc " + (isLeft ? "l " : "r " ) +
                    " triangular distance matrix with " +
                    funcTag + " using " +
                    nprocs + " threads");

        long t0 = System.currentTimeMillis();

        try {

            if (nprocs == 1) {

                pWorker pw = new pWorker(null, func);

                for (int i=0; i<npics-1; i++) {
                    pw.calcDistRow(i);
                }

            } else {

                BlockingQueue<Integer> pQ = new
                        LinkedBlockingQueue<Integer>(nprocs);

                ExecutorService pWorkers = Executors.newFixedThreadPool(nprocs);
                for (int i=0; i<nprocs; i++) {
                    pWorkers.execute(new pWorker(pQ, func));
                }
                //pout("procs started");
                for (int i=0; i<npics-1; i++) {
                    pQ.put(i);
                }
                //pout("procs started, pics put");

                for (int i=0; i<nprocs; i++) {
                    pQ.put(-1);
                }

                pWorkers.shutdown();

                try {
                    pWorkers.awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                    System.err.println("Interrupt: " + ie);
                    //System.exit(1);
                }
            }

        } catch (Exception e) {
            System.err.println("E: " + e);
            e.printStackTrace();
            System.exit(1);
        }
        pout("-- Calced " + funcTag + " with " + nprocs +
                " threads in " + ((System.currentTimeMillis()-t0)/1000) + " sec");

    }

    class pWorker implements Runnable {

        private final BlockingQueue<Integer> pQ;
        private final String func; // MathUtil.compFuncs = { "cosine", "poinca", "hellin", "cartes" };

        public pWorker(BlockingQueue<Integer> pQ, String func) {
            this.pQ = pQ;
            this.func = func;
        }

        @Override
        public void run() {

            int taken = 0;

            while(true) {

                try {

                    int i = pQ.take();
                    if (i == -1) {
                        break;
                    }

                    taken++;

                    calcDistRow(i);

                } catch (Exception e) {
                    e.printStackTrace();
                    err(e.toString());
                }
            }

            //pout("Handled " + taken);
        }

        public void calcDistRow(int ipic) throws Exception {

            double[] row = getRow(ipic);
            double[] iarr = side_arr[ipic];

            int rowi = 0;
            for (int j=ipic+1; j<npics; j++) {

                double[] jarr = side_arr[j];

                //pout("a1,2: " + Arrays.toString(iarr) + "   " + Arrays.toString(jarr));
                //err("compare " + MathUtil.vec_compare(func, iarr, jarr));

                double val = MathUtil.vec_compare(func, iarr, jarr, true); // zero if NaN
                row[rowi++] = val;
            }
        }

    }

    public List<String> getIds() {
        return ids;
    }
    public int getNPairs() {
        return pr_sz;
    }

    public String toString() {
        return "NNVectors: npics " + npics +
                " npairs " + pr_sz +
                " fname " + fname +
                (func == null ? " no distances" : " distances " + func);
    }

    public double getSum(int ipic, String id) {

        if (ipic >= npics) {
            err("NNVectors.getSum: ipic out of range: " +
                    ipic + " " + id + "n" +
                    toString());
        }

        double sum = 0.0;

        // row
        double[] row = getRow(ipic);
        for (double val : row) {
            sum += val;
        }
        // column
        for (int i=0; i<ipic; i++) {
            row = getRow(i);
            sum += row[ipic];
        }

        return sum;
    }

    /**
     **  writeTopLRPairs() - write/append top cases
     **                     for l, r:
     **                         calc distances, print top
     **
     **     Adapted from predtool.py writeTopFile().
     */

    public int writeTopLRPairs(PrintStream out,
                            int nprocs, List<String> ids,
                            String tag, String func,
                            int pairsPic, int pairsPicArch)
            throws Exception {
//nprocs=1;
        long t0 = System.currentTimeMillis();

        if (nprocs < 1) {
            nprocs = MiscUtil.getProcs();
        }

        int perproc = npics / nprocs;

        String funcTag = func + "(" + veclen + ")";

        pout("-- NNVectors.writeTopLRPairs[" +
                funcTag + ", " + tag + "]: " +
                npics + " pics, " +
                nprocs + " threads, " +
                perproc + " pics/thread");

        int lines = 0;

        boolean[] both = { true, false };

        for (boolean isLeft : both) {

            calcDistances(isLeft, func);

            lines += writeTopPairs(out, nprocs, ids,
                            tag + ( isLeft ? "l" : "r" ),
                            pairsPic, pairsPicArch);

        }

/*
            if (nprocs == 1) {

                SortVal[] sortVals = new SortVal[npics-1];
                for (int i=0; i<sortVals.length; i++) {
                    sortVals[i] = new SortVal();
                }

                try {
                    for (int ipic=0; ipic<npics-1; ipic++) {
                        writePicTop(out, thisTag, ipic,
                                        sortVals, usedPairs,
                                        pairsPic, pairsPicArch,
                                        trim, mult);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    err("NNVectors: " + e);
                }

            } else {

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
                                    writePicTop(out, thisTag, ipic,
                                                    sortVals, usedPairs,
                                                    pairsPic, pairsPicArch);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                err("NNVectors: " + e);
                            }
                        }
                    });
                    threads[i].start();
                }
                for (Thread t : threads) {
                    t.join();
                }
            }
            pout("-- top " + (isLeft ? "left" : "right") +
                    " calced/output in " +
                    ((System.currentTimeMillis() - t0s)/1000) + " sec");
*/

        long dt = (System.currentTimeMillis() - t0) / 1000;

        pout("== NNVectors.writeTopLRPairs[" +
                    funcTag + ", " + tag + "] done: " +
                    dt + " sec " +
                    (npics/dt) + " pics/sec " +
                    (lines/dt) + " lines/sec");

        return lines;
    }

}
