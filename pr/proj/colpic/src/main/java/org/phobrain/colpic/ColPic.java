package org.phobrain.colpic;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  ColPic/cmdline - Calculate image average color 
 **                     and average 'radius' in RGB, HSV, LAB.
 **
 */

import org.phobrain.util.Stdio;
import org.phobrain.util.MiscUtil;
import org.phobrain.util.ColorUtil;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import java.util.*;
import java.util.concurrent.TimeUnit;

import java.io.IOException;
import javax.imageio.ImageIO;

public class ColPic extends Stdio {

	private static void usage(String msg) {
		if (msg != null) {
			System.err.println(msg + ":");
		}
		System.err.println(
		    "usage: <run> <jpg|dir_w_jpgs> <output_dir|output_file>");
		System.err.println(
		    "       output_dir = //image_desc/0color");
		System.exit(1);
	}

    private static List<Exception> El = new ArrayList<>();

    private static BufferedImage readLoudly(File f) {

	    System.out.print("\rReading " + f.getName() + "        ");

        if (f.length() == 0) {
            pout("Nope");
            err("File size is 0");
            return null;
        }

		try {
			BufferedImage bi = ImageIO.read(f);
            if (bi == null) {
                err("Null ImageIO.read(): " + f.getPath());
            }
            return bi;
		} catch (Exception e) {
			pout("Skipping " + f + ": " + e);
            El.add(e);
			return null;
		}
    }

    // static for multi-thread
    // one is left null
    static private PrintStream outStream = null;
    static private File outDir = null;

	/**
	 * HSV stores color information in Hue and Saturation 
     * while intensity is in Value.  
	 */
	public static void calc(List<File> fList) throws Exception {

		double[] lab = new double[3];
		double[] xyz = new double[3];
		double[] hsv = new double[3];

		// pout("Calcing " + fList.size());
		for (File file: fList) {

			BufferedImage image = readLoudly(file);

			if ( image == null ) {
                if (El.size() > 0) {
                    err("I/O Exception: " + El.get(0));
                }
                pout("Skipped");
				continue;
            }

			// averages

            int rr = 0, gg = 0, bb = 0;
            double ll = 0.0, aa = 0.0, bbb = 0.0;

			for (int x=0; x<image.getWidth(); x++) {

                for (int y=0; y<image.getHeight(); y++) {

                    final int clr = image.getRGB(x, y);
                    final int red = (clr & 0x00ff0000) >> 16;
                    final int green = (clr & 0x0000ff00) >> 8;
                    final int blue = clr & 0x000000ff;

                    ColorUtil.rgbToLab(red, green, blue, lab);

                    rr += red; gg += green; bb += blue;
                    ll += lab[0]; aa += lab[1]; bbb += lab[2];
                }
			}

            int n = image.getWidth() * image.getHeight();

            rr /= n; gg /= n; bb /= n;
			ll /= n; aa /= n; bbb /= n;

			int iL = (int) ll;
			int iA = (int) aa;
			int iB = (int) bbb;

			// average radius

			double rLab = 0.0;
			double maxL = 0.0;
			double minL = 999999;

			double rRgb = 0.0;

			for (int x=0; x<image.getWidth(); x++) {

     		    for (int y=0; y<image.getHeight(); y++) {

				    final int clr = image.getRGB(x, y);
				    int red = (clr & 0x00ff0000) >> 16;
				    int green = (clr & 0x0000ff00) >> 8;
				    int blue = clr & 0x000000ff;

				    ColorUtil.rgbToLab(red, green, blue, lab);

				    double r = ColorUtil.cdE2k(iL, iA, iB,
						(int) lab[0], 
						(int) lab[1], 
						(int) lab[2]);
				    rLab += r;
				    if (lab[0] > maxL) maxL = lab[0];
				    if (lab[0] < minL) minL = lab[0];

				    red -= rr; green -= gg; blue -= bb;
					
				    rRgb += Math.sqrt((double)
						    red * red + 
						    green * green +
						    blue * blue); 
			    }
		    }

			rLab /= n;
			rRgb /= n;

			if (minL < 1) minL = 1;

            String outName = file.getName() + " ";
            PrintStream thisOut = outStream;

            if (thisOut == null) { 

                outName = "";

                // new file-per-pic mode: get dir above pic,
                // which should be archive

                String archDir = file.getParentFile().getName();
                String parent = null;

                try {

                    int archive = Integer.parseInt(archDir);

                    if (archive < 1) {
                        err("Expected parent dir to be archive number >=1: " +
                            file.getPath());
                    }

                    parent = outDir.getPath() + "/" + archDir;
                    File f = new File(parent);
                    if (!f.exists()) {
                        pout("creating archive: " + parent);
                        f.mkdir();
                    }

                } catch (NumberFormatException nfe) {

                    err("Expected parent dir to be archive number: " +
                            file.getPath());
                }
                thisOut = new PrintStream(
                            new FileOutputStream(
                                outDir.getPath() + "/" + 
                                archDir + "/" + 
                                file.getName()
                            ));
            }

			thisOut.println(outName + // empty if out file is same name
					image.getWidth() + " " + image.getHeight() + " " + 
                    file.length() + " " + 
                    ( (double)file.length() / 
                            ((double)image.getWidth() * 
                             (double)image.getHeight()) ) + " " + 
                    rr + " " + gg + " " + bb + " " + 
                    (int) rRgb + " " + iL + " " + iA + " " + iB + " " + 
                    (int) rLab + " " + //" Ml " + maxL + " ml " + minL +
					(int) (maxL / minL) );
            if (outStream == null) {
                thisOut.close();
            }
		}
	}
 
	private static class FileRec implements Comparable {
		int arch;
		int seq;
		String name;
		File file;
		@Override
		public int compareTo(Object o) {
			FileRec fr = (FileRec) o;
			if (arch < fr.arch) return -1;
			if (arch > fr.arch) return 1;
			if (seq < fr.seq) return -1;
			if (seq > fr.seq) return 1;
			return 0;
		}
    }


	private static List<File> getFilesFromDir(File dir, String lastFile) {

        String outParent = null;

        if (outDir != null) {

            // should be archive 

            String archDir = dir.getName();

            try {

                int archive = Integer.parseInt(archDir);

                if (archive < 1) {
                    err("Expected dir to be archive number >=1: " +
                                dir.getPath());
                }
            } catch (NumberFormatException nfe) {
                err("Expected parent dir to be archive number: " +
                            dir.getPath());
            }

            outParent = outDir.getPath() + "/" + archDir;
            File f = new File(outParent);
            outParent += "/";
            if (!f.exists()) {
                pout("creating archive dir for output: " + outParent);
                f.mkdir();
            }
        }

	    List<File> fileList = new ArrayList<>();

        int ok = 0;

		try {
			boolean skip = false;
			if (lastFile != null) {
				skip = true;
				pout("Skipping through " + lastFile + " and appending");
			}
			String base = dir.getAbsolutePath() + "/";
			String fnames[] = dir.list();

			int skipCt = 0;
			for (String fname : fnames) {
				if (skip) {
					skipCt++;
					System.out.print(".");
					if (++skipCt % 70 == 0) {
						pout("");
					}
					if (fname.equals(fname)) {
						skip = false;
					}
					continue;
				}
				if (!fname.endsWith(".jpg")) {
					pout("Skipping " + fname);
					continue;
				}
                if (outDir != null) {
                    // skip if outDir version is current
                    File jpg = new File(base + fname);
                    File color = new File(outParent + fname);
                    if (color.exists()) {
                        if (jpg.lastModified() < color.lastModified()) {
                            ok++;
                            continue;
                        }
                    }
                }
				fileList.add(new File(base + fname));
			}
		
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

        if (ok > 0) {
            pout("Skipped/up-to-date jpg: " + ok);
            if (fileList.size() == 0) {
                pout("All up-to-date in " + dir.getPath());
            }
        }

        return fileList;
	}

	public static void main(String[] args) {

        if (args.length != 2) {
            usage("expected 2 args");
        }

        // arg 0

        String s = args[0].trim();
        File f_arg0 = new File(s);
        if (!f_arg0.isDirectory()  && !s.endsWith(".jpg")
                              && !s.endsWith(".JPG")) {
		    usage("Not a dir or jpg: " + args[0]);
        }

        // arg 1

        File outFile = null;
		String lastFile = null;

		File f_arg1 = new File(args[1]);
        if (!f_arg1.exists()) {
            usage("normally //image_desc/0_color/");
        }

		if (f_arg1.isDirectory()) {

            // individual output file for each jpg

            outDir = f_arg1;

            pout("Output dir: " + outDir);

        } else {

            // bulk calc to one file, legacy, maybe keep for build

            outFile = f_arg1;

            pout("Output file{LEGACY]: " + outFile);

			try {
				if (outFile.exists()) {
					BufferedReader in = new BufferedReader(
						new FileReader(outFile));
					String line;
					while ((line = in.readLine()) != null) {
						String ss[] = line.split(" ");
						lastFile = ss[0];
					}
					in.close();
				} else {
					if (!outFile.createNewFile()) {
						usage("outFile already exists: " + args[1]);
					}
				}
			} catch (Exception e) {
				err("Creating or reading outFile [" + args[1] + "]: " + e);
			}
		}

		List<File> flist = null;
        if (f_arg0.isDirectory()) {
            flist = getFilesFromDir(f_arg0, lastFile);
            pout("Files: " + flist.size());
        } else {
            flist = new ArrayList<File>();
            flist.add(f_arg0);
        }

        if (flist.size() == 0) {
            pout("Done (no files)");
            System.exit(0);
        }

        int PROCS = 14;
        if (flist.size() < PROCS) {
            PROCS = flist.size() / 5;
            if (PROCS == 0) {
                PROCS = 1;
            }
        }
        pout("Threads: " + PROCS);
        if (flist.size() < 5) {
            pout("Files: " + Arrays.toString(flist.toArray()));
        }

        int perproc = flist.size() / PROCS;

		try {
            if (outFile != null) {
                outStream = new PrintStream(
                                    new FileOutputStream(outFile, true));
            }
            if (perproc < 2) {
			    calc(flist);
            } else {

                pout("Calcing with " + PROCS + " Threads, " + 
                                        perproc + " each");

                Thread[] threads = new Thread[PROCS];

                for (int i=0; i<PROCS; i++) {
                    int start = i * perproc;
                    int end = start + perproc;
                    if (i == PROCS-1) {
                        end = flist.size();
                    }
                    List<File> subL = flist.subList(start, end);
                    threads[i] = new Thread(new Runnable() {
                        public void run() {
                            try {
			                    calc(subL);
                            } catch (Exception e) {
                                pout("Err: " + e);
                                System.exit(1);
                            }
                        }
                    });
                    threads[i].start();
                }
                for (Thread t : threads) {
                    t.join();
                }
                pout("Threads done");
            }
            if (outStream != null) {
			    outStream.close();
            }
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
