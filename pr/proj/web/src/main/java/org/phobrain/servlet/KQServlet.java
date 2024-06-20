package org.phobrain.servlet;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  KQServlet - web.xml - curate.html
 **
 */

import org.phobrain.util.ConfigUtil;
import org.phobrain.util.MiscUtil;

import org.phobrain.math.Higuchi;

import org.phobrain.db.util.DBUtil;
import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.SessionDao;
import org.phobrain.db.dao.UserDao;
import org.phobrain.db.dao.ShowingPairDao;
import org.phobrain.db.dao.BrowserDao;
import org.phobrain.db.dao.KeywordsDao;
import org.phobrain.db.dao.PictureDao;
import org.phobrain.db.dao.PictureMapDao;
import org.phobrain.db.dao.ApprovalDao;
import org.phobrain.db.dao.PairDao;
import org.phobrain.db.record.Session;
import org.phobrain.db.record.Browser;
import org.phobrain.db.record.User;
import org.phobrain.db.record.ShowingPair;
import org.phobrain.db.record.Keywords;
import org.phobrain.db.record.Picture;
import org.phobrain.db.record.PictureMap;
import org.phobrain.db.record.ApprovedPair;

import com.google.gson.Gson;

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;
import java.util.List;
import java.util.Arrays;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

import java.io.IOException;
import java.io.PrintWriter;

import java.sql.Connection;
import java.sql.SQLException;

import javax.naming.NamingException;

import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KQServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(KQServlet.class);

    protected static final ServletData data = ServletData.get();

    @Override
    public void init(ServletConfig config) throws ServletException {
/*
        graphDir = ConfigUtil.runtimeProperty("local.dir") + "/" +
                   ConfigUtil.runtimeProperty("graph.dir");
        File folder = new File(graphDir);
        if (!folder.isDirectory()) {
            log.error("CONFIG: graph.dir [" + graphDir + 
                                       "] IS NOT A DIRECTORY - EXITING");
            System.exit(1);
        }
*/
        Connection conn = null;
        try {
            conn = DaoBase.getConn();

        } catch (NamingException ne) {
            log.error("init: Naming", ne);
        } catch (SQLException sqe) {
            log.error("init: DB: " + sqe, sqe);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignore) {}
            }
        }
        log.info("KQServlet Init OK");
    }

    @Override
    public String getServletInfo() {
        return "KQServlet";
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException  {

        res.setContentType( "text" );
        PrintWriter out = res.getWriter();

        out.println("Hello\n");

        out.close();
    }

    private static class KV implements Comparable {
        String key;
        int value;
        double dbl; // so far value^x
        @Override
        public int compareTo(Object oo) {
            KV o = (KV) oo;
            if (this.value > o.value) return 1;
            if (this.value < o.value) return -1;
            return 0;
        }
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException  {

        long t1 = System.currentTimeMillis();

        String remoteHost = req.getRemoteHost();

        //log.info("KQServlet POST " + remoteHost);

        if (!ServletUtil.isAdminIP(remoteHost)) {
            String err = "NOT AN ADMIN HOST: " + remoteHost;
            log.error(err);
            res.sendError(401, err);
            return;
        }

        String sessionTag = req.getParameter("sess");
        if (sessionTag == null) {
            log.error("NO SESSION TAG");
            res.sendError(500, "Failed to generate session tag");
            return;
        }
        sessionTag = sessionTag.trim();
        log.info("session: " + sessionTag);

        Connection conn = null;
        try {
            conn = DaoBase.getConn();

            // look up session and user

            Session session = SessionDao.getSessionByTag(conn, sessionTag);
            if (session == null) {
                log.error("NO session for tag " + sessionTag);
                res.sendError(500, "Please reload web page.");
                return;
            }

            if (session.user == null) {
                log.error("NO user for session tag " + sessionTag);
                res.sendError(500, "Internal Error. Call Help.");
                return;
            }
            if (!session.user.startsWith("cu_")) {
                log.error("User not 'cu_' for session tag " + sessionTag +
                          ": " + session.user);
                res.sendError(500, "Internal Error. Call Help.");
                return;
            }
            String orient = req.getParameter("o");
            if (!"v".equals(orient)  &&  !"h".equals(orient)) {
                log.error("Bad 'o'ient for session tag " + sessionTag +
                          ": " + session.user + ": " + orient);
                res.sendError(500, "Internal Error. Call Help.");
                return;
            }
            String kstr = req.getParameter("k");
            Gson gson = new Gson();
            String[] kwds = gson.fromJson(kstr, String[].class);

            log.info("Kwds: " + kwds.length + "\n" + Arrays.toString(kwds));
            // do it
            int[] cts = ServletData.loadKwdIds(conn, 6, kwds);
            log.info("Set view 6/kwds: v " + cts[0] + " h " + cts[1]);
            int nPics = 0;
            if ("v".equals(orient)) {
                nPics = cts[0];
            } else {
                nPics = cts[1];
            }
/*
            if (session.kwdChoice == null) { // TODO - use for kwdCoder?
                log.error("NO kwd_choice for session tag " + sessionTag);
                res.sendError(500, "Internal Error. Call Help.");
                return;
            }
*/
            res.setContentType( "text/plain" );
            PrintWriter out = res.getWriter();
            out.println("OK " + nPics);
            out.close();

            t1 = System.currentTimeMillis() - t1;
            log.info("done " + sessionTag + " time " + t1);

        } catch (NamingException ne) {
            log.error("Naming", ne);
            res.sendError(500, "jndi error");
        } catch (SQLException sqe) {
            log.error("DB: " + sqe, sqe);
            res.sendError(500, "Database error");
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignore) {}
            }
        }
    }

}

