
package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  FileRec - file record
 **
 */

import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;

import java.io.File;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.io.EOFException;

//import java.io.FileReader;
//import java.io.BufferedReader;
//import java.io.IOException;

import javax.naming.InvalidNameException;

public class FileRec extends Stdio implements Comparable {

    public int ct;

    public String fname;

    public ID id;

    public File file;
    public boolean vertical;
    public double[] histogram;

    @Override
    public String toString() {
        return "FileRec: " +
            (id == null ? "id=null " : id.id + " ") +
            (fname == null ? "fname=null " : fname) +
            (file == null ? "file=null " : file.getPath());
    }

    @Override
    public int compareTo(Object o) {

        FileRec fr = (FileRec) o;

        if (id == null  &&  fr.id == null) {

            return file.compareTo(fr.file);
        }
        int cmp = id.compareTo(fr.id);
        if (cmp != 0) return cmp;
        return fname.compareTo(fr.fname);
    }

    public FileRec(File file, boolean expectArchSeq)
            throws InvalidNameException {

        init(file, expectArchSeq);
    }

    public FileRec(String path, boolean expectArchSeq)
            throws InvalidNameException {

        file = new File(path);

        if (!file.isFile()) {

            String hpath = path.replace(".jpg", ".hist_bin");
            file = new File(hpath);
            if (!file.isFile()) {
                hpath = path.replace(".jpg", ".hist");
                file = new File(hpath);
                if (!file.isFile()) {
                    throw new RuntimeException(
                        "Not a file (and no .hist or .hist_bin): " + path);
                }
            }
        }

        init(file, expectArchSeq);
    }

    private void init(File file, boolean expectArchSeq)
            throws InvalidNameException {


        this.file = file;

        // get fname, check reqd substrings

        String path = file.getAbsolutePath();

        int fi = path.lastIndexOf("/");
        if (fi == -1) {
            throw new RuntimeException("No '/': " + path);
        }

        this.fname = path.substring(fi+1);
        if (this.fname.length() == 0) {
            throw new RuntimeException("No fname: " + path);
        }

        int endFnameBody = this.fname.indexOf(".jpg");
        if (endFnameBody == -1) {
            endFnameBody = this.fname.indexOf(".jpeg");
            if (endFnameBody == -1) {
                endFnameBody = this.fname.indexOf(".hist_bin");
                if (endFnameBody == -1) {
                    endFnameBody = this.fname.indexOf(".hist");
                }
            }
        }
        if (endFnameBody == -1) {
            throw new RuntimeException(
               "FileRec: Expected " +
                    "'.jpg' or '.jpeg' or '.hist_bin' or '.hist', path=" +
                    path);
        }

        if (expectArchSeq) {
/*
            int ix;
            for (ix=0; ix<endFnameBody; ix++) {
                if (Character.isDigit(s.charAt(ix))) {
                    break;
                }
            }
            if (ix == endFnameBody) {
                throw new RuntimeException(
                       "FileRec: Expected a number in filename: " + path);
            }
*/
            String fnameBody = fname.substring(0, endFnameBody);

            // get archive

            String a = null;

            int ix = fnameBody.indexOf(":");
            if (ix != -1) {

                // file base name is already 
                //  the actual id of pic

                a = fnameBody.substring(0, ix);
                fnameBody = fnameBody.substring(ix+1);

            } else {
                for (int i=fi-1; i>-1; i--) {

                    if (path.charAt(i) == '/') {
                        a = path.substring(i+1, fi);
                        break;
                    }
                }
            }
            if (a == null) {
                throw new RuntimeException("No archive: " + path);
            }

            int arch = -1;
            try {
                arch = Integer.parseInt(a);
            } catch (NumberFormatException nfe) {
                throw new RuntimeException("Parsing archive: [" +
                                           a + "] [" + path + "]: " + nfe);
            }
            if (arch == -1) {
                throw new RuntimeException("No archive: " + path);
            }
            this.id = new ID(arch, fnameBody);
        }
    }

    public void readHistogram() {

        if (this.id == null) {
            pout("Internal error:");
            new Exception().printStackTrace();
            err("==> FileRec.readHistogram: no id??");
        }

        if (this.fname == null) {
            pout("Internal error:");
            new Exception().printStackTrace();
            err("==> FileRec.readHistogram: fname is null");
        }
        try {

            if (this.fname.endsWith("bin")) {

                readBinHistogram();

            } else if (this.fname.endsWith(".hist")) {

                readAsciiHistogram();

            } else {

                err("FileRec.readHistogram: not a histogram filename: " + this.fname);
            }

        } catch (Exception e) {

            e.printStackTrace();

            err("Exiting on " + e);
        }
    }

    public void readBinHistogram() {

        List<Double> l = new ArrayList<>();

        DataInputStream dis = null;

        try {

            dis = new DataInputStream(
                                    // ? new BufferedInputStream(
                                    new FileInputStream(this.file));
            while(true) {
                l.add((double)dis.readFloat());
            }

        } catch (EOFException eoe) {
            // expected
            if (l.size() == 0) {
                err("Nothing read: " + toString());
            }
            this.histogram = l.stream().mapToDouble(d -> d).toArray();
            
        } catch (Exception e) {

            pout("-- Error reading array: " + e);
            e.printStackTrace();
            err("Exiting");

        } finally {
            if (dis != null) {
                try { dis.close(); } catch (Exception e) { err("close: " + e);}
            }
        }
    }

    public void readAsciiHistogram() throws Exception {

        List<Double> l = new ArrayList<>();
        Scanner s = new Scanner(new FileReader(this.file));
        while (s.hasNext()) {
            l.add(s.nextDouble());
        }
        double d[] = new double[l.size()];
        for (int i=0; i< l.size(); i++) {
            d[i] = l.get(i);
        }
        this.histogram = d;
    }
}
