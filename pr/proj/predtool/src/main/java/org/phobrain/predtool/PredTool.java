package org.phobrain.pairtop;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  PredTool/cmdline - intermediate calcs on numpy 1D arrays written
 **     pred.byteswap().tofile(path), viewed as 2D picA-picB predictions.
 **
 **/

import org.phobrain.util.Stdio;
import org.phobrain.util.PairsBin;
import org.phobrain.util.MiscUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.HashSet;
//import java.util.Map;
//import java.util.TreeMap;
//import java.util.HashMap;

public class PredTool extends Stdio {

    private static void usage(String msg) {
        if (msg != null) {
            System.err.println("Error: " + msg);
        }
        System.err.println("Usage:" +
                   "\n\t<npics> -sum <dir>");

        System.exit(1);
    }

    private static int npics = -1;

    public static void main(String[] args) {

        if (args.length == 0) {
            usage(null);
        }
        if (args.length != 3) {
            usage("Expected 3 args");
        }

        try {
            npics = Integer.parseInt(args[0]);
        } catch (NumberFormatException nfe) {
            usage("Expected integer: " + args[0]);
        }
        if (npics < 1) {
            usage("Expected pics > 0: " + args[0]);
        }

        String cmd = args[1];
        String dir = args[2];

        pout("npics " + npics + " cmd " + cmd + " dir " + dir);

        try {
            if ("-sum".equals(cmd)) {
                sum(dir);
                return;
            } else {
                usage("-sum is only cmd");
            }
        } catch (Exception e) {
            err("ouch: " + e);
            e.printStackTrace();
        }
    }

    private static void sum(String dir)
            throws Exception {

        File d = new File(dir);
        if (!d.isDirectory()) {
            usage("Not a dir: " + dir);
        }

        List<File> dirsWithPairs = new ArrayList<>();
        List<File> dirsWithoutPairs = new ArrayList<>();

        sortDirs(d, dirsWithPairs, dirsWithoutPairs);

        for (File dp : dirsWithPairs) {
            sumDirPairs(dp);
        }

        // deeper dirs are first
        for (File dp : dirsWithoutPairs) {
            sumDirChildrenPB(dp);
        }
    }

    private static void sortDirs(File root, List<File> dirsWithPairs, List<File> dirsWithoutPairs)
            throws Exception {

        List<File> dirs = new ArrayList<>();

        int hasPairs = 0;
        int hasPB = 0;

        String[] list = root.list();

        for (String fname : list) {

            String path = root.getPath() + "/" + fname;

            File f = new File(path);

            if (f.isDirectory()) {
                dirs.add(f);
            } else if (path.endsWith(".pairs")  &&
                      path.indexOf("_avg_") == -1   &&
                      path.indexOf("_add_") == -1) {
pout(path);
                hasPairs++;
            } else if (path.endsWith(".pb")) {
                hasPB++;
            }
        }

        pout("Dir: " + root.getPath() + " " + hasPairs + " .pairs " + hasPB + " .pb");

        for (File d : dirs) {
            sortDirs(d, dirsWithPairs, dirsWithoutPairs);
        }

        // Classifying after recursion == building dir lists from leaves up
        //  - enables dirsWithoutPairs to fill in a pyramid.

        if (hasPairs > 0) {
            dirsWithPairs.add(root);
        } else {
            dirsWithoutPairs.add(root);
        }
    }

    private static void sumDirPairs(File dir)
            throws Exception {

        List<String> origPairs = new ArrayList<>();
        List<String> pbs = new ArrayList<>();

        String[] list = dir.list();

        for (String fname : list) {

            if (fname.endsWith(".pb")) {
                pbs.add(fname);
            } else if (fname.endsWith(".pairs")) {
                if (fname.contains("_avg_")  ||  fname.contains("_add_")) {
                    continue;
                }
                origPairs.add(fname);
            }
        }
        String pbname = PairsBin.dirPBName(origPairs.size());

        if (pbs.contains(pbname)) {

            pout("Done/skipping " + dir.getPath() + "/" + pbname);

        } else {

            pout("Doing " + pbname);

            PairsBin pairsBin = null;

            for (String fname : origPairs) {

                String path = dir.getPath() + "/" + fname;

                if (pairsBin == null) {
                    pairsBin = new PairsBin(path, npics);
                } else {
                    pairsBin.add(path);
                }

            }

            pairsBin.write(dir.getPath());
        }
    }

    private static void sumDirChildrenPB(File dir)
            throws Exception {

        String[] list = dir.list();

        List<String> childPBs = new ArrayList<>();

        for (String fname : list) {

            File f = new File(dir.getPath() + "/" + fname);

            if (!f.isDirectory()) {
                continue;
            }

            String[] sublist = f.list();
            int pbs = 0;
            for (String subfname : sublist) {

                if (!subfname.endsWith(".pb")) {
                    continue;
                }

                if (pbs++ > 0) {
                    err(">1 .pb in " + f.getPath());
                }
                String path = f.getPath() + "/" + subfname;

                childPBs.add(path);

            }
        }
        if (childPBs.size() == 0) {
            pout("No child .pb's: " + dir.getPath());
            return;
        }
        int baseCount = 0;
        for (String path : childPBs) {
            baseCount += PairsBin.parseDirPBSize(path);
        }
        String pbname = PairsBin.dirPBName(baseCount);
        File f = new File(dir.getPath() + "/" + pbname);
        if (f.isFile()) {
            pout("Already done/skipping: " + pbname);
            return;
        }

        pout("Doing " + pbname);

        PairsBin pairsBin = null;

        for (String path : childPBs) {

            if (pairsBin == null) {
                pairsBin = PairsBin.load(path, npics);
            } else {
                pairsBin.add(path);
            }
        }

        if (pairsBin == null) {
            pout("No .pb files in child dirs (1-level): " + dir.getPath());
        } else {
            pairsBin.write(dir.getPath());
        }
    }
}
