package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  MiscUtil - title says it all.
 **
 */

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;

import javax.swing.ImageIcon;

import javax.naming.InvalidNameException;

public class MiscUtil extends Stdio {

/*
    public static String stringify(List l) {
        return stringify(l.toArray());
    }
    public static String stringify(Object arr) {
        return Arrays.toString(arr);
    }
*/

    public static String getPhobrainLocal() {

        String local = System.getenv("PHOBRAIN_LOCAL");

        if (local == null) {
            local = "~/phobrain_local";
        }

        if (local.startsWith("~")) {
            local = local.replace("~", System.getProperty("user.home"));
        }

        File dir = new File(local);

        if (!dir.isDirectory()) {
            err("Expecting PHOBRAIN_LOCAL dir to exist: " + local);
        }

        return local;
    }

    private static List<String> local_camera_tags = null;

    public static void loadLocalCameraTags() {

        String fname = null;

        try {

            String local = getPhobrainLocal();

            fname = local + "/camera_tags";

            File f = new File(fname);

            if (!f.exists()) {
                pout("\t(no local/specialty camera tags file: " + fname + ")");
                return;
            }

            local_camera_tags = new ArrayList<String>();

            BufferedReader in = new BufferedReader(
                        new FileReader(fname));

            String line;
            while ((line = in.readLine()) != null) {

                line = line.trim();

                if (line.length() == 0  || line.startsWith("#")) {
                    continue;
                }

                // old school sensibility restrictions

                if (line.contains(" ")) {
                    pout("Skipping camera tag w/ space in " +
                        fname + " [" + line + "]");
                    continue;
                }
                if (line.length() > 4) {
                    pout("Skipping suspiciously-long camera tag in " +
                        fname + " [" + line + "]");
                    continue;
                }

                local_camera_tags.add(line.toUpperCase());
            }

        } catch (Exception e) {
            e.printStackTrace();
            err("loadCameraTags: " + e);
        }

        if (local_camera_tags.size() == 0) {

            pout("(No camera tags in " + fname);

        } else {

            pout("Loaded camera tags from " + fname +
                ": " + Arrays.toString(local_camera_tags.toArray()));
        }

    }

    // NB sync local camera img filename strip for parallel

    private static Object syncStrip = new Object();

    private static int localStripped = 0;

    static String localStrip(String s) {

        synchronized(syncStrip) {

            if (localStripped == 0) {
                loadLocalCameraTags();
            }
            localStripped++;
        }

        if (local_camera_tags == null) {
            return s;
        }

        // local tag not expected w/ generic one

        if (s.contains("IMG") ||
            s.contains("_MG") ||
            s.contains("DSC")) {

            return s;
        }

        for (String tag : local_camera_tags) {

            int ix = s.indexOf(tag);
            if (ix == -1) {
                ix = s.indexOf(tag.toLowerCase());
            }
            if (ix == -1) {
                continue;
            }
            ix += tag.length();
            s = s.substring(ix);

            // only one camera tag expected
            break;
        }

        return s;
    }
/*
        if (!s.equals(strip)) {

            pout("Used local camera_tags to strip: " +
                        s + " -> " + strip);
        }
        return strip;
    }
*/

    public static int parseSeq(String s) throws InvalidNameException {

        s = localStrip(s);

/*
        if (!s.toLowerCase().contains("img")  &&
            !s.toLowerCase().contains("_mg")  &&
            !s.toLowerCase().contains("dsc")) {
            throw new InvalidNameException(
                    "parseSeq: Expected 'img', '_mg', 'dsc': " + s);
        }
*/
        int start = -1;
        int end = -1;
        for (int i=0; i<s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                start = i;
                break;
            }
        }
        if (start == -1) {
            throw new InvalidNameException("parseSeq: Expected a number: " + s);
        }
        for (int i=start+1; i<s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                end = i;
                break;
            }
        }
        if (end == -1) {
            end = s.length();
        } else {
/*
ignoring secondary numbers
            for (int i=end+2; i<s.length(); i++) {
                if (Character.isDigit(s.charAt(i))) {
                    throw new Exception("More than 1 number: " + s);
                }
            }
*/
        }

        s = s.substring(start, end);

        s = s.toLowerCase();

        s = localStrip(s);

        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException nfe) {
            throw new InvalidNameException("Expected int: " + s + ": " + nfe);
        }

    }

    public static List<Integer> parseInts(String val, int limit) {
        val = val.trim();
        String ss[] = val.split(" ");
        List<Integer> l = new ArrayList<>();
        int count = 0;
        for (String s : ss) {
            if (s.trim().length() != 0) {
                l.add(Integer.parseInt(s));
                count++;
                if (count > limit) break;
            }
        }
        return l;
    }

    public static int normalizeFracDim(String s)
            throws NumberFormatException {

        try {
            float f = Float.parseFloat(s);
            f *= 1000.0d;
            return Math.round(f);
        } catch (NumberFormatException nfe) {
            throw new NumberFormatException("Bad fracDim: " + s  + ": " + nfe);
        }

    }

    public static String formatInterval(final long l) {
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

    public static List<Integer> intList(String s)
            throws NumberFormatException {

        List<Integer> ret = new ArrayList<>();

        if (s == null) {
            return ret;
        }

        String ss[] = s.split(" ");

        try {
            for (String si : ss) {
                ret.add(Integer.parseInt(si));
            }
        } catch (NumberFormatException nfe) {
            throw new NumberFormatException("Parsing " + s + ": " + nfe);
        }
        return ret;
    }

    final static private char digits[] =
      // 0       8       16      24      32      40      48      56     63
      // v       v       v       v       v       v       v       v      v
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_-".toCharArray();

    final static private Map<Character, Integer> digitMap = new HashMap<>();
    static {
        for (int i=0; i<digits.length; i++) {
            digitMap.put(digits[i], i);
        }
    }

    public static int[] parseIntList(String s)
            throws IllegalArgumentException {

        if (s == null) {
            throw new IllegalArgumentException("parseIntList: null list");
        }
        s = s.trim();

        if (s.length() == 0) {
            throw new IllegalArgumentException("parseIntList: empty list");
        }
        String[] ss = s.split(",");

        int[] ints = new int[ss.length];
        int ix = 0;

        int errs = 0;
        StringBuilder nans = new StringBuilder();

        try {
            for (int i=0; i<ss.length; i++) {
                if ("NaN".equals(ss[i])) {
                    nans.append(i).append(",");
                    ints[i] = -123456789;
                    errs++;
                } else {
                    ints[i] = Integer.parseInt(ss[i]);
                }
                ix++;
            }
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(
                        "parseIntList: elt " + ix + 
                        " [" + ss[ix] + "]: " + nfe);
        }

        if (errs > 0) {
            throw new IllegalArgumentException(
                        "parseIntList: errs " + errs + 
                        " [" + s + "] NaNs=" + nans.toString());
        }

        return ints;
    }

    // sacrifice 16510910
    public final static String NULL_BASE64 = "____";

    public static int base64ToInt(String s) 
            throws IllegalArgumentException {

        if (s == null) {
            throw new IllegalArgumentException("base64ToInt: NULL str");
        }

        int res = 0;
        for (int i=0; i<s.length(); i++) {

            Character c = s.charAt(i);

            if (c == null) {
                throw new IllegalArgumentException("UNK char " +
                             Character.isLetterOrDigit(c) + " " +
                             Character.isWhitespace(c) + " " +
                             Character.isSpaceChar(c) + " " +
                             Character.getNumericValue(c));
            }

            // this is the base64
            res = (res << 6) + digitMap.get(c);
        }

        return res;
    }

    public static int[] base64ToIntArray(String s)
            throws NumberFormatException {

        if (s == null) {
            return null;
            // throw new RuntimeException("NULL str");
        }
        if ("none".equals(s)  ||  "0".equals(s)) {
            return null;
        }

        String tt[] = s.split(",");

        int ret[] = new int[tt.length];
        for (int i=0; i< tt.length; i++) {
            ret[i] = base64ToInt(tt[i]);
        }
        return ret;
    }

    public static void copyFileToFile(final File src, final File dest)
            throws IOException {

        copyInputStreamToFile(new FileInputStream(src), dest);
        dest.setLastModified(src.lastModified());
    }

    public static void copyInputStreamToFile(final InputStream in,
                                             final File dest)
            throws IOException {
        copyInputStreamToOutputStream(in, new FileOutputStream(dest));
    }


    public static void copyInputStreamToOutputStream(final InputStream in,
                                                     final OutputStream out)
            throws IOException {
        try {
            try {
                final byte[] buffer = new byte[1024];
                int n;
                while ((n = in.read(buffer)) != -1)
                    out.write(buffer, 0, n);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    private static Random rand = new Random();

    public static void shuffleInts(int[] arr) {
        for (int k=arr.length-1; k>-1; k--) {
            int kk = rand.nextInt(k+1);
            int a = arr[kk];
            arr[kk] = arr[k];
            arr[k] = a;
        }
    }

    public static List<File> getNewJpgsFromArchDir(File inDir,
                                                   File outDirParent,
                                                   String suffix)
            throws Exception {


        String archDir = inDir.getName();

        // should be archive - relax for non-phobrain

        int archive = Integer.parseInt(archDir);

        if (archive < 1) {
            err("Expected dir to be archive number >=1: " + inDir.getPath());
        }

        String outParent = outDirParent.getPath() + "/" + archDir;
        File f = new File(outParent);
        outParent += "/";
        if (!f.exists()) {
            pout("creating archive dir for output: " + outParent);
            f.mkdir();
        }

        List<File> fileList = new ArrayList<>();

        int ok = 0;

        String base = inDir.getAbsolutePath() + "/";
        String fnames[] = inDir.list();

        for (String fname : fnames) {
            if (!fname.endsWith(".jpg")) {
                pout("Skipping " + fname);
                continue;
            }
            File jpg = new File(base + fname);
            String newFname = null;
            if (suffix != null) {
                newFname = fname.replace("jpg", suffix);
            } else {
                newFname = fname;
            }
            // skip if outDirParent version is current
            File target = new File(outParent + newFname);
            if (target.exists()) {
                if (jpg.lastModified() < target.lastModified()) {
                    ok++;
                    continue;
                }
            }
            fileList.add(jpg);
        }

        if (ok > 0) {
            pout("Skipped/up-to-date jpg: " + ok);
            if (fileList.size() == 0) {
                pout("All up-to-date in " + outParent);
            }
        }

        return fileList;
    }

    public static String getStack(int level) {

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        level += 2; // 2 == skip thread and this level

        if (stackTrace.length < level) {
            level = stackTrace.length;
        }

        StringBuilder sb = new StringBuilder();
        for (int i=2; i<level; i++) {
            sb.append("\t").append(stackTrace[i]).append("\n");
        }
        return sb.toString();
    }

    public static class SeenIds {

        public Set<String> exclude = new HashSet<>();

        public Set<String> seen = null;

        public void add(String id, String orient) {
            if (seen == null) {
                seen = new HashSet<String>();
            }
            seen.add(id);
            if ("v".equals(orient)) {
                vertical++;
            } else {
                horizontal++;
            }
        }

        public int vertical = 0;
        public int horizontal = 0;

        public boolean contains(String id) {
            if (seen != null) {
                if (seen.contains(id)) {
                    return true;
                }
            }
            //log.info("EXCL " + exclude.size());
            return exclude.contains(id);
        }

        public int size() {
            if (seen == null) return 0;
            return seen.size();
        }
    }

    public static int getProcs() {

        try {

            int nprocs = Runtime.getRuntime().availableProcessors();

            if (nprocs < 2) {
                pout("-- getProcs: just 1 for available procs=" + nprocs);
                return 1;
            }

            OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(
                OperatingSystemMXBean.class);

            double jvmLoad = osBean.getProcessCpuLoad();
            double sysLoad = osBean.getSystemCpuLoad();

            if (sysLoad == 0.0) {

                try { Thread.sleep(1000); } catch (Exception ignore) {}

                sysLoad = osBean.getSystemCpuLoad();

                if (sysLoad == 0.0) {
                    pout("-- getProcs: sysload twice 0.0");
                }
            }

            pout("-- getProcs: availableProcs: " + nprocs +
                    " sysLoad: " + sysLoad +
                    "  jvm: " + jvmLoad);

            // sysload seems in 0..1

            if (sysLoad >= 1.0) {

                if (nprocs < 5) {

                    pout("-- getProcs: 1 since sysLoad>=1.0  and nprocs<5");

                    return 1;
                }

                if (nprocs < 10) {

                    pout("-- getProcs: 3 since sysLoad>=1.0  and nprocs in 5..9");

                    return 3;
                }

                if (nprocs < 21) {

                    pout("-- getProcs: 5 since sysLoad>=1.0  and nprocs in 10..20");

                    return 5;
                }

                pout("-- getProcs: nprocs/4 since sysLoad>=1.0  and nprocs>20");

                return nprocs/4;

            }

            if (sysLoad == 0.0) {

                pout("-- getProcs: using half since sysLoad suspect");

                return nprocs / 2;

            }

            double availFrac = 1.0 - sysLoad;

            if (jvmLoad == 0.0) {

                double ezFrac = (double)(nprocs-2) / nprocs;

                // proc startup

                if (availFrac < ezFrac) {

                    // TODO assuming high-priority; allow user setting
                    pout("getProcs: nprocs-2 at startup");

                    availFrac = ezFrac;
                }

            } else {
                // roll over the current share
                availFrac += jvmLoad;
            }

            if (availFrac < 0.0) {

                pout("-- getProcs: unexpected sysload, default nprocs/2");
                return nprocs / 2;

            }

            long avail = (long) (availFrac * nprocs);
            if (avail < 1) {
                avail = 1;
            }
            if (avail > nprocs) {

                pout("-- avail=?=" + avail);

                return nprocs / 2;

            }

            int ret = (int) avail;

            if (ret >= nprocs) {
                ret = nprocs - 2;
                if (ret < 1) {
                    ret = 1;
                }
            }

            return ret;

        } catch (Exception e) {
            err("getProcs: " + e);
        }

        return -1; // for compiler

    }

    public static File checkDir(String path) {

        File f = new File(path);
        if (!f.isDirectory()) {
            err("checkDir: not a dir: " + path);
        }
        return f;
    }

    public static File checkFile(String path) {
        File f = new File(path);
        if (!f.isFile()) {
            err("checkFile: not a file: " + path);
        }
        return f;
    }

    private static String listArchiveDir(String images_dir, int archive) {

        String listDir = images_dir + archive + "/";

        File archdir = new File(listDir);

        if (!archdir.exists()) {
            err("MiscUtil.listArchiveDir: Dir does not exist: " + listDir);
        }

        File[] list = archdir.listFiles();
        if (list.length == 0) {
            err("MiscUtil.listArchiveDir: No files in dir: " + listDir);
        }

        StringBuilder sb = new StringBuilder();

        int njpg = 0, nv = 0, nh = 0;

        for (File f : list) {

            String s = f.getName();

            if (!s.endsWith(".jpg")) {
                continue;
            }

            njpg++;

            String fname = archive + "/" + s;  // as used in output

            ImageIcon icon = new ImageIcon(images_dir + fname);

            int h = icon.getIconHeight();
            int w = icon.getIconWidth();

            if (h == 0  ||  w == 0) {
                err("Bad ImageIcon: height/width " + h + "/" + w +
                                 ": " + images_dir + fname);
            }

            if (icon.getIconHeight() > icon.getIconWidth()) {
                sb.append(fname + " t\n");
                //out.println(fname + " t");
                nv++;
            } else {
                sb.append(fname + " f\n");
                //out.println(fname + " f");
                nh++;
            }

        } // files

        pout("Done: " + listDir + " Files: " + njpg + "  v/h " + nv + "/" + nh);

        return sb.toString();
    }

    /*
    **  listImageFiles - this is the initial Sorting Hat for deciding
    **      the orientation of photos on import: 
    **
    **          'v' / vertical / portrait orientation
    **          'h' / vertical / landscape orientation
    **
    **      In outfile, it's boolean tho:
    **
    **          # pic   vertical
    **          1/img01a.jpg f
    */

    public static void listImageFiles(String outfile, String images_dir, int[] archives) {

        long t0 = System.currentTimeMillis();

        int ndirs = 0;
        int nfiles = 0, nv = 0, nh = 0;

        if (outfile == null) {
            err("MiscUtil.listImageFiles: outfile is null");
        }
        if (images_dir == null) {
            err("MiscUtil.listImageFiles: images_dir is null");
        }
        images_dir = images_dir.trim();

        if (archives == null  ||  archives.length == 0) {
            err("MiscUtil.listImageFiles: expected archives[]");
        }

        PrintStream out = null;

        try {

            File idir = new File(images_dir);

            if (!idir.isDirectory()) {
                err("MiscUtil.listImageFiles: not a dir: [" + images_dir + "]");
            }

            if (!images_dir.endsWith("/")) {
                images_dir += "/";
            }

            File fout = new File(outfile);

            /* these are tmp files, bro
            if (fout.exists()) {
                pout("MiscUtil.listImageFiles: Overwriting in 5 seconds: " + outfile);
                try { Thread.sleep(5000); } catch (Exception ignore) {}
            }
            */

            out = new PrintStream(fout);

            pout("MiscUtil.listImageFiles: writing " + archives.length + 
                        " archive" + (archives.length > 1 ? "s" : "") + 
                        " to " + outfile);

            int max_procs = getProcs();
            if (archives.length > 1  &&
                archives.length <= max_procs) {

                pout("MiscUtil.listImageFiles: running 1 proc per archive");

                Thread[] threads = new Thread[archives.length];

                final PrintStream locout = out;
                final String locimagesdir = images_dir;

                for (int i=0; i<threads.length; i++) {

                    final int archive = archives[i];

                    threads[i] = new Thread(new Runnable() {
                        public void run() {

                            // per-thread workspace

                            String result = listArchiveDir(locimagesdir, archive);
                            locout.print(result);
                        }
                    });
                    threads[i].start();
                }
                for (Thread t : threads) {
                    t.join();
                }

                pout("-- parallelized version done");

            } else {

                // serial (for now if  >max_procs)

                for (int archive : archives) {

                    ndirs++;
                    String result = listArchiveDir(images_dir, archive);
                    out.print(result);

                }

            } // archive dirs
        } catch (Exception e) {
            e.printStackTrace();       
            err(" " + e);
        } finally {

            try { out.close(); } catch (Exception ignore) {}
        }

        pout("MiscUtil.listImageFiles: Archive dirs: " + ndirs + 
                //"  Files: " + nfiles + " v " + nv + " h " + nh +
                "  T=" + ((System.currentTimeMillis()-t0)/1000) + " sec");
    }
}
