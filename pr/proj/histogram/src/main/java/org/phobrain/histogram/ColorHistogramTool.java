package org.phobrain.histogram;

/*
 *  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 *
 *  SPDX-License-Identifier: CC-BY-SA-4.0
 */

// === ColorHistogramTool.java - histograms and histogram distances,
//                              interactive search by histogram of
//                              images under a dir.
//
//          Phobrain-specific: generate postgres table dump
//                              with top-50 per-pic pair distances,
//                              using Poincare distance.
//
//          Based on boofcv's ExampleColorHistogramLookup.java,
//                              by Peter Abeles.

import org.phobrain.util.Stdio;
import org.phobrain.util.MathUtil;
import org.phobrain.util.HashCount;
import org.phobrain.util.MiscUtil;
import org.phobrain.util.FileRec;
import org.phobrain.util.SymPairsBin;

import org.phobrain.db.util.DumpGen;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;

import boofcv.alg.color.ColorXyz;
import boofcv.alg.color.ColorHsv;
import boofcv.alg.color.ColorLab;
import boofcv.alg.descriptor.UtilFeature;
import boofcv.alg.feature.color.GHistogramFeatureOps;
import boofcv.alg.feature.color.HistogramFeatureOps;
import boofcv.alg.feature.color.Histogram_F64;

import boofcv.gui.ListDisplayPanel;
import boofcv.gui.image.ScaleOptions;
import boofcv.gui.image.ShowImages;

import boofcv.io.UtilIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;

import boofcv.misc.BoofMiscOps;

import boofcv.struct.feature.TupleDesc_F64;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.Planar;

import org.ddogleg.nn.FactoryNearestNeighbor;
import org.ddogleg.nn.NearestNeighbor;
import org.ddogleg.nn.NnData;
import org.ddogleg.nn.alg.distance.KdTreeEuclideanSq_F64;
import org.ddogleg.struct.DogArray;

import java.awt.image.BufferedImage;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
//import java.io.RandomAccessFile;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.Comparator;
import java.util.Scanner;
import java.util.Date;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.naming.InvalidNameException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.FileImageInputStream;

public class ColorHistogramTool extends Stdio {

    // cmdline-related

    final static boolean POINC = false;

    final static boolean ASCII = false;

    static boolean by_archive = false;
    static boolean concat_output = false;

    static List<String> createFiles = new ArrayList<>();

    // for -pairtop option

    static File dbdump = null;
    static PrintStream dbout = null;
    static String db_tbl_name = null;
    static HashCount tagsCount = new HashCount();
    static SymPairsBin spb = null;
    static File pairs_uniq = null;

    static {

        // Load deferred color space profiles to avoid
        // ConcurrentModificationException due to JDK
        // Use in public static main void or prior to application initialization
        // https://github.com/haraldk/TwelveMonkeys/issues/402
        // https://bugs.openjdk.java.net/browse/JDK-6986863
        // https://stackoverflow.com/questions/26297491/imageio-thread-safety

        ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
        ICC_Profile.getInstance(ColorSpace.CS_PYCC).getData();
        ICC_Profile.getInstance(ColorSpace.CS_GRAY).getData();
        ICC_Profile.getInstance(ColorSpace.CS_CIEXYZ).getData();
        ICC_Profile.getInstance(ColorSpace.CS_LINEAR_RGB).getData();
    }

    // TODO - when SymPairsBin exceeds memory, replace with parallel
    //          write w/ channels, per commented starter code,
    //          remembering reader needs conversion too

    private static List<String> dummyids(int n) {
        List<String> ids = new ArrayList<>();
        for (int i=0; i<n; i++) {
            ids.add("id" + i);
        }
        return ids;
    }

    static class Result {  // list of [picA picB value] for printing
        double[] vals = null; // using SymPairsBin w/ final object write
        int nvals = 0;
        String out = null;
        Boolean vertical = null;
    }

    private static Result[] results = null;

    private static Map<FileRec, String> fileMap = new TreeMap<>();
    private static List<FileRec> fileList = new ArrayList<>();
    private static FileRec[] fileHists = null;
    private static List<String> ids = new ArrayList<>();

    private static File cache_dir = null;

    private static int readBinHistTot = 0;
    private static int readHistTot = 0;
    private static int readJpgTot= 0;

    private static int readCt = 1;

    private static Set<String> usedPairs = Collections.synchronizedSet(new HashSet<>());

    private static BufferedImage readLoudly(FileRec fr) {

        // You may need this for debug
        //if ("my_excluded_files".contains(f.getName())) {
        //    pout("Skipping: " + f);
        //    return null;
        //}

        if (fr.ct > -1) {
            if (fr.file == null) {
                err("readLoudly: File is null: readCt: " + readCt + " fr.ct>-1: " + fr.ct);
            }
            System.err.print("\rReading " +
                (readCt++) + "/" + fileHists.length +
                ": " + fr.file.getName() + "        ");
        }

        try {

            //err("read " + fr.id.id + " f " + fr.file);
            return ImageIO.read(fr.file);

        } catch (IOException ioe) {

            pout("Skipping " + fr.file + ": " + ioe);
            return null;
        }
    }
    private static abstract class HistCalc {

        public String method;
        public Scanner inp;
        abstract public Histogram_F64 calcHist(BufferedImage buffered);

        public int readBinHist = 0;
        public int readHist = 0;
        public int readJpg = 0;

        HistCalc(String method, Scanner inp) {

            this.method = method;

            if (inp != null) {

                // Boofcv: "The number of bins is an important parameter.
                // Try adjusting it"

                while (histDim <= 1  ||  histDim > 256) {

                    System.out.print("NBins per axis: [2..256]: ");

                    try {

                        histDim = Integer.parseInt(inp.next());

                    } catch (NumberFormatException nfe) {
                           pout("Oops: " + nfe);
                           continue;
                    }
                }
            }

            if (histDim < 2  ||  histDim > 256) {
                err("hist [" + method + "]: dim not in [2..256]: " +
                                            histDim);
            }
        }

        /**
         **  jpgToHistogram - read jpg and calc histogram,
         **     if histOutDir != null: save histogram in ascii,bin
         **/

        FileRec jpgToHistogram(FileRec fr, boolean normalize, boolean by_archive) {

            BufferedImage buffered = readLoudly(fr);
            if (buffered == null ) {
                err("Exiting");
            }

            Histogram_F64 h = calcHist(buffered);
            if (h == null) {
                System.err.println("Hist null " + fr.ct);
                return null;
            }
            if (normalize) {
                UtilFeature.normalizeL2(h); // normalize so that image size doesn't matter
            }
            fr.histogram = h.getData();

            readJpg++;

            if (histOutDir != null) {

                String name = fr.fname.substring(0,
                        fr.fname.lastIndexOf('.')) +
                        ".hist";

                File outDir = null;

                if (cache_dir != null) {
                    outDir = cache_dir;
                } else if (by_archive) {
                    String archDir = histOutDir.getPath() + "/" + fr.id.arch;
                    outDir = new File(archDir);
                    if (!outDir.isDirectory()) {
                        pout("-- Creating by_archive dir " + archDir);
                        try { outDir.mkdir(); } catch (Exception ignore) {}
                    }
                } else {
                    outDir = histOutDir;
                }

                // size diff:
                //      2590  grey_hist/36//_K7A6999_sm.hist
                //      1051  grey_hist/36//_K7A6999_sm.hist_bin

                String outFile = outDir + "/" + name;
                String binOutFile = outDir + "/" + name + "_bin";

                //err("Outfiles: " + outFile + " " + binOutFile);
                //System.err.printf("\rhist: " + outFile + "      ");

                try {

                    if (ASCII) {
                        PrintStream out = new PrintStream(new FileOutputStream(outFile, concat_output));
                        for (int j=0; j<fr.histogram.length; j++) {
                            out.println("" + fr.histogram[j]);
                        }
                        out.close();
                    }

                    DataOutputStream dos = 
                            new DataOutputStream(
                                // new BufferedOutputStream(
                                new FileOutputStream(binOutFile, concat_output));

                    for (int i=0; i<fr.histogram.length; i++) {
                        dos.writeFloat((float)fr.histogram[i]);
                    }
                    dos.close();

                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                fr.histogram = null;
            }

            return fr;
        }

        public void load(boolean normalize) {

            int nthreads = MiscUtil.getProcs();

            pout("=== Loading " + method +
                (histDim == -1 ? "" : "(" + histDim + ")") +
                (normalize ? "/normalize" : "") +
                (histOutDir != null ? "/writeHist" : "/nowrite") +
                " files: " + fileMap.size() + " threads: " + nthreads);

            String cmd = "read" + (normalize ? "N" : "");

            long t1 = System.currentTimeMillis();
            fileHists = new FileRec[fileMap.size()];

            // put in order, make id list

            int i = 0;
            for (Map.Entry entry : fileMap.entrySet()) {

                FileRec fr = (FileRec) entry.getKey();
                fr.ct = i;
                fileHists[i] = fr;
                i++;

                ids.add(fr.id.id);
            }

            if (nthreads == 1) {

                try {

                    pWorker worker = new pWorker(null, cmd, this); // this is histCalc

                    for (i=0; i<fileHists.length; i++) {
                        worker.loadHistOrJpg(i);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    err("ERROR: " + e);
                }

            } else {

                BlockingQueue<Integer> pQ = new
                        LinkedBlockingQueue<Integer>(nthreads);
                ExecutorService pWorkers =
                        Executors.newFixedThreadPool(nthreads);
                try {

                    for (i=0; i<nthreads; i++) {
                        pWorkers.execute(new pWorker(pQ, cmd, this)); // this is histCalc
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
                    System.err.println("Interrupt: " + ie);
                    System.exit(1);
                }

            }

            System.err.println("Loaded " +
                    (readBinHist>0 ? readBinHist + " binHists " : "") +
                    (readHist>0 ? readHist + " hists " : "") +
                    (readJpg>0 ? readJpg +  " jpgs " : "") +
                    " threads " + nthreads +
                    " in: " +
                    MiscUtil.formatInterval(System.currentTimeMillis() - t1));

            readBinHistTot += readBinHist;
            readHistTot += readHist;
            readJpgTot += readJpg;
        }
    }

    static class pWorker implements Runnable {

        private final BlockingQueue<Integer> pQ;
        private final String type;
        private final HistCalc histCalc;

        public pWorker(BlockingQueue<Integer> pQ, String type,
                            HistCalc histCalc) {
            this.pQ = pQ;
            this.type = type;
            this.histCalc = histCalc;

        }

        @Override
        public void run() {

            if (type.startsWith("read")) {
                readCt = 1;
                while(true) {
                    try {
                        int i = pQ.take();
                        if (i == -1) {
                            break;
                        }

                        loadHistOrJpg(i);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if ("dist".equals(type)) {

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
            } else if ("angle".equals(type)) {

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
                err("Internal: unk type: " + type);
            }
        }

        void loadHistOrJpg(int i) throws Exception {

            //pout("load " + i + ": " + fileHists[i].fname + " using " + histCalc.method);

            if (fileHists[i].fname.endsWith(".hist")) {

                fileHists[i].readAsciiHistogram();
                histCalc.readHist++;

            } else if (fileHists[i].fname.endsWith(".hist_bin")) {

                fileHists[i].readBinHistogram();
                histCalc.readBinHist++;

            } else {
                histCalc.jpgToHistogram(fileHists[i],
                                            type.endsWith("N"), // normalize
                                            by_archive);
            }
        }

        private static void calcDistRow(int i) throws Exception {

//if(fileHists[i].length != dummyids().size()) err("WWHAT");

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
                err("row size mismatch: row " + i +
                        " of " + (fileHists.length-1) +
                        " got " + row.length +
                        " expected " + row_size);
            }

            int jix = 0;
            for (int j=i+1; j<fileHists.length; j++) {

                FileRec fr2 = fileHists[j];

                if (fr2.histogram == null) {
                    err("p2 null: " + j);
                }

                if (fr1.vertical != fr2.vertical) {
                    err("Internal error - mixed v,h");
                    continue;
                }

                double d = (POINC ? MathUtil.poincareDist(point1, fr2.histogram) :
                                    MathUtil.cos_sim(point1, fr2.histogram));

                row[jix++] = d;

                if (ASCII) {

                    String id2 = fr2.id.id;

                    result.append(id1).append(" ").append(id2)
                          .append(" ").append(d).append('\n');
                }
            }

            synchronized(results[i]) {

                results[i].out = result.toString();
                //results[i].vals = vals;
                results[i].nvals = row.length; // for % done display
                results[i].vertical = fr1.vertical;
                results[i].notifyAll();
            }

            //err("Result len " + results[i].out.length());

            result.setLength(0); // gc not working?
        }

        private static void calcAngleRow(int i) throws Exception {

            if (spb == null) err("angleRow spb null at i " + i);

            StringBuilder result = new StringBuilder();
            FileRec f1 = fileHists[i];
            String id1 = f1.id.id;
            double[] point1 = f1.histogram;
            int[] tmp_distr = new int[190];
            for (int ii=0; ii<tmp_distr.length; ii++) {
                tmp_distr[ii] = 0;
            }

            //double[] vals = new long[fileHists.length-i];
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

                if (ASCII) {
                    result.append(id1).append(" ").append(id2)
                        .append(" ").append(a).append('\n');;
                }
            }

            synchronized(angle_distr) {

                for (int ii=0; ii<tmp_distr.length; ii++) {
                    angle_distr[ii] += tmp_distr[ii];
                }
            }

            synchronized(results[i]) {

                results[i].out = result.toString();
                //results[i].vals = vals;
                results[i].nvals = row.length; // for % done display
                results[i].vertical = f1.vertical;
                results[i].notifyAll();
            }
        }
    }


    private static int histDim = -1;


    private static Planar<GrayF32> LAB =
                                    new Planar<GrayF32>(GrayF32.class,1,1,3);


    /**
     * Lab stores color information in 'a' and 'b' while intensity is in L.  This computes a 2D histogram
     * from a, b only, which makes it lighting independent.
     * - But Lab conversion didn't give good distributions of values,
     *   so abandoned (at boofcv v0.23)
    private static class CoupledAB extends HistCalc {

        public CoupledAB(Scanner inp) {
            super("coupledAB", inp);
        }

        @Override
        public FileRec scan(FileRec fr) {

            BufferedImage buffered = readLoudly(fr.file, ct++,
                                fileMap.size());
            if( buffered == null ) {
                System.exit(1);
                        }
                        try {
                RGB.reshape(buffered.getWidth(), buffered.getHeight());
                LAB.reshape(buffered.getWidth(), buffered.getHeight());

                ConvertBufferedImage.convertFrom(buffered, RGB, true);
                ColorLab.rgbToLab(RGB, LAB);
                        } catch (IllegalArgumentException iae) {
                            pout("Probably monochrome, skipping: " + fr.file);
                            return null;
                        }

            Planar<GrayF32> ab = LAB.partialSpectrum(1,2);

            // The number of bins is an important parameter.  Try adjusting it
            Histogram_F64 histogram = new Histogram_F64(histDim, histDim);
            histogram.setRange(0, -128.0, 127.0);
            histogram.setRange(1, -128.0, 127.0);

            // Compute the histogram
            GHistogramFeatureOps.histogram(ab, histogram);

            fr.histogram = histogram.value;
            return fr;
        }
    }
     */

    /**
     * HSV stores color information in Hue and Saturation while
     * intensity is in Value.
     * This computes a 1D histogram from saturation only,
     * which makes it lighting independent.
     */

    private static class HSV_SatOnly extends HistCalc {

        public HSV_SatOnly(Scanner inp) {
            super("HSV_SatOnly", inp);
        }

        @Override
        public Histogram_F64 calcHist(BufferedImage buffered) {

            Planar<GrayF32> RGB = new Planar<GrayF32>(GrayF32.class,1,1,3);
            Planar<GrayF32> HSV = new Planar<GrayF32>(GrayF32.class,1,1,3);
            try {
                RGB.reshape(buffered.getWidth(), buffered.getHeight());
                HSV.reshape(buffered.getWidth(), buffered.getHeight());

                ConvertBufferedImage.convertFrom(buffered, RGB, true);
                ColorHsv.rgbToHsv(RGB, HSV);
            } catch (IllegalArgumentException iae) {
                pout("Probably monochrome");
                return null;
            }

            Planar<GrayF32> s = HSV.partialSpectrum(1);
            Histogram_F64 histogram = new Histogram_F64(histDim);
            //Planar<GrayF32> hs = HSV.partialSpectrum(0);
            //Histogram_F64 histogram = new Histogram_F64(histDim);

            histogram.setRange(0, 0, 1.0);         // range of saturation is from 0 to 1

            // Compute the histogram
            GHistogramFeatureOps.histogram(s, histogram);
            return histogram;
        }
    }

    /**
     * HSV stores color information in Hue and Saturation while
     * intensity is in Value.
     * This computes a 2D histogram from hue and saturation only,
     * which makes it lighting independent.
     */

    private static class CoupledHueSat extends HistCalc {
        public CoupledHueSat(Scanner inp) {
            super("coupledHueSat", inp);
        }

        @Override
        public Histogram_F64 calcHist(BufferedImage buffered) {

            Planar<GrayF32> RGB = new Planar<GrayF32>(GrayF32.class,1,1,3);
            Planar<GrayF32> HSV = new Planar<GrayF32>(GrayF32.class,1,1,3);
            try {
                RGB.reshape(buffered.getWidth(), buffered.getHeight());
                HSV.reshape(buffered.getWidth(), buffered.getHeight());

                ConvertBufferedImage.convertFrom(buffered, RGB, true);
                ColorHsv.rgbToHsv(RGB, HSV);
            } catch (IllegalArgumentException iae) {
                pout("Probably monochrome");
                return null;
            }

            Planar<GrayF32> hs = HSV.partialSpectrum(0,1);
            Histogram_F64 histogram = new Histogram_F64(histDim, histDim);
            //Planar<GrayF32> hs = HSV.partialSpectrum(0);
            //Histogram_F64 histogram = new Histogram_F64(histDim);

            histogram.setRange(0, 0, 2.0*Math.PI); // range of hue is from 0 to 2PI
            histogram.setRange(1, 0, 1.0);         // range of saturation is from 0 to 1

            // Compute the histogram
            GHistogramFeatureOps.histogram(hs,histogram);
            return histogram;
        }

    }

    private static class CoupledSV extends HistCalc {
        public CoupledSV(Scanner inp) {
            super("coupledSV", inp);
        }

        @Override
        public Histogram_F64 calcHist(BufferedImage buffered) {

            Planar<GrayF32> RGB = new Planar<GrayF32>(GrayF32.class,1,1,3);
            Planar<GrayF32> HSV = new Planar<GrayF32>(GrayF32.class,1,1,3);
            try {
                RGB.reshape(buffered.getWidth(), buffered.getHeight());
                HSV.reshape(buffered.getWidth(), buffered.getHeight());

                ConvertBufferedImage.convertFrom(buffered, RGB, true);
                ColorHsv.rgbToHsv(RGB, HSV);
            } catch (IllegalArgumentException iae) {
                pout("Probably monochrome");
                return null;
            }

            Planar<GrayF32> hs = HSV.partialSpectrum(1,2);
            Histogram_F64 histogram = new Histogram_F64(histDim, histDim);
            //Planar<GrayF32> hs = HSV.partialSpectrum(0);
            //Histogram_F64 histogram = new Histogram_F64(histDim);

            // range of saturation is from 0 to 1
            histogram.setRange(0, 0, 1.0);

            // range of 'value' is from 0 to 1
            histogram.setRange(1, 0, 1.0);

            // Compute the histogram
            GHistogramFeatureOps.histogram(hs,histogram);
            return histogram;
        }

    }

    /**
     * Computes two independent 1D histograms from hue and saturation.  Less affects by sparsity, but can produce
     * worse results since the basic assumption that hue and saturation are decoupled is most of the time false.
    TODO - bring into Bill's mode
    public static List<double[]> independentHueSat( ) {
        List<double[]> points = new ArrayList<double[]>();

        // The number of bins is an important parameter.  Try adjusting it
        TupleDesc_F64 histogramHue = new TupleDesc_F64(30);
        TupleDesc_F64 histogramValue = new TupleDesc_F64(30);

        List<TupleDesc_F64> histogramList = new ArrayList<TupleDesc_F64>();
        histogramList.add(histogramHue); histogramList.add(histogramValue);

        int ct = 1;
        for( File f : images ) {
            BufferedImage buffered = readLoudly(f, ct++, images.size());
            if (buffered == null) {
                continue;
            }

            RGB.reshape(buffered.getWidth(), buffered.getHeight());
            HSV.reshape(buffered.getWidth(), buffered.getHeight());
            ConvertBufferedImage.convertFrom(buffered, RGB, true);
            ColorHsv.rgbToHsv(RGB, HSV);

            GHistogramFeatureOps.histogram(HSV.getBand(0), 0, 2*Math.PI,histogramHue);
            GHistogramFeatureOps.histogram(HSV.getBand(1), 0, 1, histogramValue);

            // need to combine them into a single descriptor for processing later on
            TupleDesc_F64 imageHist = UtilFeature.combine(histogramList,null);

            points.add(imageHist.value);
        }

        return points;
    }
     */

    /**
     * Constructs a 3D histogram using RGB.  RGB is a popular color space, but the resulting histogram will
     * depend on lighting conditions and might not produce the accurate results.
     */
    private static class CoupledRGB extends HistCalc {
        public CoupledRGB(Scanner inp) {
            super("coupledRGB", inp);
        }

        @Override
        public Histogram_F64 calcHist(BufferedImage buffered) {

            Planar<GrayF32> RGB = new Planar<GrayF32>(GrayF32.class,1,1,3);
            RGB.reshape(buffered.getWidth(), buffered.getHeight());
            ConvertBufferedImage.convertFrom(buffered, RGB, true);

            Histogram_F64 histogram = new Histogram_F64(histDim,histDim,histDim);
            histogram.setRange(0, 0, 255);
            histogram.setRange(1, 0, 255);
            histogram.setRange(2, 0, 255);

            GHistogramFeatureOps.histogram(RGB,histogram);

            return histogram;
        }
    }


    /**
     * Computes a histogram from the gray scale intensity image alone.  Probably the least effective at looking up
     * similar images.
     */
    private static class Greyscale extends HistCalc {
        public Greyscale(Scanner inp) {
            super("greyscale", inp);
        }

        @Override
        public Histogram_F64 calcHist(BufferedImage buffered) {

            GrayU8 gray = new GrayU8(1,1);

            gray.reshape(buffered.getWidth(), buffered.getHeight());
            ConvertBufferedImage.convertFrom(buffered, gray, true);

            //TupleDesc_F64 imageHist = new TupleDesc_F64(128);
            Histogram_F64 imageHist = new Histogram_F64(histDim);
            HistogramFeatureOps.histogram(gray, 255, imageHist);

            return imageHist;
        }
    }

    private static void usage(String msg) {

        if (msg != null) {
            System.err.println(msg);
        }

        System.err.println("Interactive search usage: <run> <dir_w_jpgs> [-cache <histogram_cache>] find");
        System.err.println("usage: <run> <dir_w_jpgs>  \\");
        System.err.println("           [-cache <histogram_cache_dir>]   \\"); // TODO/almost
        System.err.println("           [-h|-v] [-f list_file]  \\");
        System.err.println("           [-by_archive] [-concat]  \\");
        System.err.println("           [-pairtop <xxx_dump.sql>]  \\");
        System.err.println("       <cmd1> [<cmd2..>]");
        System.err.println("  -by_archive:  put files in subdir numbered by archive");
        System.err.println("  -concat:      per-pic, concatenate histo types (used with -cache)"); // ?
        System.err.println("  <cmd>:  <fname>-<type>-<meas>");
        System.err.println("  cmd section delim is '-'");
        System.err.println("  <fname> in [jpg|bin]");
        System.err.println("  <type> (delim '_') in [gs|hs|rgb]_<num_bins>");
        System.err.println("  <meas> in [hist|dist|angle]");
        System.err.println("NB: options must be used in order used here.");
        System.err.println("NB: -cache and -concat may need work]");
        System.exit(1);
    }

    // Gurdjieff's advice to his daughter?
    // "Always finish what you start. ... Don't cling to things that will eventually destroy you."

    private static String getSuffix(final String path) {

        if (path == null) {
            return null;
        }

        String result = "";

        if (path.lastIndexOf('.') != -1) {
            result = path.substring(path.lastIndexOf('.'));
            if (result.startsWith(".")) {
                result = result.substring(1);
            }
        }

        return result;
    }

    private static File getHisto(String dir, String fname) {

        String tmp = fname.replace(".jpg", ".hist_bin");
        File f = new File(dir + tmp);

        if (!f.exists()) {
            tmp = fname.replace(".jpg", ".hist");
            f = new File(dir + tmp);
            if (!f.exists()) {
                return null;
            }
        }
        return f;
    }

    private static void getFilesFromFile(File dir, File cache,
                        File listFile,
                        String orient) {

        // listFile is real_img_orient: fname_from_dir <v|h>

        if (!listFile.isFile()) {
            usage("List file not file " + listFile);
        }

        String imgDir = dir.getAbsolutePath() + "/";
        String cacheDir = null;
        if (cache != null) {
            cacheDir = cache.getAbsolutePath() + "/";
        }

        pout("\n-- getting files in imgDir:cache " +
                    imgDir + " " + cacheDir +
                    " from list " + listFile);

        int linect = 0;

        try {

            BufferedReader in = new BufferedReader(
                        new FileReader(listFile));
            String line;
            while ((line = in.readLine()) != null) {

                linect++;

                line = line.trim();

                if (line.length() == 0  ||  line.startsWith("#")) {
                    continue;
                }

                String ss[] = line.split("\\s+");
                if (ss.length != 2) {
                    err("List file fields not=2: expected |fname <t|f>|: \n[" +
                                                    line + "]");
                }
                String fname = ss[0];
                String or = ss[1];
                boolean vertical = "t".equals(or);

                if (orient != null  &&  vertical != "v".equals(orient)) {
                    continue;
                }

                // see if the original jpg exists

                File jpg = new File(imgDir + fname);

                if (!jpg.exists()) {
                    pout("Warning: no jpg, checking for cached histogram: " + imgDir + fname);
                    jpg = null;
                }

                // see what fastest type exists

                File f = getHisto(cacheDir, fname);

                if (f == null) {

                    // this might be where histos were placed recursively
                    //  in same dir as their pics

                    f = getHisto(imgDir, fname);

                }

                if (f == null) {

                    // no cache or no histo in cache or no histo in imgDir

                    f = jpg;
                }

                if (f == null) {
                    err("No jpg or leftover histogram: " + imgDir + fname);
                }

                if (f != jpg  &&  jpg != null  &&
                        jpg.lastModified() < f.lastModified()) {

                    pout("Jpg is newer than cached histogram: " +
                            new Date(jpg.lastModified()) +
                            " < " +
                            new Date(f.lastModified()) +
                            " => redoing histo: " + jpg);

                    f = jpg;
                }

                //pout("frec " + base + " .. " + fname);
                FileRec fr = new FileRec(f, true);

                fr.vertical = vertical;

/*
                if (fileMap.containsKey(fr)) {
                    pout("Keeping dupe seq: " + line);
                    //continue;
                }
*/

                fileMap.put(fr, fr.id.id);
                //System.err.print("\rChecked " + line + "          ");
            }

            System.err.println();
            in.close();

        } catch (Exception e) {
            System.err.println("Reading " + listFile + " line " + linect +
                            ": " + e);
            e.printStackTrace();
            System.exit(1);
        }

        pout("-- Got " + fileMap.size() +
                " .jpg or hist from list " + listFile +
                " img dir " + imgDir +
                " cache " + cacheDir);

    }


    private static void buildJpgListRecursively(File root, String orient) {

        if (root == null) {
            err("Null root dir in buildJpgListRecursively");
            //return;
        }

        File[] list = root.listFiles();

        if (list == null) {
            err("Empty root dir: " + root);
        }

        String dirname = root.getName();

        for ( File f : list ) {
            if ( f.isDirectory() ) {
                buildJpgListRecursively(f, orient);
                // pout( "Dir:" + f.getAbsoluteFile() );
            } else {
                String fname = f.getName();
                if (!fname.toLowerCase().endsWith(".jpg")) {
                    continue;
                }
                // pout( "File:" + f.getAbsoluteFile() );
                try {

                    FileRec fr = new FileRec(f.getAbsolutePath(), false);

                    if (orient != null) {
                        BufferedImage image = UtilImageIO.loadImage(f.getPath());
                        int h = image.getHeight();
                        int w = image.getWidth();
                        if ("v".equals(orient)) {
                            if (h < w) continue;
                            fr.vertical = true;
                        } else {
                            // "h"
                            if (w < h) continue;
                            fr.vertical = false;
                        }
                    }

                    int i = 0; //fname.indexOf("/");
                    String s = fname.substring(0, i);

                    String id = dirname + ":" +
                                MiscUtil.parseSeq(fname.substring(i));
                    fileMap.put(fr, id);
                } catch (Exception e) {
                    e.printStackTrace();
                    err("Wow");
                }
            }
        }
        System.err.println();
        //pout("Recursively got " + fileMap.size() + " .jpg");
    }

    private static double histDist0(double[] h1, double[] h2) {
        double d2T = 0.0;
        for (int i=0; i<h1.length; i++) {
            double d = h1[i] - h2[i];
            d2T += d * d;
        }
        //return Math.sqrt(d2T);
        return d2T;
    }

    private static double histDist1(double[] h1, double[] h2) {
        // Hellinger distance from javatips.net/libsim
        // modified to half Hd squared

        int n = h1.length;
        double mean1 = 0.0, mean2 = 0.0;
        for (int i=0; i<n; i++) {
            mean1 += h1[i];
            mean2 += h2[i];
        }
        mean1 /= n;
        mean2 /= n;
        double sum = 0.0;
        for (int i=0; i<n; i++) {
            sum += Math.pow(
                Math.sqrt(h1[i]/mean1)-Math.sqrt(h2[i]/mean2),2);
        }
        //return Math.sqrt(2*sum);
        return sum;
    }

    private static void writeOutHistDists(String outFile,
                        String orient, String tag, HistCalc histCalc) {

        int threads = MiscUtil.getProcs();

        System.err.println("=== Calcing histogram distances " +
                    orient + "_" + outFile +
                    " tag " + tag +
                    " threads " + threads);

        long t1 = System.currentTimeMillis();

        if (spb == null) {
            spb = new SymPairsBin(ids);
        }

        int lines = 0;

        try {

            // results is what we wants (also spb, below)

            results = new Result[fileHists.length];
            for (int i=0; i<results.length; i++) {
                results[i] = new Result();
            }

            // output

            PrintStream outAscii = dbout;
            if (outAscii == null) {
                outAscii = new PrintStream(
                    new File(orient + "_" + outFile));
            }

            double dtot = 0.5 * ((double)fileHists.length * (fileHists.length-1));

            double ct = 0.0;

            long t0 = System.currentTimeMillis();

            if (threads == 1) {

                // unparallel worker

                pWorker worker = new pWorker(null, "dist", null);

                for (int i=0; i<fileHists.length-1; i++) {

                    worker.calcDistRow(i);

                    if (ASCII) {
                        outAscii.print(results[i].out);
                    }
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

                BlockingQueue<Integer> pQ = new
                        LinkedBlockingQueue<Integer>(threads);
                ExecutorService pWorkers = Executors.newFixedThreadPool(threads);
                for (int i=0; i<threads; i++) {
                    pWorkers.execute(new pWorker(pQ, "dist", null));
                }
                int nextI = 0;
                for (int i=0; i<threads; i++) {
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

                    if (ASCII) {
                        outAscii.print(results[i].out);
                    }

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
                for (int i=0; i<threads; i++) {
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

            long dt = ((System.currentTimeMillis()-t0)/1000);

            pout("Calced ~n^2/2 " +
                    histCalc.method + "." + histDim +
                    " distances in " +
                    dt + " sec using " +
                    threads + " threads " +
                    "(" + String.format("%.1E", dtot / dt) + " distances/sec)");

            // wrap up output

            lines = spb.writeTopPairs(outAscii, MiscUtil.getProcs(),
                            ids, tag, 50, 50);

            tagsCount.add(tag, lines);

            if (dbout == null) {
                outAscii.close();
            }

        } catch (IOException ioe) {
            System.err.println("Writing output: " + outFile);
            ioe.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("E: " + outFile);
            e.printStackTrace();
            System.exit(1);
        }
        System.err.println("                     Calc+writing " + lines + " lines to file: " +
            MiscUtil.formatInterval(System.currentTimeMillis() - t1));
    }

    private static int[] angle_distr = null;
    private static double[] vlen = null;

    private static void writeOutAngDists(String outFile,
                        String orient, String tag, HistCalc histCalc)
            throws Exception {

        int threads = MiscUtil.getProcs();

        System.err.println("=== Calcing distances+angles+distrib to [" +
                    orient + "_" + outFile +
                    "*], threads " + threads);

        long t1 = System.currentTimeMillis();

        if (spb == null) {
            spb = new SymPairsBin(ids);
        }

        int lines = 0;

        try {

            // avg point treated as center of space

            int dim = fileHists[0].histogram.length;
//pout("0th histo " + Arrays.toString(fileHists[0].histogram));
//if (dim>0)System.exit(0);
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
            //pout("Got avg point");

            vlen = new double[fileHists.length];
            PrintStream out_len = new PrintStream(orient + "_" +
                            outFile + "_len");
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
                out_len.println(id1 + " " +
                        Math.round(vlen[i]));
            }
            out_len.close();

            // results is what we wants

            results = new Result[fileHists.length];
            for (int i=0; i<results.length; i++) {
                results[i] = new Result();
            }

            // angles too, apparently

            angle_distr = new int[190];
            for (int i=0; i<angle_distr.length; i++) {
                angle_distr[i] = 0;
            }

            // dang it's slow, let's multithread. start 'em up

            BlockingQueue<Integer> pQ = new
                    LinkedBlockingQueue<Integer>(threads);
            ExecutorService pWorkers = Executors.newFixedThreadPool(threads);
            for (int i=0; i<threads; i++) {
                pWorkers.execute(new pWorker(pQ, "angle", null));
            }

            // give the threads a round of work

            int nextI = 0;
            for (int i=0; i<threads; i++) {
                pQ.put(nextI); nextI++;
            }

            PrintStream out_pair = dbout;
            if (out_pair == null) {
                out_pair = new PrintStream(
                    orient + "_" + outFile + "_angle");
            }

            double ct = 0.0;
            double dtot = fileHists.length * (fileHists.length-1) / 2;

            long t0 = System.currentTimeMillis();

            for (int i=0; i<fileHists.length-1; i++) {

                synchronized(results[i]) {

                    if (results[i].out == null) {
                        results[i].wait();
                    }
                }

                if (nextI < fileHists.length-1) {
                    pQ.put(nextI); nextI++;
                }

                if (ASCII) {
                    out_pair.print(results[i].out);
                }

                ct += (double) results[i].nvals;

                // free memory

                results[i].out = null;
                results[i].vals = null;
                results[i].vertical = null;
                results[i] = null;
                fileHists[i].histogram = null;

                if (i%100 == 0) {
                    System.err.printf(
                        "\rangles: %3.2f%%       ",
                        (100.0*(ct / dtot)));
                }
            }
            for (int i=0; i<threads; i++) {
                pQ.put(-1);
            }
            pWorkers.shutdown();
            try {
                pWorkers.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                System.err.println("Interrupt: " + ie);
                //System.exit(1);
            }

            pout("-- Calced angles in " + ((System.currentTimeMillis()-t0)/1000) + " sec");
            lines = spb.writeTopPairs(out_pair, MiscUtil.getProcs(),
                            ids, tag, 50, 50);

            tagsCount.add(tag, lines);

            if (dbout == null) {
                out_pair.close();
            }

            PrintStream out_dist = new PrintStream(orient + "_" +
                            outFile + "_dist");
            for (int i=0; i<angle_distr.length; i++) {
                if (angle_distr[i] > 0) {
                    out_dist.println("" + i + " " +
                            angle_distr[i]);
                }
            }
            out_dist.close();

        } catch (IOException ioe) {
            System.err.println("Writing output: " + outFile);
            ioe.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("E: " + outFile);
            e.printStackTrace();
            System.exit(1);
        }
        System.err.println("                       Calc+writing files: " +
            MiscUtil.formatInterval(System.currentTimeMillis() - t1));
    }

    private static void test(String[] args) {
        Scanner inp = new Scanner(System.in);
        inp.useDelimiter("\n");
        try {
            while (true) {
                System.out.print("RGB: ");
                String s = inp.next();
                String ss[] = s.split(" ");
                int r = Integer.parseInt(ss[0]);
                int g = Integer.parseInt(ss[1]);
                int b = Integer.parseInt(ss[2]);
                pout("rgb.1 " + r + " " + g + " " + b);
                double[] xyz = new double[3];
                ColorXyz.rgbToXyz(r, g, b, xyz);
                pout("xyz   " + xyz[0] + " " + xyz[1] + " " + xyz[2]);
                double[] lab = new double[3];
                ColorLab.rgbToLab(r, g, b, lab);
                pout("lab   " + lab[0] + " " + lab[1] + " " + lab[2]);

                double[] hsv = new double[3];
                ColorHsv.rgbToHsv(r, g, b, hsv);
                pout("hsv   " + hsv[0] + " " + hsv[1] + " " + hsv[2]);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String outType = null;

    private static File histOutDir = null;

    private static void createOutFiles() {

        pout("Creating " + createFiles.size() + " files");

        List<File> existing = new ArrayList<>();

        try {

            for (String fname : createFiles) {

                File f = new File(fname);

                if (!f.createNewFile()) {
                    existing.add(f);
                }
            }

            if (existing.size() > 0) {

                pout("\toutFile(s) already exist, removing in 5 sec:\n\t" +
                        Arrays.toString(existing.toArray()));

                try { Thread.sleep(5000); } catch (Exception ignore) {}
                pout("");

                for (File f : existing) {

                    f.delete();

                    if (!f.createNewFile()) {
                        err("Can't delete/create: " + f);
                    }
                }
            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
            err("Creating outFiles: " + ioe);
        }
    }

    private static void handleCmd(String cmd, String orient, boolean run) {

        cmd = cmd.trim();

        if (run) {
            System.err.println("\n\n====== cmd: " + cmd);
        } else {
            System.err.println("====== pre-check: " + cmd);
        }

        String ss[] = cmd.split("-");
        if (ss.length != 3) {
            usage("Expected <cmd>: 'fname-type-meas', got " + cmd);
        }

        String outFile = ss[0];
        String histType = ss[1];
        String measType = ss[2];

        String tt[] = outFile.split("/");
        String end = tt[tt.length-1];
        tt = end.split("_");
        if (tt.length < 2) {
            err("getting tag from outFile: " + outFile);
        }
        String tag = tt[0];

        if ("hist".equals(measType)) {

            if (outFile.startsWith("~")) {
                outFile = outFile.replace("~",
                                        System.getProperty("user.home"));
            }

            histOutDir = new File(outFile);
            if (!histOutDir.isDirectory()) {
                pout("Not a dir (histOutDir): " + histOutDir + " creating");
                histOutDir.mkdir();
            }

        } else if ("dist".equals(measType)) {

            // histDist

            if (run  &&  !"/dev/null".equals(outFile)  &&  dbdump == null) {
                createFiles.add(orient + "_" + outFile);
            }

        } else if ("angle".equals(measType)) {

            // length file + pairs d+angle file

            if (!run) {
                if (dbdump == null) {
                    createFiles.add(orient + "_" + outFile + "_angle");
                }
                createFiles.add(orient + "_" + outFile + "_len");
                createFiles.add(orient + "_" + outFile + "_dist");
            }

        } else {
            usage("Unknown measurement type: " + measType);
        }

        HistCalc histCalc = null;

        String ht[] = histType.split("_");

        /*
        if ("ml".equals(ht[0])) {

            if (ht.length != 1) {
                usage("histType/ml: Expected just ml");
            }
            histCalc = new MLHists(null);

        } else */

        if ("gs".equals(ht[0])) {

            // greyscale

            if (ht.length != 2) {
                usage("histType: Expected type xx_nn");
            }
            histDim = Integer.parseInt(ht[1]);
            histCalc = new Greyscale(null);

        } else if ("s".equals(ht[0])) {

            // saturation from HSV

            if (ht.length != 2) {
                usage("histType: Expected type xx_nn");
            }
            histDim = Integer.parseInt(ht[1]);
            histCalc = new HSV_SatOnly(null);

        } else if ("hs".equals(ht[0])) {

            // hue x saturation

            if (ht.length != 2) {
                usage("histType: Expected type xx_nn");
            }
            histDim = Integer.parseInt(ht[1]);
            histCalc = new CoupledHueSat(null);

        } else if ("sv".equals(ht[0])) {

            // saturation x greyscale?luminance?

            if (ht.length != 2) {
                usage("histType: Expected type xx_nn");
            }
            histDim = Integer.parseInt(ht[1]);
            histCalc = new CoupledSV(null);

        } else if ("rgb".equals(ht[0])) {

            // plain old rgb is more-intuitive than expected

            if (ht.length != 2) {
                usage("histType: Expected type xx_nn");
            }
            histDim = Integer.parseInt(ht[1]);
            histCalc = new CoupledRGB(null);

        } else {
            usage("Unexpected type: " + ht[0]);
        }
/*
        else if ("ab".equals(ht[0])) {
            if (ht.length != 2) {
                usage("Expected type xx_nn");
            }
            histDim = Integer.parseInt(ht[1]);
            histCalc = new CoupledAB(null);
        }
*/
        //pout("METHOD: " + histCalc.method + " (" + histDim + ")");

        if (!run) {
            return;
        }

        histCalc.load("hist".equals(measType)  ||
                      "dist".equals(measType));

        if ("hist".equals(measType)) {
            return;
        }

        try {

            if ("dist".equals(measType)) {

                writeOutHistDists(outFile, orient, tag, histCalc);

            } else {

                writeOutAngDists(outFile, orient, tag, histCalc);

            }

        } catch (Exception e) {
            e.printStackTrace();
            err("Fatal: " + e);
        }
    }

    public static void main(String[] args) {

        //test(args);
        if (args.length < 2) {
            usage("arg len");
        }

        int arg_ix = 0;

        String picDir = args[arg_ix++];

        if (picDir.startsWith("~")) {
            picDir = picDir.replace("~", System.getProperty("user.home"));
            pout("-- Adjusted picDir: " + picDir);
        }
        File pic_dir = new File(picDir);
        if (!pic_dir.isDirectory()) {
            usage("picDir not a dir: " + picDir);
        }

        String cacheDir = null;

        if ("-cache".equals(args[arg_ix])) {
            arg_ix++;
            if (args.length == arg_ix) {
                usage("Expected <cacheDir>");
            }
            cacheDir = args[arg_ix];
            if (cacheDir.startsWith("~")) {
                cacheDir = cacheDir.replace("~", System.getProperty("user.home"));
            }
            cache_dir = new File(cacheDir);
            if (!cache_dir.isDirectory()) {
                usage("cacheDir not a dir: " + cacheDir);
            }

            arg_ix++;
        }

        if (!"find".equals(args[arg_ix])) {

            // non-interactive / batch mode

            String orient = null;

            String arg = args[arg_ix];

            if ("-v".equals(arg)  ||  "-h".equals(arg)) {
                orient = arg.substring(1);
                arg_ix++;
                if (arg_ix == args.length) {
                    usage("-<v|h>: expected more");
                }
                arg = args[arg_ix];
            }

            // use list or recurse

            if ("-f".equals(arg)) {

                // get files from list

                arg_ix++;
                if (arg_ix == args.length) {
                    usage("-f: expected <listfile>");
                }
                String listfile = args[arg_ix];
                if (listfile.startsWith("~")) {
                    listfile = listfile.replace("~",
                                        System.getProperty("user.home"));
                }
                getFilesFromFile(pic_dir, cache_dir, new File(listfile), orient);

                arg_ix++;
                if (arg_ix == args.length) {
                    usage("Expected more arg(s)");
                }


            } else {

                // recurse for files, recurse

                pout("Searching .jpg under [" + pic_dir + "] recursively");

                buildJpgListRecursively(pic_dir, orient);

                pout("Recursively got " + fileMap.size() + " .jpg");

            }

            if ("-by_archive".equals(args[arg_ix])) {
                by_archive = true;
                arg_ix++;
                if (arg_ix == args.length) {
                    usage("expected more arg(s)");
                }
            }

            if ("-concat".equals(args[arg_ix])) {
                concat_output = true;
                arg_ix++;
                if (arg_ix == args.length) {
                    usage("expected more arg(s)");
                }
            }

            if ("-pairtop".equals(args[arg_ix])) {

                // phobrain-specific: 'pr.pairtop_col' (==color)
                //      postgres table dump.sql, with fixed table name
                //      and holding top-50 pair distances per pic

                if (orient == null) {
                    usage("phobrain-specific -o needs -v or -h");
                }

                arg_ix++;
                if (arg_ix == args.length) {
                    usage("-pairtop: expected fname: <pairtop_dump.sql>");
                }
                String outfile = args[arg_ix];
                arg_ix++;
                if (outfile.startsWith("~")) {
                    outfile = outfile.replace("~",
                                        System.getProperty("user.home"));
                }
                createFiles.add(outfile);
                dbdump = new File(outfile);

                pairs_uniq = new File("pairs_uniq_" + orient);

                if (!pairs_uniq.canRead()) {
                    err("Expected " + pairs_uniq +
                            " file from proj/pairs/run." + orient);
                }
            }

            pout("\n-- Pre-check " + (args.length-arg_ix) + " <cmd>s" +
                        (dbdump != null ? " for pairtop " :
                            (concat_output ? " (for CONCATENATING output)" :
                                " (for overwriting if any)")) + "\n");

            for (int i=arg_ix; i<args.length; i++) {
                handleCmd(args[i], orient, false);
            }

            createOutFiles(); // chance to bail

            if (dbdump != null) {

                try {

                    pout("-- Creating " + dbdump);

                    dbout = new PrintStream(
                            new BufferedOutputStream(
                                new FileOutputStream(
                                        dbdump, false))); // no-append, all-in-one

                    db_tbl_name = "pr.pairtop_col_" + orient;

                    DumpGen.pairtopHeader(dbout, db_tbl_name);

                    pout("Copying in hardwired " + pairs_uniq);

                    int lines = 0;

                    BufferedReader in = new BufferedReader(new FileReader(pairs_uniq));
                    while (true) {

                        String s = in.readLine();
                        if (s == null) {
                            break;
                        }
                        if (s.startsWith("#")) {
                            pout("[comment: " + s + "]");
                            continue;
                        }

                        String[] ss = s.split("\t");
                        if (ss.length != 4) {
                            err("Expected 4 fields: " + s);
                        }

                        tagsCount.add(ss[2]);

                        dbout.println(s);
                        lines++;
                    }
                    in.close();

                    pout("Copied " + lines +
                            " lines with " + tagsCount.size() +
                            " tags from " + pairs_uniq);

                } catch (Exception e) {
                    e.printStackTrace();
                    err("Opening: " + dbdump + ": " + e);
                }

            }

            pout("\n-- Executing " + (args.length-arg_ix) +
                    " <cmd>s" +
                    (concat_output ? " CONCATENATING output" : " overwriting if any") + "\n");

            try {

                for (int i=arg_ix; i<args.length; i++) {
                    handleCmd(args[i], orient, true);
                }

                if (dbout != null) {
                    DumpGen.finishPairtopDump(dbout, db_tbl_name, tagsCount, null, null);
                    dbout.close();
                }

            } catch (Exception e) {
                e.printStackTrace();
                err("And Now This: " + e);
            }

            System.err.println("Loaded total " +
                    (readBinHistTot>0 ? readBinHistTot + " binHists " : "") +
                    (readHistTot>0 ? readHistTot + " hists " : "") +
                    (readJpgTot>0 ? readJpgTot +  " jpgs " : ""));

            return;
        }

        // interactive mode is what the original Example did,
        //      nowadays imagenet vectors would likely be
        //      better for the original purpose.

        buildJpgListRecursively(pic_dir, null);

        Scanner inp = new Scanner(System.in);

        // Different color spaces you can try

        HistCalc histCalc = null;

        while (histCalc == null) {
            pout("Choose a method:\n");
            pout("\t0: coupledAB");
            pout("\t1: coupledHueSat");
            pout("\t2: independentHueSat");
            pout("\t3: coupledRGB");
            pout("\t4: histogramGray");
            System.out.print("\nThis is so awkward: ");

            String s = inp.next();

            if ("0".equals(s)) {
                System.err.println("Not impl");
                //histCalc = new CoupledAB(inp);
            } else if ("1".equals(s)) {
                histCalc = new CoupledHueSat(inp);
            } else if ("2".equals(s)) {
                System.err.println("Not impl");
            } else if ("3".equals(s)) {
                histCalc = new CoupledRGB(inp);
            } else if ("4".equals(s)) {
                histCalc = new Greyscale(inp);
            } else {
                pout("Scientists have yet to figure that one out: please explain your choice.");
            }
        }
        histCalc.load(true); // normalize

        List<double[]> points = new ArrayList<>();
        List<File> images = new ArrayList<>();
        for (FileRec fr2 : fileHists) {
            points.add(fr2.histogram);
            images.add(fr2.file);
        }

        ListDisplayPanel gui = new ListDisplayPanel();
        final String defaultF = "/Users/priot/Desktop/tmp1.jpg";
        while (true) {
            pout("Choose a file to compare [0=" + defaultF + "]: ");
            String s = inp.next();
            if ("0".equals(s)) {
                s = defaultF;
            }
            if (!(new File(s)).isFile()) {
                pout("Not a file, skip: " + s);
                continue;
            }

            FileRec fr = null;
            try {
                fr = new FileRec(s, false);
                fr = histCalc.jpgToHistogram(fr, true, false);
            } catch (InvalidNameException ine) {
                pout("Bad name, skipping: " + ine);
                continue;
            }
            if (fr == null) {
                pout("Skip it");
                continue;
            }
            pout("read ok");
            double[] targetPoint = fr.histogram;

            pout("Matching by " + histCalc.method +
                    " dimension " + targetPoint.length);
            gui.reset();

            // Use a generic NN search algorithm.
            // This uses Euclidean distance as a distance metric.

            NearestNeighbor<double[]> nn =
                    FactoryNearestNeighbor.exhaustive(
                        new
                        KdTreeEuclideanSq_F64(targetPoint.length));
            NearestNeighbor.Search<double[]> search = nn.createSearch();
            DogArray<NnData<double[]>> results = new DogArray(NnData::new);

            nn.setPoints(points, true);
            search.findNearest(targetPoint, -1, 40, results);

            // Add the target which the other images are being matched against
            gui.addImage(UtilImageIO.loadImage(s), "Target", ScaleOptions.ALL);

            // The results will be the 10 best matches, but their order can be arbitrary.  For display purposes
            // it's better to do it from best fit to worst fit
            Collections.sort(results.toList(), new Comparator<NnData>() {
                @Override
                public int compare(NnData o1, NnData o2) {
                    if( o1.distance < o2.distance)
                        return -1;
                    else if( o1.distance > o2.distance )
                        return 1;
                    else
                        return 0;
                }
            });

            // Add images to GUI
            for (int i = 0; i < results.size; i++) {

                int ix = results.get(i).index;
                double error = results.get(i).distance;

                File file = images.get(ix);
                if (file == null) {
                    System.err.println("Null result for index " + ix +
                                                            " at " + i);
                    continue;
                }
                BufferedImage image = UtilImageIO.loadImage(file.getPath());
                gui.addImage(image,
                        String.format("Error %6.3f %s",
                                        error, file.getPath()),
                        ScaleOptions.ALL);
            }

            ShowImages.showWindow(gui,"Similar Images",true);
        }
    }
}
