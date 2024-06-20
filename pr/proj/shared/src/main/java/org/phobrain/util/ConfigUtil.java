package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  ConfigUtil - compileConfigDir()
 **             - /var/phobrain/phogit.properties Phobrain server/servlet config.
 **
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.FileReader;

import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigUtil extends Stdio {

    private static final Logger log = LoggerFactory.getLogger(ConfigUtil.class);

    private static String PHOBRAIN_COMPILE_CONFIG_DIR = null;

    private static Properties compileProps = null;
    private static Properties runtimeProps = null;

    final public static String RUNTIME_CONFIG_FILE = "/var/phobrain/phogit.properties";

    public static String compileProperty(String name) {

        if (compileProps == null) {
            File f = compileConfigDir("build.properties");
            pout("Loading compile properties: " + f);
            compileProps = loadProperties(f.getPath());
        }

        return compileProps.getProperty(name);
    }

    public static String runtimeProperty(String name, String dfault) {

        String s = runtimeProperty(name);
        if (s == null) return dfault;
        return s;
    }

    public static String runtimeProperty(String name) {

        if (runtimeProps == null) {
            pout("Loading runtime properties: " + RUNTIME_CONFIG_FILE);
            runtimeProps = loadProperties(RUNTIME_CONFIG_FILE);
        }

        return runtimeProps.getProperty(name);
    }

    public static File compileConfigDir(String fname) {
        try {
            if (PHOBRAIN_COMPILE_CONFIG_DIR == null) {
                PHOBRAIN_COMPILE_CONFIG_DIR = System.getenv("PHOBRAIN_COMPILE_CONFIG_DIR");
                if (PHOBRAIN_COMPILE_CONFIG_DIR == null) {
                    PHOBRAIN_COMPILE_CONFIG_DIR = System.getProperty("user.home") + "/phobrain_local";
                    pout("No PHOBRAIN_COMPILE_CONFIG_DIR set, trying " + PHOBRAIN_COMPILE_CONFIG_DIR);
                }
                if (PHOBRAIN_COMPILE_CONFIG_DIR == null) {
                    err("No way, no way.");
                }
                File f = new File(PHOBRAIN_COMPILE_CONFIG_DIR);
                if (!f.isDirectory()) {
                    err("Not a dir: " + PHOBRAIN_COMPILE_CONFIG_DIR);
                }
                pout("Phobrain compile config dir exists: " + PHOBRAIN_COMPILE_CONFIG_DIR);
            }

            String path = PHOBRAIN_COMPILE_CONFIG_DIR + "/" + fname;

            return new File(path);

        } catch (Exception e) {
            err("caught: " + e);
        }
        return null;
    }

    final public static int MAX_VIEW = 9; // TODO: get from config, why even care?

    public static Properties loadProperties(String fname) {

        log.info("ConfigUtil.loadProperties: " + fname);

        File config = new File(fname);
        if (!config.isFile()) {
            throw new RuntimeException("NO CONFIG FILE: " + fname);
        }

        InputStream propInput = null;
        try {
            propInput = new FileInputStream(config);
            Properties p = new Properties();
            p.load(propInput);
/*
            if (p.getProperty("local.dir") == null) {
                throw new RuntimeException("ConfigUtil " +
                        CONFIG_FILE +
                        ": no local.dir (PHOBRAIN_LOCAL) defined");
            }
*/
            return p;
        } catch (IOException ioe) {
            throw new RuntimeException("ConfigUtil: Loading config " + fname + ": " + ioe);
        } finally {
            if (propInput != null) {
                try { propInput.close(); } catch (Exception ignore) {}
            }
        }
    }

    public static int kwdCoderCode(String coder) {
        if ("b".equals(coder)) {
            return 1;
        } else if ("l".equals(coder)) {
            return 2;
        } else if ("m_geom".equals(coder)) {
            return 3;
        } else if ("m_nogeom".equals(coder)) {
            return 4;
        } else if ("pr_geom".equals(coder)) {
            return 5;
        } else if ("pr_nogeom".equals(coder)) {
            return 6;
        } else if ("pr_multi".equals(coder)) {
            return 7;
        } else if ("pr_multix".equals(coder)) {
            return 8;
        } else if ("pr_multil".equals(coder)) {
            return 9;
        } else if ("pr_multir".equals(coder)) {
            return 10;
        } else {
            throw new RuntimeException("Unknown coder: " + coder);
        }
    }

    public static double[] parseDoubleArray(String s) {
        String ss[] = s.split(" ");
	double[] ret = new double[ss.length];
        try {
            for (int i=0; i<ss.length; i++) {
                ret[i] = Double.parseDouble(ss[i]);
	    }
        } catch (NumberFormatException nfe) {
            throw new RuntimeException("Bad fracDim: " + s);
        }
	return ret;
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

    public static List<String> loadViewIds(String fname) {

        List<String> ret = new ArrayList<>();

        File f = new File(fname);

        if (!f.isFile()) {
            throw new RuntimeException("loadViewIds("+fname+"): not a file");
        }

        BufferedReader in = null;
        int lineN = 0;
        String line = "start";
        try {
            in = new BufferedReader(new FileReader(f));
            while ((line = in.readLine()) != null) {
                lineN++;
                if (line.startsWith("--")) {
                    continue;
                }
                String ss[] = line.split(" ");
                ret.add(ss[0]);
            }
        } catch (Exception e) {
            throw new RuntimeException("loadViewIds("+fname+"): line " + lineN + "\n" +
                                       line, e);
        } finally {
            try { in.close(); } catch (Exception ignore) {}
        }

        return ret;
    }
}
