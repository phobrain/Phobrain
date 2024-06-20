package org.phobrain.mlhistogram;

/*
 *  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 *
 *  SPDX-License-Identifier: CC-BY-SA-4.0
 */

// === MLColorHistogramTool.java - write concatenated,
//          Phobrain-selected histograms for ML 
//          in files next to .jpgs.
//
//          Based on boofcv's ExampleColorHistogramLookup.java,
//          by Peter Abeles.

import org.phobrain.util.Stdio;
import org.phobrain.util.MathUtil;
import org.phobrain.util.MiscUtil;
import org.phobrain.util.FileRec;

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
import java.io.RandomAccessFile;
import java.io.BufferedReader;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.Comparator;
import java.util.Scanner;

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

public class MLColorHistogramTool extends Stdio {

    // cmdline

    // finals

    final static int NIO_BUF_SIZE = 4096;

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

    private static List<FileRec> fileList = new ArrayList<>();

    private static int readJpgTot= 0;

    // Hmx_g128_s128_r12_1984

    /**
     **  jpgToHistogram - read jpg and calc/write histogram
     **/

     static void jpgToHistogram(FileRec fr) {
 
        String outFileName = fr.file.getAbsolutePath().replace(".jpg", ".hist");
        File outFile = new File(outFileName);
        if (outFile.exists()) {
            pout("-- done already: " + outFileName);
            return;
        }

        BufferedImage buffered = readLoudly(fr);
        if (buffered == null ) {
            System.exit(1);
        }

//pout("read ok: " + fr.fname);

        boolean normalize = true;

        // GREYSCALE 128

        Histogram_F64 h = calc_Greyscale_Hist(buffered);
        if (h == null) {
            err("Grey Hist null " + fr.ct);
        }
        if (normalize) {
            UtilFeature.normalizeL2(h); // normalize so that image size doesn't matter
        }
        double[] greyHist = h.getData();

        // SAT 128

        h = calc_HSV_SatOnly(buffered);
        if (h == null) {
            err("HSV/Sat Hist null " + fr.file.getAbsolutePath());
        }
        if (normalize) {
            UtilFeature.normalizeL2(h); // normalize so that image size doesn't matter
        }
        double[] satHist = h.getData();

        // RGB 12x12x12

        h = calc_RGB_Hist(buffered);
        if (h == null) {
            err("HSV/Sat Hist null " + fr.ct);
        }
        if (normalize) {
            UtilFeature.normalizeL2(h); // normalize so that image size doesn't matter
        }

        double[] rgbHist = h.getData();

        try {

            PrintStream out = new PrintStream(new FileOutputStream(outFile, false));
            for (double d : greyHist) {
                out.println("" + d);
            }
            for (double d : satHist) {
                out.println("" + d);
            }
            for (double d : rgbHist) {
                out.println("" + d);
            }
            out.close();

/*
            ObjectOutputStream oos = new ObjectOutputStream(
                                                new FileOutputStream(binOutFile, concat_output));
            oos.writeObject(fr.histogram);
            oos.close();
*/
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
//pout("wrote ok: " + outFileName);
    }


    static void doit() {

        int nThreads = MiscUtil.getProcs();
        if (nThreads > fileList.size()) {
            nThreads = 1;
        }

        pout("Processing files: " + fileList.size() + " threads: " + nThreads);

        long t1 = System.currentTimeMillis();

        BlockingQueue<Integer> pQ = new 
                    LinkedBlockingQueue<Integer>(nThreads);
        ExecutorService pWorkers = 
                     Executors.newFixedThreadPool(nThreads);
        try {
            for (int i=0; i<nThreads; i++) {
                pWorkers.execute(new pWorker(pQ));
            }
            for (int i=0; i<fileList.size(); i++) {
                pQ.put(i);
            }
            for (int i=0; i<nThreads; i++) {
                pQ.put(-1);
            }
            
            pWorkers.shutdown();
            pWorkers.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException ie) {
            System.err.println("Interrupt: " + ie);
            System.exit(1);
        }
        System.err.println("Done in " + 
                    formatInterval(System.currentTimeMillis() - t1));

    }

    static class pWorker implements Runnable {

        private final BlockingQueue<Integer> pQ;

        public pWorker(BlockingQueue<Integer> pQ) {
            this.pQ = pQ;
        }

        @Override
        public void run() {

            //readCt = 1;
            while(true) {
                try {
                    int i = pQ.take();
                    if (i == -1) {
                        break;
                    }
                    jpgToHistogram(fileList.get(i));

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private static int readCt = 1;

    private static BufferedImage readLoudly(FileRec fr) {

        // You may need this for debug
        //if ("my_excluded_files".contains(f.getName())) {
        //    pout("Skipping: " + f);
        //    return null;
        //}

        if (fr.ct > -1) {
            System.err.print("\rReading " + 
                (readCt++) + "/" + fileList.size() + 
                ": " + fr.file.getName() + "        ");
        }

        try {
            return ImageIO.read(fr.file);
        } catch (IOException ioe) {
            pout("Skipping " + fr.file + ": " + ioe);
            return null;
        }
    }

    private static int histDim = -1;


    private static Planar<GrayF32> LAB = 
                                    new Planar<GrayF32>(GrayF32.class,1,1,3);
    
    /**
     * HSV stores color information in Hue and Saturation while 
     * intensity is in Value.  
     * This computes a 1D histogram from saturation only, 
     * which makes it lighting independent.
     */

    static Histogram_F64 calc_HSV_SatOnly(BufferedImage buffered) {

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
        Histogram_F64 histogram = new Histogram_F64(128); // HARD CODED histDim

        histogram.setRange(0, 0, 1.0);         // range of saturation is from 0 to 1
 
        // Compute the histogram
        GHistogramFeatureOps.histogram(s, histogram);
        return histogram;
    }

    /**
     * Constructs a 3D histogram using RGB.  RGB is a popular color space, but the resulting histogram will
     * depend on lighting conditions and might not produce the accurate results.
     */
     static Histogram_F64 calc_RGB_Hist(BufferedImage buffered) {

        Planar<GrayF32> RGB = new Planar<GrayF32>(GrayF32.class,1,1,3);
        RGB.reshape(buffered.getWidth(), buffered.getHeight());
        ConvertBufferedImage.convertFrom(buffered, RGB, true);
 
        Histogram_F64 histogram = new Histogram_F64(12,12,12); // HARD CODED histDim
        histogram.setRange(0, 0, 255);
        histogram.setRange(1, 0, 255);
        histogram.setRange(2, 0, 255);
 
        GHistogramFeatureOps.histogram(RGB,histogram);
 
        return histogram;
    }


    /**
     * Computes a histogram from the gray scale intensity alone.  Probably the least effective at looking up
     * similar images.
     */
    static Histogram_F64 calc_Greyscale_Hist(BufferedImage buffered) {
 
        GrayU8 gray = new GrayU8(1,1);

        gray.reshape(buffered.getWidth(), buffered.getHeight());
        ConvertBufferedImage.convertFrom(buffered, gray, true);
 
        Histogram_F64 imageHist = new Histogram_F64(128); // HARD CODED histDim
        HistogramFeatureOps.histogram(gray, 255, imageHist);
 
        return imageHist;
    }
 
    private static void usage(String msg) {
        if (msg != null) {
            System.err.println(msg);
        }
        System.err.println("usage: <run> <dir_w_jpgs>");
        System.exit(1);
    }

    // Gurdjieff's advice to his daughter?
    // "Always finish what you start. ... Don't cling to things that will eventually destroy you."

    private static String getSuffix(final String path) {
        String result = null;
        if (path != null) {
            result = "";
            if (path.lastIndexOf('.') != -1) {
                result = path.substring(path.lastIndexOf('.'));
                if (result.startsWith(".")) {
                    result = result.substring(1);
                }
            }
        }
        return result;
    }

    private static int done;

    private static void buildJpgListRecursively(File root) {

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
                buildJpgListRecursively(f);
                // pout( "Dir:" + f.getAbsoluteFile() );
            } else {
                String fname = f.getName();
                if (!fname.toLowerCase().endsWith(".jpg")) {
                    continue;
                }
                String path = f.getAbsolutePath();
                String histPath = path.replace(".jpg", ".hist");
                File h = new File(histPath);
                if (h.exists()) {
                    done++;
                    continue;
                }
                // pout( "File:" + f.getAbsoluteFile() );
                try {

                    FileRec fr = new FileRec(path, false);

                    fileList.add(fr); 

                } catch (Exception e) {
                    e.printStackTrace();
                    err("Wow");
                }
            }
        }
    }

    private static String formatInterval(final long l) {
        final long hr = TimeUnit.MILLISECONDS.toHours(l);
        final long min = TimeUnit.MILLISECONDS.toMinutes(l -
                    TimeUnit.HOURS.toMillis(hr));
        final long sec = TimeUnit.MILLISECONDS.toSeconds(l -
                    TimeUnit.HOURS.toMillis(hr) -
                    TimeUnit.MINUTES.toMillis(min));
        final long ms = TimeUnit.MILLISECONDS.toMillis(l -
                    TimeUnit.HOURS.toMillis(hr) -
                    TimeUnit.MINUTES.toMillis(min) -
                    TimeUnit.SECONDS.toMillis(sec));
        return String.format("%02d:%02d:%02d.%03d", hr, min, sec, ms);
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

    public static void main(String[] args) {

        //test(args);
        if (args.length != 1) {
            usage("arg len");
        }

        String picDir = args[0];

        if (picDir.startsWith("~")) {
            picDir = picDir.replace("~", System.getProperty("user.home"));
            pout("-- Adjusted picDir: " + picDir);
        }
        File pic_dir = new File(picDir);
        if (!pic_dir.isDirectory()) {
            usage("picDir not a dir: " + picDir);
        }

        // recurse for files, recurse
        done = 0;
        buildJpgListRecursively(pic_dir);

        pout("-- Recursively got " + fileList.size() + " .jpg, skipping done: " + done);

        if (fileList.size() == 0) {
            err("No .jpg in " + picDir);
        }
        pout("1st  " + fileList.get(0).file);
        pout("last " + fileList.get(fileList.size()-1).file);

        doit();
    }
}
