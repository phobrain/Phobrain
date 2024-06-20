package org.phobrain.servlet;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  ServletUtil - exclude over-busy or blacklisted IPs
 **              - handle admin IP checking using hardcoded
 **                list of pages that show status (mju.html)
 **                or affect pair labels in the db. Such
 **                Pages shouldn't load to non-admin hosts,
 **                nor should label-related Servlets accept
 **                requests.
 **/

import org.phobrain.util.ConfigUtil;
import org.phobrain.util.MiscUtil;

import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.PictureDao;
import org.phobrain.db.dao.KeywordsDao;
import org.phobrain.db.record.Picture;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;

import javax.naming.NamingException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServletUtil {

    private static final Logger log = LoggerFactory.getLogger(
                                                      ServletUtil.class);

    public static int getInt(HttpServletRequest req, String param,
                                                     String sessionTag)
            throws Exception {

        String str = req.getParameter(param);
        if (str == null) {
            log.error("NO '" + param + "' sessionTag " + sessionTag);
            throw new Exception();
        }
        str = str.trim();
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException nfe) {
            // be forgiving and accept Double
            try {
                Double d = Double.parseDouble(str);
                return d.intValue();
            } catch (NumberFormatException nfe2) {
                log.error("PARSING '" + param + "' [" + str + "] sessionTag " +
                        sessionTag + " : " + nfe + " : " + nfe2);
            }
            throw new Exception();
        }
    }

    private static String listify(List<String[]> list) {

        if (list.size() == 0) {
            return "empty list";
        }

        StringBuilder sb = new StringBuilder();
        for (String[] item : list) {
            for (String sub : item) {
                sb.append(sub).append("/");
            }
            sb.replace(sb.length()-1, sb.length(), ", ");
        }
        sb.replace(sb.lastIndexOf(", "), sb.length(), "");

        return sb.toString();
    }

    // pages that require 'admin' access, initially by fixed IP

    private static List<String> adminPages = new ArrayList<>();

    // allowed admin IPs (for labeling and status)

    private static List<String[]> adminIPs = new ArrayList<>();
    private static String ipString;

    private static StringBuilder adminStatus = new StringBuilder("------ ADMIN settings\n\n");

    // static init section: vars initialized by methods called here must be declared above here, else hang

    static {
        log.info("ServletUtil static section");

        initExcludeIPs();
        adminIPs.add(new String[] {"127.0.0.1", "[localhost]", "localhost/default" });
        log.info("ServletUtil hack test done");
        initAdminIPs();
        initAdminPages();

        log.info("ServletUtil static section - DONE");
    }

    public static String getAdminConfig() {
        return adminStatus.toString();
    }

    public static boolean isAdminIP(String ip) {

        if (ipString == null) {
            log.error("isAdminIP(" + ip + ") - admin ip's not configged");
            return false;
        }

        if (ip.startsWith("[")) {
            //log.info("shortening ip, orig=" + ip);
            ip = ip.substring(1, ip.length()-1);
            //log.info("shorten/retro ip, short=" + ip);
        }
        return ipString.contains(ip);
    }

    private static void initAdminIPs() {

        log.info("initAdminIPs: add localhost and any IPs in local adminIP file");

        adminIPs.add(new String[] {"127.0.0.1", "localhost/default" });
        adminIPs.add(new String[] {"0:0:0:0:0:0:0:1", "localhost/default" });
        adminIPs.add(new String[] {"localhost", "localhost/default" });

        log.info("initAdminIPs: added");

        String adminFile = ConfigUtil.runtimeProperty("local.dir") +
                                                          "/adminIP";

        File adminList = new File(adminFile);
        if (!adminList.isFile()) {
            adminStatus.append("Warn: ADMIN IP Init: No " + adminFile +
                        ", so admin/curation only possible from " +
                        listify(adminIPs) + ".\n\n");
            return;
        }
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(adminList));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.length() == 0  ||  line.charAt(0) == '#') {
                    continue;
                }
                line = line.trim();
                String[] fields = line.split("\t");
                if (fields.length == 1) {
                    fields = line.split(" ");
                    if (fields.length > 1) {
                        fields = new String[] { fields[0], null };
                        int i = fields[0].length();
                        for (; i < line.length(); i++) {
                            if (line.charAt(i) != ' ') {
                                fields[1] = line.substring(i);
                                break;
                            }
                        }
                        if (fields[1] == null) {
                            fields[1] = "no description";
                        }
                    }
                }
                if (fields.length != 2) {
                    adminStatus.append("ERROR: ADMIN IP (" + 
                                adminFile +"): bad line: [" + 
                                line +"]: fields=" + fields.length)
                               .append("\n\n");
                    continue;
                }
                if (!fields[0].contains(".")) {
                    adminStatus.append("ERROR: ADMIN IP (" + adminFile +"): No '.' in addr: " + fields[0])
                                        .append("\n\n");
                    continue;
                }
                if (fields[1].length() < 5) {
                    adminStatus.append("Warn: ADMIN IP: explanation <5: " + fields[1])
                                        .append("\n\n");
                }
                log.info("ADMIN IP: Config added [" + fields[0] + "]: " + fields[1]);
                adminIPs.add(fields);
            }
            //log.info("ADMIN IP: Config size: " + adminIPs.size());
        } catch (Exception e) {
            adminStatus.append("ADMIN Exception: Reading adminIP (" + adminFile + "): " + e)
                                        .append("\n\n");
            log.error("Reading adminIP (" + adminFile + "): " + e);
            throw new RuntimeException(e);
        } finally {
            if (in != null) {
                 try { in.close(); } catch (Exception ignore) {}
            }
            StringBuilder sb = new StringBuilder();
            for (String[] fields : adminIPs) {
                sb.append(fields[0]).append(" | ");
            }
            sb.setLength(sb.lastIndexOf(" | "));
            ipString = sb.toString();
            adminStatus.append("\n\n" +
                "Admin IPs (" + adminFile + "):\n[" + ipString +
                    "]\nWith comments: " + listify(adminIPs) +
                "\n\n");
        }
    }

    // admin pages to restrict service of by general file loader

    public static boolean failAdminPage(String page, String ip) {

        String testPage = null;

        if (page.endsWith(".gz")  ||  page.endsWith(".tz")) {
            testPage = page.substring(0, page.length()-3);
        } else {
            testPage = page;
        }

        boolean admin = false;
        for (String p : adminPages) {
            if (testPage.endsWith("/"+p)) {
                admin = true;
                break;
            }
        }
        if (!admin) {
            return false;
        }
        // is an admin page; need ip=permission
        if (isAdminIP(ip)) {
            return false;
        }
        // not an admin ip
        return true;
    }

    private static void initAdminPages() {

        log.info("initAdminPages:");

        // hard-coded to protect the less-technical

        adminPages.add("mju.html");
//log.info("initAdminPages: 1st added");
        adminPages.add("curate.html");
        adminPages.add("mnbpair.html");
        adminPages.add("mnbvcxz.html");
        adminPages.add("pairlist.html");
        adminPages.add("kdwd.html");

        log.info("initAdminPages: append status");

        adminStatus.append("\n\n")
                   .append("Admin-IP-only Pages (hard-coded in ServletUtil.java):\n\t")
                   .append(String.join(",", adminPages))
                   .append("\n");

        log.info("initAdminPages done");
/*
        coded this anyway
        String adminPages = ConfigUtil.runtimeProperty("local.dir") +
                                                          "/adminPages";

        File adminPages new File(adminPages);
        if (!adminPages.isFile()) {
            log.warn("ADMIN PAGES Init: No " + adminPages);
            return;
        }
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(adminPages));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.length() == 0  ||  line.charAt(0) == '#') {
                    continue;
                }
                line = line.trim();
                String[] fields = line.split("\t");
                if (fields.length == 1) {
                    fields = line.split("\\s+");
                    if (fields.length > 1) {
                        log.error("ADMIN Pages (" + adminPages +"): bad line: [" + line +"]: fields=" + fields.length);
                        continue;
                    }
                }
                if (!line.endsWith(".html")) {
                    adminStatus.append("ERROR: ADMIN Pages list (" + adminPages +"): No '.html': " + line);
                    continue;
                }
                //log.info("ADMIN Pages: Config added [" + line + "]");
                adminPages.add(line);
            }
        } catch (Exception e) {
            log.error("Reading adminPages (" + adminPages + "): " + e);
            throw new RuntimeException(e);
        } finally {
            if (in != null) {
                 try { in.close(); } catch (Exception ignore) {}
            }
            adminStatus.append("\n\n" +
                "== ADMIN Pages (" + adminPages + "): " +
                    String.join(",", adminPages) +
                "\n");
        }
*/
    }

    // overbusy-IP exclusion

    private static List<String[]> excludeIPs = null;

    private static class IPTime {
        String ip;
        long t;
        String first;
        IPTime(String ip, long t) {
            if (ip.startsWith("[")) {
                log.info("shortening ip, orig=" + ip);
                ip = ip.substring(1, ip.length()-1);
                ip = ip.replace(":", ".");
                log.info("shorten/retro ip, short=" + ip);
            }
            this.ip = ip;
            this.t = t;
            first = ip.substring(0, ip.indexOf('.'));
        }
    }

    private static LinkedList<IPTime> recentIPs = new LinkedList<>();

    public static String checkExcludeIP(String ip, boolean speedCheck) {

        //log.info("excludeIP checking " + ip + (speedCheck ? " w/ speed" : ""));

        if (excludeIPs != null) {

            // excludeIPs: IP prefix | reason
            for (String[] rec : excludeIPs) {
                if (ip.startsWith(rec[0])) {
                    log.warn("EXCLUDE " + ip +
                             " rule [" + rec[0] + "]: " + rec[1]);
                    return rec[1];
                }
            }
        }

        if (!speedCheck) {
            return null;
        }

        // too many from 1 major IP

        long now = System.currentTimeMillis();

        log.info("Checking IP 1st num for speed: " + ip +
                 " all recents: " + recentIPs.size() +
                 (recentIPs.size() == 0 ? "" :
                  " oldest(ms): " + (now - recentIPs.getLast().t)));
        String firstNum = "'error'";
        IPTime ipt = new IPTime(ip, now);
        try {
            recentIPs.addFirst(ipt);
            int ix = ipt.ip.indexOf('.');
            if (ix == -1) {
                log.error("No '.' in [" + ipt.ip + "]");
            } else {
                firstNum = ipt.ip.substring(0, ix);
            }
        } catch (Exception e) {
            log.error("E: " + e);
        }
//log.info("firstNum " + firstNum);

        int recent = 0;
        for (int i=0; i<recentIPs.size(); i++) {
            IPTime ipti = recentIPs.get(i);
            if (now - ipti.t > 15000) {
                break;
            }
            if (firstNum.equals(ipti.first)) {
                recent++;
            }
        }
//log.info("recent " + recent);
        if (recent > 5) { // including this
            log.warn("Too many recent from " + ip + " - rejecting");
            return "Slow down, server overheating!";
        }
        if (recent > 0) {
            log.info("recents: " + recent);
        }
        if (recentIPs.size() > 20) {
            for (int i=recentIPs.size(); i>20; i--) {
                recentIPs.removeLast();
            }
        }
        return null;
    }

    private static void initExcludeIPs() {

        log.info("initExcludeIPs:");

        String excludeFile = ConfigUtil.runtimeProperty("local.dir") +
                                                          "/excludeIP";

        File excludeList = new File(excludeFile);
        if (!excludeList.isFile()) {
            log.warn("EXCLUDE IP Init: No " + excludeFile);
            adminStatus.append("EXCLUDE IP Init: No " + excludeFile).append("\n\n");
            return;
        }

        excludeIPs = new ArrayList<>();
        BufferedReader in = null;

        try {
            in = new BufferedReader(new FileReader(excludeList));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.length() == 0  ||  line.charAt(0) == '#') {
                    continue;
                }
                line = line.trim();
                String[] fields = line.split("\t");
                if (fields.length != 2) {
                    adminStatus.append("EXCLUDE IP: bad line: " + line).append("\n");
                    continue;
                }
                if (!fields[0].contains(".")) {
                    adminStatus.append("EXCLUDE IP: No '.' in addr: " +
                                    fields[0]).append("\n");
                    continue;
                }
                if (fields[1].length() < 5) {
                    adminStatus.append("Warn: EXCLUDE IP: Exclude explanation <5: " +
                                    fields[1]).append("\n");
                }
                log.info("EXCLUDE IP: Config added [" + fields[0] + "]: " + fields[1]);
                excludeIPs.add(fields);
            }
            log.info("EXCLUDE IP: Config size: " + excludeIPs.size());
        } catch (Exception e) {
            log.error("Reading excludeIP: " + e);
            adminStatus.append("Reading excludeIP: " + e).append("\n");
            throw new RuntimeException(e);
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignore) {}
            }
        }
    }
}
