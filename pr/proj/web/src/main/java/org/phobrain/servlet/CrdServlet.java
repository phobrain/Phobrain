package org.phobrain.servlet;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  Used for disabled DNA animation.
 **
 */

import org.phobrain.util.ConfigUtil;

import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.IPDao;
import org.phobrain.db.dao.ShowingPairDao;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Date;

import com.google.gson.Gson;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.annotation.WebServlet;

import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/pr/crd")
public class CrdServlet extends HttpServlet {

    private static final int NATOMS = 95;

    private final static int CRD_LINES = (NATOMS * 3) / 10 +
                                         ((NATOMS * 3) / 10) % 2;

    private static String crdFile;
    private static String engFile;

    private static Set<String> ipSet;

    private static int requestCount = 0;

    private static Date lastRequest = null;

    private static final Logger log = LoggerFactory.getLogger(
                                                    CrdServlet.class);

    public static String getStatus() {
        try {
            Path path = Paths.get(engFile);
            List<String> lines = Files.readAllLines(path,
                                                    StandardCharsets.UTF_8);
            System.out.println("Got lines: " + lines.size());
            String molStatus = "";
            if (lines.size() > 1) {
                String line = lines.get(1).trim().replaceAll("\\s+"," ");
                String ss[] = line.split(" ");
                int ix = 4;
                while (!"TEMP(K)".equals(ss[ix])  &&  ix < ss.length) {
                    ix++;
                }
                if (ix != ss.length  &&  ss.length > ix + 2) {
                    molStatus += "&nbsp;&nbsp;&nbsp;Temp(K): " + ss[ix+2];
                }
            }
            return "Requests: " + requestCount +
                   "&nbsp;&nbsp;&nbsp;Clients: " + ipSet.size() +
                   "&nbsp;&nbsp;&nbsp;Last Request: " + lastRequest +
                   "<br>&nbsp;&nbsp;&nbsp;Molecule: " + molStatus;
        } catch (Exception e) {
            return "Exception: " + e;
        }
    }

    private boolean config = true;

    @Override
    public void init() throws ServletException {
        crdFile = ConfigUtil.runtimeProperty("crd.file");
        File f = new File(crdFile);
        if (!f.isFile()) {
            log.error("CONFIG: crd.file [" + f + "] IS NOT A FILE");
            config = false;
        }
        engFile = ConfigUtil.runtimeProperty("eng.file");
        f = new File(engFile);
        if (!f.isFile()) {
            log.error("CONFIG: eng.file [" + f + "] IS NOT A FILE");
            config = false;
        }
        if (config) {
            log.info("CrdServlet serving " + crdFile + " and " + engFile);
        } else {
            log.warn("CrdServlet serving ZILCH you know.");
        }

        ipSet = Collections.synchronizedSet(new HashSet<>());
    }

    @Override
    public String getServletInfo() {
        return "CrdServlet";
    }

    private static class Bundle {
        List<Float> crds;
        String but1Msg;
        String but2Msg;
        String but3Msg;
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException  {

        long t1 = System.currentTimeMillis();

        String remoteHost = req.getRemoteHost();

        if (!config) {
            res.sendError(500, "molecule not set up");
            return;
        }
        boolean insertIP = false;
        if (!ipSet.contains(remoteHost)) {
            // stop another thread coming thru
            // in case the db runs slow
            ipSet.add(remoteHost);
            insertIP = true;
        }
        int count = 0;
        Connection conn = null;
        try {
            conn = DaoBase.getConn();
            if (insertIP) {
                if (IPDao.insertIP(conn, remoteHost)) {
                    log.info("New IP: " + remoteHost +
                             " Served: " + requestCount +
                             " IP set size: " + ipSet.size());
                }
            }
            count = ShowingPairDao.getCount(conn);
        } catch (NamingException ne) {
            log.error("Naming [" + remoteHost + "]", ne);
        } catch (SQLException sqe) {
            log.error("DB [" + remoteHost + "]: " + sqe, sqe);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignore) {}
            }
        }

        try {
            Bundle b = new Bundle();
            b.crds = new ArrayList<>();
            Path path = Paths.get(crdFile);
            //byte[] encoded = Files.readAllBytes(Paths.get(crdFile));

            List<String> lines = null;
            while (true) {
                lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                if (lines != null  &&  lines.size() > 1+CRD_LINES) {
                    break;
                }
                log.info("got no lines: " +
                             (lines == null ? "null" : lines.size()));
                try { Thread.sleep(5); } catch (Exception ignore) {}
            }

            for (int i=1; i<1+CRD_LINES; i++) {
                String[] crds = lines.get(i).split("\\s+");
                for (String s : crds) {
                    if ("".equals(s)) {
                        continue;
                    }
                    //s = s.trim();
                    b.crds.add(Float.parseFloat(s));
                }
            }
            path = Paths.get(engFile);
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            b.but1Msg = "(Temperature)";
            b.but2Msg = "(Energy)";
            b.but3Msg = "(Time)";
            if (lines.size() > 1) {
                String line = lines.get(1).trim().replaceAll("\\s+"," ");
                String ss[] = line.split(" ");
                if (ss.length > 4  && "TIME(PS)".equals(ss[3])) {
                    if ("=".equals(ss[4])) {
                        b.but3Msg = "Time (ps): " + ss[5] +
                                        " | Clicks last 5 sec: " + count;
                    } else {
                        b.but3Msg = "Time (ps): " + ss[4].substring(1) +
                                        " | Clicks last 5 sec: " + count;
                    }
                }

                int ix = 4;
                while (!"TEMP(K)".equals(ss[ix])  &&  ix < ss.length) {
                    ix++;
                }
                if (ix != ss.length  &&  ss.length > ix + 2) {
                    b.but1Msg = "Temperature (Kelvin): " + ss[ix+2];
                }
                if (lines.size() > 2) {
                    b.but2Msg = lines.get(2).replaceAll("\\s+"," ");
                }
            }

            Gson gson = new Gson();
            String json = gson.toJson(b);
            res.setContentType( "text/plain" );
            PrintWriter out = res.getWriter();
            out.println(json);
            out.close();

        } catch (Exception e) {
            log.error("Exception " + e, e);
        }

        lastRequest = new Date();

        if (++requestCount % 1000 == 0) {
            log.info("Served: " + requestCount +
                     " IP set size: " + ipSet.size());
        }
    }
}
