package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
  *  PairFileAggregator - add, or get percent of values > cutoff (e.g. 0.5)
  *
  *  Format: id1 id2 val01 val10
  */

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintStream;

public class PairFileAggregator extends Stdio {

    // per-instance

    public String id = "<id>";

    private List<File> dirs = new ArrayList<>();
    private List<Integer> fileIndexes = new ArrayList<>();

    private final boolean add; 
    private final double cutoff; // if add==true

    //  the file-reading part is a singleton for all Aggregators' files.

    private PairFilesReader reader = PairFilesReader.getReader();

    public PairFileAggregator(String id, 
                              List<File> indirs, boolean add, double cutoff) 
            throws Exception {

        if (add) {
            pout("PairFileAggregator: '" + id + "' pos: " +
                 " dirlist size " + indirs.size());
        } else {
            pout("PairFileAggregator: '" + id + "' negs w/ cutoff " + cutoff + 
                 " dirlist size " + indirs.size());
        }

        this.id = id + (add ? "/avg" : "/neg");
        this.add = add;
        this.cutoff = cutoff;

        // populate files

        for (File dir : indirs) {
            if (!dir.isDirectory()) {
                throw new Exception("PairFileAggregator." + id + 
                                        ": Not a dir: " + dir);
            }
            dirs.add(dir);
            String[] tt = dir.list();
            String path = dir.getAbsolutePath() + "/";
            for (String s : tt) {
                if (s.endsWith(".pairs")) {
                    String fname = path + s;
                    
                    fileIndexes.add(reader.indexFile(fname));
                }
            }
        }

        finish();
    }

    public PairFileAggregator(String id, 
                              String path, boolean add, double cutoff) 
            throws Exception {

        this.id = id;
        this.add = add;
        this.cutoff = cutoff;

        // scan dir for .pairs files to read and open them
        //  (for recursive scan, move pairtop scanner here)

        File dir = new File(path);
        if (!dir.isDirectory()) {
            throw new Exception("PairFileAggregator." + id +
                                    ": Not a dir: " + path);
        }
        this.dirs.add(dir);

        String[] tt = dir.list();
        int nfiles = 0;
        for (String s : tt) {
            if (s.endsWith(".pairs")) {
                String fname = path + "/" + s;

                fileIndexes.add(reader.indexFile(fname));
            }
        }

        finish();
    }

    private void finish() throws Exception {

        if (fileIndexes.size() == 0) {
            throw new Exception("PairFileAggregator." + id +
                                " No .pairs files in " + dirs.toString());
        }

        if (add) {
            pout("Aggregator: add val12, val21: " +
                                " dirs " + dirs.size() +
                                " files: " + fileIndexes.size());
        } else {
            pout("Aggregator: % val12, val21 over " + cutoff + 
                                " dirs " + dirs.size() +
                                " files: " + fileIndexes.size());
        }
    }

    /**
      *  readLine() - aggregate the values from the lines.
      *             Call PairFilesReader.next() before reader.readLines
      *             to increment line, with lineNum and id's checked .
      */

    public double[] readLine() throws Exception { 

        int ndone = 0;

        double arr[] = { 0.0, 0.0 };

        if (add) {

            for (int fileIndex : fileIndexes) {

                double t[] = reader.readLine(fileIndex);

                arr[0] += t[0];
                arr[1] += t[1];
            }
             
            // normalize by file count
            arr[0] /= fileIndexes.size();
            arr[1] /= fileIndexes.size();

        } else {

            for (int fileIndex : fileIndexes) {

                double t[] = reader.readLine(fileIndex);

                if (t[0] > cutoff) {
                    arr[0]++;
                }
                if (t[1] > cutoff) {
                    arr[1]++;
                }
            }

            arr[0] = (double) (100 * arr[0]) / fileIndexes.size();
            arr[1] = (double) (100 * arr[1]) / fileIndexes.size();

        } 

        return arr;
    }
}
