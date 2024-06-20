package org.phobrain.servlet;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  Status - web.xml=status - mju.html (CHANGE FOR PRIVACY)
 **
 */

import org.phobrain.util.ConfigUtil;

import org.phobrain.db.record.Browser;
import org.phobrain.db.record.ShowingPair;
import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.ShowingPairDao;
import org.phobrain.db.dao.BrowserDao;
import org.phobrain.db.dao.BIPDao;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;

import com.google.gson.Gson;

import java.util.List;
import java.util.ArrayList;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.annotation.WebServlet;

import javax.naming.NamingException;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/pr/mnb")
public class Status extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(Status.class);

    @Override
    public void init() throws ServletException {
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException  {

        String page = "mju.html"; // TODO something sensible
        String host = req.getRemoteHost();

        if (ServletUtil.failAdminPage(page, host)) {
            log.error("Admin page w/o ip: " + page + " / " + host);
            throw new IllegalArgumentException("Admin page w/o ip");
        }

        String s = req.getParameter("sha1");
        if (s != null  &&  s.length() == 0) {
            s = null;
        }
        String statusPwd = ConfigUtil.runtimeProperty("status.pwd");
        if (s != null  &&  statusPwd != null  &&  s.equals(statusPwd)) {
            // ok
        } else if (s == null  &&  statusPwd == null) {
            // also ok
        } else {
            // whoa
            log.error("BAD TAG: [" + s + "] pwd [" + statusPwd + "]");
            res.sendError(500, "Failed to generate session tag");
            return;
        }

        // enter the db

        Connection conn = null;
        try {
            conn = DaoBase.getConn();

            Bundle bundle = new Bundle();

            bundle.molReport = CrdServlet.getStatus();

            List<Browser> browsers = BrowserDao.getBrowsers(conn);

            for (Browser b : browsers) {
                List<ShowingPair> show = ShowingPairDao.getAllShowings(conn, 
                                                                      b.id);
                if (show.size() > 0) {
                    String lastIP = null;
                    try {
                        lastIP = BIPDao.getLastIP(conn, b.id);
                    } catch (Exception sx) {
                        // orig db
                        if (b.id < 36) {
                            lastIP = "not recorded";
                        } else if (b.id < 219) {
                            int i = b.version.lastIndexOf("%");
                            lastIP = b.version.substring(i+1).trim();
                        }
                    }
                    bundle.browsers.add(browserReport(b, lastIP, show).toJSON());
                }
            }
            Gson gson = new Gson();
            String json = gson.toJson(bundle);
            res.setContentType( "application/json" );
            PrintWriter out = res.getWriter();
            out.println(json);
            out.close();
        } catch (NamingException ne) {
            log.error("report: Naming", ne);
        } catch (SQLException sqe) {
            log.error("report: DB: " + sqe, sqe);
        } catch (Exception e) {
            log.error("report: misc: " + e, e);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignore) {}
            }
        }
    }


    private BrowserReport browserReport(Browser b, String lastIP,
                                        List<ShowingPair> show) {

        BrowserReport br = new BrowserReport();

        br.id = "<span title='" + b.version + "'>" + b.id + "</span>";
        //br.version = b.version;

        int i = b.version.indexOf("k=");
        if (i != -1) {
            
            String kwdCoder = b.version.substring(i+2, i+3);
            if (!"blm0".contains(kwdCoder)) {
                log.error("Bad kwdCoder: "+ kwdCoder);
            }
            if (b.version.indexOf("mooser ") == -1) {
                br.id += "/" + kwdCoder;
            } else { // uppercase mult screens
                br.id += "/" + kwdCoder.toUpperCase();
            }
        }
        i = b.version.indexOf("v=");
        if (i != -1) {
            String view = b.version.substring(i+2, i+3);
            br.id += "." + view;
        }

        br.ip = lastIP;

        br.first = show.get(0).createTime;
        br.last = show.get(show.size()-1).createTime;

        br.longestBreak = 0;
        br.breaks = 0;
        ShowingPair prev = null;
        int cts[] = new int[20];
        for (ShowingPair s : show) {
            if (s.rating == -1  || s.rating > cts.length-1) {
                cts[0]++;
            } else {
                cts[s.rating]++;
            }
            if (prev != null) {
                long dt = s.createTime.getTime() - prev.createTime.getTime();
                if (dt > br.longestBreak) {
                    br.longestBreak = dt;
                }
                if (dt > 20000) {
                    br.breaks++;
                }
            }
            prev = s;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(show.size()).append(": ");
        for (i=0; i<cts.length; i++) {
            if (cts[i] > 0) {
                sb.append(i).append(".").append(cts[i]).append(" ");
            }
        }
        br.counts = sb.toString();
        return br;
    }

    private static class Bundle {
        String molReport;
        List<String[]> browsers = new ArrayList<>();
    }

    private static class BrowserReport {

        final private static SimpleDateFormat format = 
                         new SimpleDateFormat("MM-dd-yyy HH:mm:ss");

        public String      id;
        public String      version;
        public String      ip;
        public String      counts;
        public Timestamp   first;
        public Timestamp   last;
        public int         breaks; // > 20 min
        public long        longestBreak;

        // TODO - showing stats

        public String[] toJSON() {

            if ("[0:0:0:0:0:0:0:1]".equals(ip)) {
                ip = "Localhost";
            }
            long s = longestBreak / 1000; // total sec
            long sec = s % 60;  // after mins
            long min = ( s / 60 ) % 60;  // after hrs
            long hrs = s / (60*60);

            return new String[] {
                id, counts, format.format(first), format.format(last),
                "" + breaks, String.format("%d:%02d:%02d", hrs,min,sec), ip
            };
        }
    }
}
