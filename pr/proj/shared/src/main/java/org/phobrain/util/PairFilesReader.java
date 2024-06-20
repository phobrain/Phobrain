package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
  *  PairFilesReader - singleton - read PairValues from a set of NN-generated
  *     .pairs files 
  *
  *  Files' Format: id1 id2 val12 val21
  *
  *  readers: 5 406 GB/sec i9/nvme
  *           6 445
  *           7 378
  *           8 320
  */

import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.FileReader;

import java.util.concurrent.LinkedBlockingQueue;


public class PairFilesReader extends Stdio {

    // HACK til I go back to the more-sensible colon separators,
    //   arch:seqstr vs. arch/seqstr
    final static boolean swapColonSeparator = true;

    final static int READAHEAD_QUEUE_SIZE = 64;

    public static int readThreads;

    private static Thread readerThreads[] = null;
    static LinkedBlockingQueue<PairValues[]> pvQs[];

    /**
      *  singleton
      */

    private PairFilesReader() {
        readThreads = MiscUtil.getProcs();
        if (readThreads > 10) {
            readThreads = 10;
        } else if (readThreads > 2) {
            readThreads--;
        }

        pout("PairFilesReader: read threads: " + readThreads);

    }

    private static class PairValues {
        int lineNum = -1;
        String id1 = null;
        String id2 = null;
        double[] arr = {0.0, 0.0}; // size 2, 
                                    // pics right-left, left-right: 01, 10 
        PairValues(int lineNum) {
            this.lineNum = lineNum;
        }
    }
    private static final List<File> files = new ArrayList<>();

    /**
      *  getReader() - get singleton to add files to with indexFile()
      */

    private static PairFilesReader instance = null;
    public static synchronized PairFilesReader getReader() {
        if (instance == null) {
            instance = new PairFilesReader();
        }
        return instance;
    }

    private final Set<String> fnameSet = new HashSet<>();

    /**
      *  indexFile() - add files to the singleton until ready 
      *     to start reading, line-by-line across.
      */
    private static boolean initted = false;

    synchronized public int indexFile(String fname) {
        if (initted) {
            err("Too late! You initted!!!" +
                    " No adding (or even looking up.. add?)");
        }
        if (!fnameSet.contains(fname)) {
            //pout("Adding file for read: " + fname);
            files.add(new File(fname));
            fnameSet.add(fname);
            return files.size() - 1;
        }
        for (int i=0; i<files.size(); i++) {
            File f = files.get(i);
            if (fname.equals(f.getAbsolutePath())) {
                return i;
            }
        }
        err("Internal error: " + fname); // exits
        return -1;
    }

    // === init() stuff

    // current line of per-file PV's, supplied by pvQs, 
    //   which are started/incremented by next()
    private PairValues showPVs[] = null;

    private boolean done = false;
    private static int nullCt = 0;
    /**
      * init() - call when done calling indexFile()
      */

    private static long t0 = System.currentTimeMillis();

    public static void init() {

        if (initted) {
            err("init() already done, dude");
        }

        t0 = System.currentTimeMillis();

        if (files.size() < readThreads) {
            readThreads = files.size() / 2;
            if (readThreads == 0) readThreads = 1;
            pout("PairFilesReader: few files, lowering readThreads to " + 
                        readThreads);
        } else if (readThreads > files.size() / 5) {
            readThreads = files.size() / 5;
            pout("PairFilesReader: few files, lowering readThreads to " + 
                        readThreads);
        }
        if (readThreads == 0) {
            readThreads = 1;
        }
        pout("PairFilesReader: init with " + files.size() + " files " +
                                    readThreads + " reader threads " +
                                    " at " + new Date());


        pvQs = new LinkedBlockingQueue[readThreads];

        for (int i=0; i<readThreads; i++) {
            pvQs[i] = new LinkedBlockingQueue<>(READAHEAD_QUEUE_SIZE);
        }

        readerThreads = new Thread[readThreads];
        final double filesPerThread = (double) files.size() / readThreads;
        for (int i=0; i<readThreads; i++) {

            final int reader = i;
            final int start = (int) (i * filesPerThread);
            final int end = (i == readThreads - 1 ?
                                    files.size()
                                    : (int) ((i+1) * filesPerThread)
                            );

            //pout("PairFilesReader thread " + i + 
            //                        " start " + start + 
            //                        " end " + end);

            readerThreads[i] = new Thread(
                                new Runnable() {
                @Override
                public void run() {

                    int nIn = end - start;

                    BufferedReader[] in = new BufferedReader[nIn];

                    int iIn = 0;

                    try {
                        for (iIn=0; iIn<nIn; iIn++) {
                            in[iIn] = new BufferedReader(
                                        new FileReader(files.get(start+iIn)));
                        }
                    } catch (FileNotFoundException fnf) {
                        err("PairFilesReader: HUH?? " + files.get(start+iIn) +
                                ": " + fnf.toString());
                    }

                    String[] in_lines = new String[nIn];
                    String split_lines[][] = new String[nIn][];

                    int lineNum = 0;
                    try {
                        while (readLineAcrossAndQueue(in, 
                                                      in_lines, split_lines,
                                                      reader, ++lineNum,
                                                      start, end)); 
                                                // blocks on queueing
                    } catch (Exception e) {
                        e.printStackTrace();
                        err("PairFileAggregator: Read loop: " + e);
                    }
                }
            }, "PairFilesReader-" + i);
            readerThreads[i].start();
        }
    }

    private Set<Integer> nextLinedFiles = new HashSet<>();
    private int readLineCount = 0;

    private int next_count = 0;

    public boolean next(int lineNum, String id1, String id2) 
            throws Exception {

        if (next_count != 0  &&  nextLinedFiles.size() != files.size()) {
            pout("WARN - calling next() with readLines on only " + 
                    nextLinedFiles.size() + " of " + files.size() + " files");
        }

        PairValues pvs[] = new PairValues[files.size()];
        int ix = 0;

        int endedReaders = 0;

        for (int i=0; i<pvQs.length; i++) {
            PairValues[] reader_pvs = pvQs[i].take();  // blocks

            if (reader_pvs.length == 0) {
                endedReaders++;
                continue;
            }

            PairValues pv0 = reader_pvs[0];

            if (pv0.lineNum != lineNum) {
                throw new Exception("PairFilesReader.next():" + 
                                " caller/reader lineNum mismatch," +
                                " caller expected " + lineNum + 
                                " [" + id1 + " " + id2 + "] " +
                                " at line " + pv0.lineNum +
                                " Q size " + pvQs[i].size() +
                                " EOFs " + nullCt + "/" + files.size() +
                                " file " + files.get(i).getPath());
            }

            if (pv0.id1 == null  ||  pv0.id2 == null) { 
                pout("returning false on null id(s) line " + pv0.lineNum);
                return false;
            }

            if (!pv0.id1.equals(id1)  ||  !pv0.id2.equals(id2)) {
                throw new Exception("PairFilesReader.next():" + 
                                    " caller/reader id mismatch," +
                                    " expected [" + id1 + " "+ id2 +
                                    "] got [" + pv0.id1 + " " + pv0.id2 +
                                    "] at line " + pv0.lineNum +
                                    " Q size " + pvQs[i].size() +
                                    " EOFs " + nullCt + "/" + files.size() +
                                    " file " + files.get(i).getPath());
            }

            for (int j=0; j<reader_pvs.length; j++) {
                pvs[ix++] = reader_pvs[j];
            }
        }

        if (endedReaders == readerThreads.length) {  

            // all reader_pvs.length == 0

            for (int i=0; i<pvQs.length; i++) {
                readerThreads[i].join();
                readerThreads[i] = null;
            }

            return false;
        }

        if (ix != files.size()) {
            err("FilesReader.next(lineNum=" + lineNum + 
                    ": ix " + ix + " files " + files.size());
        }
        showPVs = pvs;

        readLineCount = 0;
        nextLinedFiles = new HashSet<>();

        return true;
    }

    /**
      *  readLine() - call on all files any number of times 
      *     after init() and next() have been called.
      */

    public double[] readLine(int fileIndex) 
            throws Exception {

        if (showPVs == null) {
            err("next() not called");
        }

        if (fileIndex >= showPVs.length) {
            err("fileIndex " + fileIndex + " >= pvs.length " + showPVs.length);
        }

        readLineCount++;
        nextLinedFiles.add(fileIndex);

        PairValues pv = showPVs[fileIndex];

        return pv.arr;
    }

    public void done() {

        try {
            System.out.print("PairFilesReader: check if done on pvQs: "); 
            for (int i=0; i<pvQs.length; i++) {
                // pout in case it hangs
                //pout("PairFilesReader: check if done on pvQs " + i); 
                System.out.print(" " + i);
                PairValues[] reader_pvs = pvQs[i].take();  // blocks
                if (reader_pvs.length > 0) {
                    err("PairReaders pvQs[" + i + "].length is " + reader_pvs);
                }
            }
            pout(" done");
        } catch (InterruptedException ie) {
            pout("PairFilesReader.done(): " + ie);
        }
        long bytes = 0;
        for (File f : files) {
            bytes += f.length();
        }
        long mbytes = bytes / (1024 * 1024);
        long gbytes = mbytes / 1024;

        long sec = (System.currentTimeMillis() - t0) / 1000;

        pout("PairFilesReader.done():" +
                " read " + gbytes + " GB" +
                " in " + (sec/60) + " min" +
                " rate " + (mbytes/sec) + " MB/sec" +
                " using " + readThreads + " readers");
    }

    @Override
    public String toString() {
        return "PairFilesReader, reader threads " + readerThreads + 
                    " files: " + files.size();
    }

    /**
      * readLineAcrossAndQueue() - puts PairValues[] in pvQ 
      *     return true if EOFs not seen; 
      *            false if EOFs seen, and put empty PV[] array in pvQs[reader].
      */

    private static boolean readLineAcrossAndQueue(BufferedReader[] in,
                                                  String[] in_lines,
                                                  String[][] split_lines,
                                                  int reader, int lineNum,
                                                  int start, int end) 
            throws Exception {

boolean NEW = false;

        //  read
        List<Integer> nulls = new ArrayList<>();
        List<Integer> nonnulls = new ArrayList<>();
        for (int i=0; i<in.length; i++) {

            if (NEW) {
                err("TODO");
                //split_lines[i] = readFields(in[i]);
            } else {
                in_lines[i] = in[i].readLine();
                if (in_lines[i] != null) {
                    in_lines[i] = in_lines[i].replaceAll(":", "/");
                    split_lines[i] = in_lines[i].split(" ");
                } else {
                    split_lines[i] = null;
                }
            }
            if (split_lines[i] == null) {
                nulls.add(i);
            } else {
                nonnulls.add(i);
            }
        }

        //  sync error 1: ending
        if (nonnulls.size() == 0) { // all null
            pout("PairFilesReader reader " + reader +
                    " internally done reading at line " + lineNum + 
                    " Q size is " + pvQs[reader].size());
            pvQs[reader].put(new PairValues[0]); // blocks - empty array
            return false;
        } 
        if (nulls.size() > 0) {
            StringBuilder sb = new StringBuilder("nulls: ");
            for (int i : nulls) {
                sb.append('\t')
                  .append( files.get(nulls.get(i)).getPath())
                  .append('\n');
            }
            sb.append("non-nulls:\n");
            for (int i : nonnulls) {
                sb.append('\t')
                  .append(files.get(nonnulls.get(i)).getPath())
                  .append("\n\t\t")
                  .append(in_lines[nonnulls.get(i)])
                  .append('\n');
            }

            err("PairFilesReader: reader " + reader + 
                    " Uneven end on " + nullCt + "/" + (end - start) +
                    " files, line " + lineNum + 
                    " Q size " + pvQs[reader].size() +
                    ":\n" + sb);
        }

/*
        for (int i=0; i<in_lines.length; i++) {

if (in_lines[i] == null) err("in line null at " + i);

            split_lines[i] = in_lines[i].split(" ");
            if (split_lines[i].length != 4) {
                err("PairFilesReader reader " + reader +
                        " Not 4 fields: " + files.get(i) + 
                        " line " + lineNum + ":\n" + in_lines[i]);
            }
            if (swapColonSeparator) {
                split_lines[i][0] = split_lines[i][0].replace(":", "/");
                split_lines[i][1] = split_lines[i][1].replace(":", "/");
            }
        }
*/
        // make array for pvQ, with id's in 0th elt

        PairValues pvs[] = new PairValues[in.length];
        PairValues pv0 = new PairValues(lineNum);

        pvs[0] = pv0;

        pv0.id1 = split_lines[0][0];
        pv0.id2 = split_lines[0][1];

        // make other pvs, checking internal id1,id2 consistency with pv0

        for (int i=1; i<pvs.length; i++) {

            int ix = start + i;

            String err = "";
            if (!pv0.id1.equals(split_lines[i][0])) {
                err = "id1";
            }
            if (!pv0.id2.equals(split_lines[i][1])) {
                err += " id2";
            }
            if (!"".equals(err)) {
                err("PairFilesReader: reader " + reader + ": " + err + "\n" + 
                             files.get(ix) + 
                             ": File " + ix + 
                             ": mismatch: line " + pv0.lineNum +
                             "\nexpected: [" + pv0.id1 +" "+ pv0.id2 +
                             "]\nGot:      [" + split_lines[i][0] + " " +
                                                split_lines[i][1] +
                             "]\nActual line: [" + in_lines[i] + "]");
            }

            PairValues pv = new PairValues(lineNum);

            pv.id1 = split_lines[i][0];
            pv.id2 = split_lines[i][1];

            pvs[i] = pv; // i, not ix
        }

        // parse all numerical values into pvs, wrap parseDouble exceptions

        int i = 0;
        try {

            for (i=0; i<pvs.length; i++) {

                PairValues pv = pvs[i];

                pv.arr[0] = Double.parseDouble(split_lines[i][2]);
                pv.arr[1] = Double.parseDouble(split_lines[i][3]);
            }

        } catch (NumberFormatException nfe) {
            nfe.printStackTrace();
            err("Number prob file=" + (start+i) + " line " + lineNum +
                    " " + pv0.id1 + " " + pv0.id2 +
                    "\nreader_file " + i + ":\n  " + 
                    files.get(start+i).getAbsolutePath() +
                    "\n  [" + in_lines[i] + "]");
        }

        pvQs[reader].put(pvs); // blocks

        return true;
    }

}
