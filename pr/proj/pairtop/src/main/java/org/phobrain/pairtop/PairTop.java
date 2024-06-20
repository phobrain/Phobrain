package org.phobrain.pairtop;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  PairTop/cmdline - generate pairtop_[col|nn|vec]_[v|h]_dump.sql
 **
 **     Top pairs are often all one needs, so provide more
 **     variety in less space than tracking all N^2 pairs,
 **     which is still necessary for comparing random pics
 **     using precomputed data.
 **/

import org.phobrain.util.Stdio;
import org.phobrain.util.TopSelector;
import org.phobrain.util.PairsBin;
import org.phobrain.util.NNVectors;
import org.phobrain.util.SymPairsBin;
import org.phobrain.util.MathUtil;
import org.phobrain.util.MiscUtil;
import org.phobrain.util.FileRec;
import org.phobrain.util.HashCount;

import org.phobrain.db.dao.PictureDao;
import org.phobrain.db.util.DumpGen;

import java.sql.DriverManager;
import java.sql.Connection;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.PrintStream;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;

import java.util.Random;
import java.util.Date;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Scanner;

/*
 *  build top/bottom version of pic-pair table, dump file for import
 */

public class PairTop extends Stdio {

    final static int ASYM_COUNT = 200;

    private final static String[] msgs = {
            "oof", "erf", "merf", "splef?", "sxhxh!", "looloo..",
            "megatrend godfatter", "mephalaic shoehorns do not fit",
            "surgonomy is no longer a word", "simply cloistopic",
            "feeling conscoopis", "ferntelios redux", "fwip!",
            "getting there", "rendered postmorphic", "text me",
            "cat", "dog", "parrot", "ice", "nice..", "(gravel)",
            "plot twist coming up", "ergonimic", "ergonomimic", "sit up!",
            "don't go away!", "Your effort will be rewarded.",
            "Do not think of it.", "Let's keep politics out of this!",
            "It's a long, long road to Tipperary.", "Realize why you do this.",
            "Don't you hate it when I'm right?", "Listen to the fan.",
            "How do you expect me to feel?",
            "I added an elephant to a giraffe. Not good.",
            "Keep the gophers off the lawn after dark.",
            "Now seems a good time for some music.",
            "Whistle a happy tune..", "Mountainous mutiny.",
            "Mundane manufactury.", "Quick, don't look!", "Cackle.",
            "\"She is just an escape goat\"",
            "\"You will be on a foundering team of two.\"",
            "\"In a democracy, anyone can be an elitist.\"",
            "\"My personal life doesn't interest me.\" --Andrei Gromyko",
            "\"How do you put 'paradise' into one word?\"",
            "\"It was bigger than me, but it was me.\"",
            "\"There are no free rides in Disneyland.\"",
            "\"50% over-fitting, 50% under-dressing\"",
            "Be right here, right now. Again.",
            "It goes up to 8: something-or-other.",
            "Lock them up!", "It is a part of life, grasshopper.",
            "Databases are obsolete, anyway!"
        };

    private static void usage(String msg) {

        if (msg != null) {
            System.err.println("Error: " + msg);
        }

        System.err.println("Usage:" +
                   "\n\t[prog] <v|h> copy <tag1 txtfile1> <tag2 txtfile2> ..." +
                   "\n\t[prog] <v|h> vec <file> <tag1 func1> <tag2 func2> ..." +
                   "\n\t\t\t funcs in [" + Arrays.toString(MathUtil.compFuncs) + "]" +
                   "\n\t[prog] <v|h> nn   <tag1 file1.pb> <tag2 file2.pb> ..." +
                   "\n\tSymmetric options [where value(A,B)==value(B,A)]:" +
                   "\n\t[prog] <v|h> sym bc <i1> <file1> ... # (color) distance" +
                   " ba <iX> <fileX> ... # golden angle" +
                   " bb <iY> <fileY> ... # bill's angle");

        System.exit(1);
    }

    private static List<String> tags = new ArrayList<>();
    private static List<File> inFiles = new ArrayList<>(); // dirs get expanded

    private static HashCount tagsCount = new HashCount();

    Map<String, String> tagFiles = new HashMap<>();

    private static String orient = null;

    public static void main(String[] args) {

        if (args.length == 0) {
            usage(null);
        }

        orient = args[0];
        if (!"v".equals(orient)  &&  !"h".equals(orient)) {
            usage("expected orient in <v|h>: " + orient);
        }

        if (args.length < 2) {
            usage("Expected 2+ args");
        }

        String cmd = args[1];

        try {

            if ("nn".equals(cmd)) {
                // umptyflackity nn cases, all in one long cmd
                doNNTops(args);
                return;
            }
            if ("copy".equals(cmd)) {
                // umptyflackity pre-calced distance cases, all in one long cmd
                doCopyTops(args);
                return;
            }
            if ("vec".equals(cmd)) {
                doVectorDistances(args);
                return;
            }

            err("cmd unknown: " + cmd);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    // for fun

    private static Random rand = new Random();

    /*
    **  copyWithTag - data needs no massage/selection.
    */
    private static void copyWithTag(PrintStream tout, String tag, File f)
            throws Exception {
err("copyWithTag - still needed?");
        if (!f.canRead()) {
            err("Uneadable: " + f);
        }
        BufferedReader reader = new BufferedReader(new FileReader(f));

        pout("Symmetric distance, adding tag " + tag + " while appending file " + f);

        int lineCt = 0;

        while (true) {

            String line = reader.readLine();
            if (line == null) {
                pout("\033[2K\rEOF at line " + lineCt);
                break;
            }
            lineCt++;

            String ss[] = line.split(" ");

            if (ss.length != 3) {
                err("bad line " + lineCt + " in [" + f + "]:\n" + line);
            }

            double val = Double.parseDouble(ss[2]);

            // just in case?
            //String[] oIds = MiscUtil.ID.sortIds(id, iv.id);
            //tout.println(oIds[0] + "\t" + oIds[1] + "\t" + tag + "\t" + (int)iv.value);

            tout.println(ss[0] + "\t" + ss[1] + "\t" + tag + "\t" + ss[2]);

            // entertain the troops

            if (lineCt > 0  &&  lineCt % 400000 == 0) {
                System.out.print("\033[2K\r\t\t\t" + //"\r\t\t\t" +
                                   msgs[rand.nextInt(msgs.length)] + "\r");
            } else if (lineCt % 50000 == 0) {
                System.out.print(".");
            }
        }
    }

    private static class SortVal implements Comparable {

        int order;
        double val;

        @Override
        public int compareTo(Object obj) {

            SortVal o = (SortVal) obj;
            //if (!(o instanceof SortVal))

            if (val < o.val) return -1;
            if (val > o.val) return 1;
            return 0;
        }
    }

    static class Result {  // list of [picA picB value] for printing
        long[] vals = null; // using SymPairsBin w/ final object write
        int nvals = 0;
        String out = null;
        Boolean vertical = null;
    }

    // copied from histogram TODO util

    private static Map<FileRec, String> fileMap = new TreeMap<>();
    private static List<FileRec> fileList = new ArrayList<>();
    private static FileRec[] fileHists = null;

    private static int[] angle_distr = null;
    private static double[] vlen = null;

    private static int readBinHist = 0;
    private static int readBinHistTot = 0;
    private static int readAsciiHist = 0;
    private static int readAsciiHistTot = 0;

    private static FileRec readBinHistogram(FileRec fr) {

        try {
            ObjectInputStream ois = new ObjectInputStream(
                                        new FileInputStream(fr.file));
            fr.histogram = (double[]) ois.readObject();
            ois.close();
        } catch (Exception e) {
            pout("-- Error reading object: " + e);
            e.printStackTrace();
            err("Exiting");
        }
        readBinHist++;

        return fr;
    }

    private static FileRec readAsciiHistogram(FileRec fr) throws Exception {

        List<Double> l = new ArrayList<>();
        Scanner s = new Scanner(new FileReader(fr.file));
        while (s.hasNext()) {
            l.add(s.nextDouble());
        }
        double d[] = new double[l.size()];
        for (int i=0; i< l.size(); i++) {
            d[i] = l.get(i);
        }
        fr.histogram = d;

        readAsciiHist++;

        return fr;
    }


    static class pWorker implements Runnable {

        private final BlockingQueue<Integer> pQ;
        private final String func;

        public pWorker(BlockingQueue<Integer> pQ, String func) {
            this.pQ = pQ;
            this.func = func;
        }

        @Override
        public void run() {

            if ("read".equals(func)) {

                // use fileHists[i].fname

                while(true) {

                    try {

                        int i = pQ.take();
                        if (i == -1) {
                            break;
                        }
                        if (fileHists[i].fname.endsWith(".hist")) {

                            readAsciiHistogram(fileHists[i]);

                        } else if (fileHists[i].fname.endsWith(".hist_bin")) {

                            readBinHistogram(fileHists[i]);

                        } else {
                            err("pWorker: no histogram for " + fileHists[i].fname);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            } else if ("angle".equals(func)) {

                // calc angle based on center of geometry
                //  of histograms

                pout("-- angle: calc geometric center");

                int dim = fileHists[0].histogram.length;
                double avg[] = new double[dim];
                for (int j=0; j<dim; j++) {
                    avg[j] = 0.0;
                }
                for (int i=0; i<fileHists.length; i++) {
                    double[] point1 = fileHists[i].histogram;
                    for (int j=0; j<dim; j++) {
                        avg[j] += point1[j];
                    }
                }
                for (int j=0; j<dim; j++) {
                    avg[j] /= fileHists.length;
                }

                pout("-- angle: calc distances to center");

                vlen = new double[fileHists.length];
                for (int i=0; i<fileHists.length; i++) {
                    FileRec f1 = fileHists[i];
                    double[] point1 = f1.histogram;
                    double d = 0.0;
                    for (int j=0; j<dim; j++) {
                        point1[j] -= avg[j];
                        d += point1[j] * point1[j];
                    }
                    String id1 = f1.id.id;
                    vlen[i] = Math.sqrt(d);
                    //out_len.println(id1 + " " +
                    //    Math.round(vlen[i]));
                }

                while(true) {
                    try {
                        int i = pQ.take();
                        if (i == -1) {
                            break;
                        }
                        calcAngleRow(i);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            } else {

                // cos, poinc, cartes, helling
                MathUtil.checkFunc(func);

                while(true) {
                    try {
                        int i = pQ.take();
                        if (i == -1) {
                            break;
                        }
                        calcDistRow(i);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void calcDistRow(int i) throws Exception {

            StringBuilder result = new StringBuilder();

            FileRec fr1 = fileHists[i];
            String id1 = fr1.id.id;

            double[] point1 = fr1.histogram;
            if (point1 == null) {
                err("p1 null: " + i);
            }

            int row_size = fileHists.length - i - 1;
            double[] row = spb.getRow(i);
            if (row.length != row_size) {
                err("row size mismatch: " + i);
            }

            int jix = 0;
            for (int j=i+1; j<fileHists.length; j++) {

                FileRec fr2 = fileHists[j];

                if (fr2.histogram == null) {
                    err("p2 null: " + j);
                }

                if (fr1.vertical != fr2.vertical) {
                    err("Internal error - mixed v,h");
                }

                double val = Math.round(10000 *
                        MathUtil.vec_compare(func, point1, fr2.histogram,
                                            false)); // false==exit on NaN

                row[jix++] = val;
/*
                if (ASCII) {

                    String id2 = fr2.id.id;

                    result.append(id1).append(" ").append(id2)
                          .append(" ").append(val).append('\n');
                }
*/
            }
            synchronized(results[i]) {

                results[i].out = result.toString();
                //results[i].vals = vals;
                results[i].nvals = row.length; // formulaic reminder
                results[i].vertical = fr1.vertical;
                results[i].notifyAll();
            }

            //err("Result len " + results[i].out.length());

            result.setLength(0); // gc not working?
        }

        private static int x = 0;

        private void calcAngleRow(int i) {

            if (spb == null) err("angleRow spb null at i " + i);

            StringBuilder result = new StringBuilder();
            FileRec f1 = fileHists[i];
            String id1 = f1.id.id;
            double[] point1 = f1.histogram;
            int[] tmp_distr = new int[190];
            for (int ii=0; ii<tmp_distr.length; ii++) {
                tmp_distr[ii] = 0;
            }

            //long[] vals = new long[fileHists.length-i];
            int base = 0;
            for (int k=1; k<=i; k++) {
                base += fileHists.length - k - 1;
            }
            //if (i < 4) pout("i " + i + " base " + base);

            int row_size = fileHists.length - i - 1;
            double[] row = spb.getRow(i);
            if (row.length != row_size) {
                err("row size");
            }


            int jix = 0;

            for (int j=i+1; j<fileHists.length; j++) {

                FileRec f2 = fileHists[j];

                if (f1.vertical != f2.vertical) {
                    err("Internal error - mixed v,h");
                }

                String id2 = f2.id.id;

                double[] point2 = f2.histogram;

                double dot = 0.0;
                double dist = 0.0;

                for (int k=0; k<point1.length; k++) {

                    dot += point1[k] * point2[k];

                    double t = point1[k] - point2[k];
                    dist += t * t;
                }

                dist = Math.sqrt(dist);

                double cos = dot / (vlen[i] * vlen[j]);
                double angle = Math.acos(cos);

                // gold = 2.39996322972865332

                angle = Math.toDegrees(angle);

                long la = Math.round(angle);
                if (la >= 180L) {
                    err("angle > 180: " + la);
                }

                row[jix++] = la;

                int a = (int) la;

                tmp_distr[a]++;
/*
                if (ASCII) {
                    result.append(id1).append(" ").append(id2)
                        .append(" ").append(a).append('\n');;
                }
*/
            }

            synchronized(angle_distr) {

                for (int ii=0; ii<tmp_distr.length; ii++) {
                    angle_distr[ii] += tmp_distr[ii];
                }
            }

            synchronized(results[i]) {

                results[i].out = result.toString();
                //results[i].vals = vals;
                results[i].nvals = row.length; // formulaic reminder
                results[i].vertical = f1.vertical;
                results[i].notifyAll();
            }
        }
    }

    private void loadHistograms(boolean normalize) {

        int nthreads = MiscUtil.getProcs();

        pout("Loading histograms" + (normalize ? "/normalizing" : "") +
                " files: " + fileMap.size() + " threads: " + nthreads);

        if (normalize) {
            err("TODO - check normalize.. should be done already");
            //UtilFeature.normalizeL2(h); // normalize so that image size doesn't matter
        }

        long t1 = System.currentTimeMillis();
        fileHists = new FileRec[fileMap.size()];

        // put in order

        int i = 0;
        for (Map.Entry entry : fileMap.entrySet()) {
            FileRec fr = (FileRec) entry.getKey();
            fr.ct = i;
            fileHists[i] = fr;
            i++;
        }

        BlockingQueue<Integer> pQ = new
                        LinkedBlockingQueue<Integer>(nthreads);
        ExecutorService pWorkers =
                        Executors.newFixedThreadPool(nthreads);
        try {

            for (i=0; i<nthreads; i++) {
                pWorkers.execute(new pWorker(pQ, "read"));
            }
            for (i=0; i<fileHists.length; i++) {
                pQ.put(i);
            }
            for (i=0; i<nthreads; i++) {
                pQ.put(-1);
            }

            pWorkers.shutdown();
            pWorkers.awaitTermination(1, TimeUnit.HOURS);

        } catch (InterruptedException ie) {
            err("Interrupt: " + ie);
        }

        System.err.println("Loaded " +
                    (readBinHist>0 ? readBinHist + " binHists " : "") +
                    (readAsciiHist>0 ? readAsciiHist + " hists " : "") +
                    " threads " + nthreads +
                    " in: " +
                    MiscUtil.formatInterval(System.currentTimeMillis() - t1));

        readBinHistTot += readBinHist;
        readAsciiHistTot += readAsciiHist;
    }
    private static SymPairsBin spb = null;

    private static Result[] results = null;

    private void doSymmetricDistances(PrintStream tout, String tag, String func)
            throws Exception {

        pout("Symmetric distance, tag " + tag);

        Connection conn = DriverManager.getConnection("jdbc:postgresql:pr",
                                                      "pr", "@@pr");
        conn.setAutoCommit(false);
        List<String> ids = PictureDao.getIdsInOrder(conn, "v".equals(orient));
        conn.close();

        loadHistograms(true); // true==normalize

        spb = new SymPairsBin(ids);

        // from here on copied from histogram, delete it there? lib it?

        double dtot = 0.5 * ((double)fileHists.length * (fileHists.length-1));

        double ct = 0.0;

        long t0 = System.currentTimeMillis();

        // results is what we wants.. really just for sync w/ spb

        results = new Result[fileHists.length];
        for (int i=0; i<results.length; i++) {
            results[i] = new Result();
        }

        try {

            if (true) {

                pWorker pw = new pWorker(null, func);

                for (int i=0; i<fileHists.length-1; i++) {

                    pw.calcDistRow(i);
/*
                    if (ASCII) {
                        outAscii.print(results[i].out);
                    }
*/
                    ct += (double) results[i].nvals;

                    // free mem
                    //System.err.println("Free " + i);
                    results[i].out = null;
                    results[i].vals = null;
                    results[i].vertical = null;
                    results[i] = null;
                    fileHists[i].histogram = null;

                    if (i%100 == 0) {
                        System.err.printf("\rdists: %3.2f%%          ",
                                ((100.0 * ct) / dtot));
                    }
                }

            } else {

                int nthreads = MiscUtil.getProcs();

                BlockingQueue<Integer> pQ = new
                        LinkedBlockingQueue<Integer>(nthreads);
                ExecutorService pWorkers = Executors.newFixedThreadPool(nthreads);
                for (int i=0; i<nthreads; i++) {
                    pWorkers.execute(new pWorker(pQ, func));
                }
                int nextI = 0;
                for (int i=0; i<nthreads; i++) {
                    pQ.put(nextI); nextI++;
                }
                for (int i=0; i<fileHists.length-1; i++) {
                    synchronized(results[i]) {
                        if (results[i].out == null) {
                            results[i].wait();
                        }
                    }
                    if (nextI < fileHists.length-1) {
                        pQ.put(nextI); nextI++;
                    }
/*
                    if (ASCII) {
                        outAscii.print(results[i].out);
                    }
*/

                    ct += (double) results[i].nvals;

                    // free mem

                    //System.err.println("Free " + i);
                    results[i].out = null;
                    results[i].vals = null;
                    results[i].vertical = null;
                    results[i] = null;
                    fileHists[i].histogram = null;

                    if (i%100 == 0) {
                        System.err.printf("\rdists: %3.2f%%          ",
                                ((100.0 * ct) / dtot));
                    }
                }
                for (int i=0; i<nthreads; i++) {
                    pQ.put(-1);
                }
                pWorkers.shutdown();
                try {
                    pWorkers.awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    System.err.println("Interrupt: " + ie);
                    //System.exit(1);
                }
            }

            pout("Calced distances in " + ((System.currentTimeMillis()-t0)/1000) + " sec");

        } catch (Exception e) {
            e.printStackTrace();
            err("doSymmetricDistances: " + e);
        }

        pout("hist_dists: " + spb.toString() + " local ids " + ids.size());

        // go to town

        spb.writeTopPairs(tout, -1, ids, tag, 50, 50);
    }

    private static void doVectorDistances(String[] args)
            throws Exception {

        // arg check:
        //     0     1      2     3     4      5     6    ...
        //   <v|h>  vec  <file1> <func1 tag1> <func2 tag2> ...
        //               <file2> <func_n+1, tag_n+1> ...

        orient = args[0];
        if (!"v".equals(orient)  &&  !"h".equals(orient)) {
            usage("Orient needs to be v or h: " + orient);
        }


        Connection conn = DriverManager.getConnection("jdbc:postgresql:pr",
                                                      "pr", "@@pr");
        conn.setAutoCommit(false);
        List<String> ids = PictureDao.getIdsInOrder(conn, "v".equals(orient));
        conn.close();

        String tbl_name = "pr.pairtop_vec_" + orient;
        String outfname = tbl_name + "_dump.sql";

        File outfile = new File(outfname);

        if (outfile.exists()) {
            pout("Output file exists: " + outfname + " - removing in 5 sec");
            try { Thread.sleep(5000); } catch (Exception ignore) {}
            outfile.delete();
        }

        PrintStream out = new PrintStream(
                                new BufferedOutputStream(
                                    new FileOutputStream(
                                        outfile, false))); // no-append, all-in-one

        DumpGen.pairtopHeader(out, tbl_name);

        int arg_ix = 2;

        while(arg_ix < args.length) {

            // expecting file func1 tag1 func2 tag2 ...

            if (args.length - arg_ix < 3) {
                pout("Last seen: args[ix] " + args[arg_ix]);
                usage("vec: ning ergs<3: Need at least: fname, func, tag." +
                        " args.length " + args.length + " arg_ix " + arg_ix);
            }

            String fname = args[arg_ix++];

            NNVectors nnv = new NNVectors(ids, fname);

            // iterate <func,tag>
            for (; arg_ix<args.length; arg_ix+=2) {

                String func = args[arg_ix];

                if (nnv.notFunc(func)) {
                    if (NNVectors.isVecsFile(func)) {
                        break;
                    }
                    usage("vec: Expected func+tag or new fname: " + func);
                }

                String tag = args[arg_ix+1];

                nnv.writeTopLRPairs(out, -1, ids, tag, func, 50, 50);
            }
            if (arg_ix == args.length-1) {
                break;
            }
        }
        DumpGen.finishPairtopDump(out, tbl_name, null, null, null);
        out.close();

        pout("done ");
    }

    private static void doGoldenAngle(PrintStream tout, int d_i, File f)
            throws Exception {

        if (!f.canRead()) {
            err("Unreadable: " + f);
        }
        BufferedReader reader = new BufferedReader(new FileReader(f));

        String tag = "b" + d_i;

        pout("Golden angle, tag " + tag + " file " + f);

        // topDown == false because it's a delta
        TopSelector topSel = new TopSelector(tout, tag, 100, false);

        int lineCt = 0;

        while (true) {

            String line = reader.readLine();
            if (line == null) {
                pout("\033[2K\rEOF at line " + lineCt + " file " + f);
                break;
            }
            lineCt++;

            String ss[] = line.split(" ");

            if (ss.length != 3) {
                String nextline = reader.readLine();
                err("bad line " + lineCt + " file " + f + ":\n" + line +
                                            "\nnext\n" + nextline);
            }

            double val = Math.abs(137.5 - Double.parseDouble(ss[2]));

            topSel.add(ss[0], ss[1], (int) val);

            // entertain the troops

            if (lineCt > 0  &&  lineCt % 400000 == 0) {
                System.out.print("\033[2K\r\t\t\t" + //"\r\t\t\t" +
                                   msgs[rand.nextInt(msgs.length)] + "\r");
            } else if (lineCt % 50000 == 0) {
                System.out.print(".");
            }
        }
        topSel.partials();
        topSel.check(); // for stragglers
    }

    private static void doBillsAngle(PrintStream tout, int d_i, File f)
            throws Exception {

        if (!f.canRead()) {
            err("Uneadable: " + f);
        }
        BufferedReader reader = new BufferedReader(new FileReader(f));

        String tag = "b" + d_i;

        pout("Bill's angle, tag " + tag + " " + f);

        // topDown == false because it's a delta
        TopSelector topSel = new TopSelector(tout, tag, 100, false);

        int lineCt = 0;

        while (true) {

            String line = reader.readLine();
            if (line == null) {
                pout("\033[2K\rEOF at line " + lineCt + " " + f);
                break;
            }
            lineCt++;

            String ss[] = line.split(" ");

            if (ss.length != 3) {
                err("bad line " + lineCt + " " + f + ":\n" + line);
            }

            double val = Double.parseDouble(ss[2]);
            if (val < 120.0) {
                continue;
            }
            val = Math.abs(137.5 - Double.parseDouble(ss[2]));

            topSel.add(ss[0], ss[1], (int) val);

            // entertain the troops

            if (lineCt > 0  &&  lineCt % 400000 == 0) {
                System.out.print("\033[2K\r\t\t\t" + //"\r\t\t\t" +
                                   msgs[rand.nextInt(msgs.length)] + "\r");
            } else if (lineCt % 50000 == 0) {
                System.out.print(".");
            }
        }
        topSel.partials(); // for stragglers
    }

    /*
    **  PairsBin are npics*npics binary nn predictions, .pairs files from
    **      ppred.py, or .pb object files based on java predtool
    **      accumulating/summing .pairs and .pb files.
    **
    **  A PairsBin holds a full N^2 matrix of float32 for |v| or |h|,
    **      and can calculate top matches for each photo when it
    **      appears on the left (ranking right-side possibilities)
    **      or right (ranking left possibilities).
    **
    **      [Old verification of (predtool.py -top) #/entries: wc -l:
    **
    **          2487975 m_v_model_pn_81_81_ben_1024_10_48_Ka1277_RMSProp.top
    **          3279175 m_v_model_pn_81_81_ben_1024_7_38_Ka1277_Adamax.top
    **          2523730 m_v_model_pn_83_78_ben_1024_6_44_Ka1277_RMSProp.top
    **
    **              w/ v pics: 39473
    **      ]
    **
    **  Asymmetric, so A:B != B:A
    **
    **      (Symmetric ml vector distances are loaded from .top files
    **      created by 2_calc_vec_geom.sh)
    **      (Symmetric color distances are handled analogously to
    **      this case in SymPairsBin.)
    */

    private static void doNNTops(String[] args) throws Exception {

        // arg check:
        //  [<v|h> nn <label1> <dir_or_file> <label2> <dof>..]

        if (args.length < 4) {
            usage("args < 4");
        }

        orient = args[0];
        if (!"v".equals(orient)  &&  !"h".equals(orient)) {
            usage("Orient needs to be v or h: " + orient);
        }

        String tbl_name = "pr.pairtop_nn_" + orient;
        String outfname = tbl_name + "_dump.sql";

        Connection conn = DriverManager.getConnection("jdbc:postgresql:pr",
                                                      "pr", "@@pr");
        conn.setAutoCommit(false);
        List<String> ids = PictureDao.getIdsInOrder(conn, "v".equals(orient));
        conn.close();

        // check: tag1 <file|dir> tag2 ...

        if (args.length % 2 != 0) {
            usage("Arg count: expected pairs of: tag <file|dir>");
        }

        // gather actual files

        for (int i=2; i<args.length; i+=2) {

            String tag = args[i];
            String fname = args[i+1];

            pout("tag: " + tag);
            pout("Fname/dirname: " + fname);

            File f = new File(fname);
            if (!f.exists()) {
                usage("No <file|dir>: " + fname);
            }
            if (f.isFile()) {

                if (!fname.endsWith(".pairs")  &&
                    !fname.endsWith(".pb")) {

                    usage("nn (tag " + tag +
                                ") requires a .pairs or .pb file: " +
                                f.getAbsolutePath());
                }
                pout("File: " + fname + " tag " + tag);
                tags.add(tag);
                inFiles.add(f);

            } else {

                // assume dir and go no deeper

                String[] list = f.list();
                pout("Examining dir: " + fname + "  N_files: " + list.length);
                int npairs_f = 0;
                for (int j=0; j<list.length; j++) {

                    if (list[j].endsWith(".pairs")  ||
                        list[j].endsWith(".pb")) {

                        npairs_f++;
                        tags.add(tag + "|" + npairs_f); // start w/ 1
                        inFiles.add(
                                new File(
                                    fname + "/" + list[j]));
                        tagsCount.add(tag);
                        pout("(Dir): " + fname+"/"+list[j] + " tag " + tag);

                    }
                }
                if (npairs_f == 0) {
                    err("No .pairs files in dir: " + fname);
                }
            }
        }
        if (tags.size() == 0) {
            usage("No .pairs input files");
        }
        if (tags.size() != inFiles.size()) {
            err("inner fuckup, run!");
        }

        // go fow it

        File outfile = new File(outfname);
        if (outfile.exists()) {
            pout("Output file exists: " + outfname + " - removing in 5 sec");
            try { Thread.sleep(5000); } catch (Exception ignore) {}
            outfile.delete();
        }

        PrintStream out = new PrintStream(
                                new BufferedOutputStream(
                                    new FileOutputStream(
                                        outfile, false))); // no-append, all-in-one

        DumpGen.pairtopHeader(out, tbl_name);

        int lines_written = 0;
        for (int i=0; i<tags.size(); i++) {

            String tag = tags.get(i);
            File f = inFiles.get(i);
            String path = f.getPath();

            int vk = path.indexOf("/v_");
            if (vk == -1) vk = path.indexOf("/vb_");

            int hk = path.indexOf("/h_");
            if (hk == -1) hk = path.indexOf("/hb_");

            if (vk != -1  &&  hk != -1) {
                err("Both orientations /h[b]_ and /v[b]_ in path: " + path);
            }
            if (vk != -1) {
                path = path.substring(vk);
            } else if (hk != -1) {
                path = path.substring(hk);
            }
            pout("NNNeuNNet, tag " + tag + "\nfile " + path);

            //tagFiles.put(tag + XXX, f.getName());

            String msgrmPath = f.getParent() + "/";
            int if_npairs = -1;

            PairsBin pairsBin = null;

            if (path.endsWith(".pairs")) {
                pairsBin = new PairsBin(f.getPath(), ids.size());
            } else {
                pairsBin = PairsBin.load(f.getPath(), ids.size());
            }
            int lines = pairsBin.writeTop(out, -1, ids, tag, 50, 50);
            lines_written += lines;

            pout("\nFile npairs: " + lines + "\ttot\t" + lines_written);
        }
        DumpGen.finishPairtopDump(out, tbl_name, tagsCount, tags, inFiles);
        out.close();
    }

    private static void doCopyTops(String[] args) throws Exception {

        // arg check:
        //     0      1      2     3      4     5    ...
        //  [<v|h> include <tag1 file1> <tag2 file2> ...

        if (args.length < 4) {
            usage("args < 4");
        }
        if (args.length % 2 != 0) {
            usage("include: need paired <tag file>");
        }

        orient = args[0];
        if (!"v".equals(orient)  &&  !"h".equals(orient)) {
            usage("Orient needs to be v or h: " + orient);
        }

        pout("Checking files..");

        for (int i=2; i<args.length; i+=2) {

            String tag = args[i];
            String fname = args[i+1];

            pout("tag/fname: " + tag + " " + fname);

            if (tags.contains(tag)) {
                err("Duplicate tag: " + tag);
            }

            File f = new File(fname);

            if (inFiles.contains(f)) {
                err("Duplicate file: " + f.getName());
            }

            if (!f.exists()) {
                usage("No <file>: " + fname);
            }
            if (!f.isFile()) {
                usage("Not a file: " + fname);
            }
            if (!f.canRead()) {
                err("Uneadable: " + f);
            }

            tags.add(tag);
            inFiles.add(f);
        }

        if (tags.size() == 0) {
            usage("No input files");
        }
        if (tags.size() != inFiles.size()) {
            err("infarcted, rung!");
        }

        String tbl_name = "pr.pairtop_vec_" + orient;
        String outfname = tbl_name + "_dump.sql";

        File outfile = new File(outfname);

        if (outfile.exists()) {
            pout("Output file exists: " + outfname + " - removing in 5 sec");
            try { Thread.sleep(5000); } catch (Exception ignore) {}
            outfile.delete();
        }

        PrintStream out = new PrintStream(
                                new BufferedOutputStream(
                                    new FileOutputStream(
                                        outfile, false))); // no-append, all-in-one

        DumpGen.pairtopHeader(out, tbl_name);

        int lines_read = 0;

        for (int i=0; i<tags.size(); i++) {

            String tag = tags.get(i);
            File f = inFiles.get(i);

            pout("Copying tag " + tag + " file " + f);

            BufferedReader in = new BufferedReader(new FileReader(f));

            int fline = 0;

            while (true) {

                String s = in.readLine();
                if (s == null) {
                    break;
                }
                fline++;

                String[] ss = s.split(" ");
                if (ss.length != 3) {
                    err("Expected 3 fields: [" + s.trim() + "] in " +
                            f.getName() + " line " + fline);
                }

                out.println(ss[0] + '\t' + ss[1] + '\t' + tag + '\t' + ss[2]);

            }
            in.close();
            pout("lines read: " + fline);
            lines_read += fline;
        }

        pout("Copied/tagged " + lines_read + " lines from " +
                    tags.size() + " files");

        DumpGen.finishPairtopDump(out, tbl_name, tagsCount, tags, inFiles);
        out.close();
    }
}
