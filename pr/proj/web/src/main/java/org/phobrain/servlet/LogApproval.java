package org.phobrain.servlet;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  LogApproval - web.xml=logap - curate.html, mnbpair.html, others?
 **
 */

import org.phobrain.util.ID;
import org.phobrain.util.HashCount;

import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.SessionDao;
import org.phobrain.db.dao.UserDao;
import org.phobrain.db.dao.UniquePairDao;
import org.phobrain.db.dao.ShowingPairDao;
import org.phobrain.db.dao.BrowserDao;
import org.phobrain.db.dao.PairDao;
import org.phobrain.db.dao.ApprovalDao;
import org.phobrain.db.dao.KeywordsDao;
import org.phobrain.db.dao.PictureDao;
import org.phobrain.db.record.Session;
import org.phobrain.db.record.Screen;
import org.phobrain.db.record.Browser;
import org.phobrain.db.record.User;
import org.phobrain.db.record.ShowingPair;
import org.phobrain.db.record.Picture;
import org.phobrain.db.record.ApprovedPair;
import org.phobrain.db.record.Keywords;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;

import java.io.IOException;
import java.io.PrintWriter;

import javax.naming.NamingException;

import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogApproval extends HttpServlet {

    private static final Logger log = 
                         LoggerFactory.getLogger(LogApproval.class);

    @Override
    public void init(ServletConfig config) throws ServletException {

        log.info("LogApproval Init OK");
    }

    @Override
    public String getServletInfo() {
        return "LogApproval";
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException  {

        res.setContentType( "text" );
        PrintWriter out = res.getWriter();

        out.println("Hello\n");

        out.close();
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException  {

        long t1 = System.currentTimeMillis();

        String remoteHost = req.getRemoteHost();

        log.info("LogApproval POST " + remoteHost);

        if (!ServletUtil.isAdminIP(remoteHost)) {
            String err = "NOT AN ADMIN HOST: " + remoteHost;
            log.error(err);
            res.sendError(403, err);
            return;
        }

        String sessionTag = req.getParameter("sess");
        if (sessionTag == null) {
            log.error("NO SESSION TAG");
            res.sendError(500, "Failed to generate session tag");
            return;
        }
        sessionTag = sessionTag.trim();
        //log.info("session: " + sessionTag);

        String p1 = req.getParameter("p1");
        String p2 = req.getParameter("p2");
        String st = req.getParameter("st");
        if (p1 == null  ||  "none".equals(p1)  ||  
            p2 == null  ||  "none".equals(p2)  ||
            st == null  ||  "none".equals(st)) {

            log.error("Missing P's/status [" + p1 + "][" + p2 + "] [" + st + "]");
            res.sendError(500, "Internal error");
            return;
        }

        int status = 0;
        st = st.trim();
        if (st.startsWith("extrap")) {
            if (!"extrapQ".equals(st)  &&  !"extrapD".equals(st)) {
                log.error("Parsing status: " + st + ": no Q or D");
                res.sendError(500, "Internal error");
                return;
            }
            status = -99;
        } else {
            try {
                status = Integer.parseInt(st.trim());
            } catch (NumberFormatException nfe) {
                log.error("Parsing status: " + st + ": " + nfe);
                res.sendError(500, "Internal error");
                return;
            }
        }

        //log.info("GOT " + p1 + " " + p1 + " " + status + " sessionTag " + sessionTag);

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
            if (!session.user.startsWith("cu_")  &&
                !"ripplay".equals(session.user)) {
                log.error("User not 'cu_'/'ripplay' for session tag " + 
                          sessionTag + ": " + session.user);
                res.sendError(500, "Internal Error. Call Help.");
                return;
            }

            String userName = session.user;
            if (userName.startsWith("cu_")) {
                userName = userName.substring(3);
            }

            String id1, id2;
            if (p1.startsWith("_")  &&  p2.startsWith("_")) {
                id1 = p1.substring(1);
                id2 = p2.substring(1);
            } else {
                //log.info("else: p1/p2: " + p1 + "/" + p2);
                int screen1, showing1, screen2, showing2;
                try {
                    p1 = p1.trim();
                    String ss[] = p1.split("\\.");
                    if (ss == null  ||  ss.length != 2) {
                        log.error("Missing . [" + p1 + "] [" + p2 + "]");
                        res.sendError(500, "Internal error");
                        return;
                    }
                    screen1 = Integer.parseInt(ss[0]);
                    showing1 = Integer.parseInt(ss[1]);
                    p2 = p2.trim();
                    ss = p2.split("\\.");
                    if (ss == null  ||  ss.length != 2) {
                        log.error("Missing . (1) [" + p1 + "] [" + p2 + "]");
                        res.sendError(500, "Internal error");
                        return;
                    }
                    screen2 = Integer.parseInt(ss[0]);
                    showing2 = Integer.parseInt(ss[1]);
                    log.info("-- screens: " + screen1 + "/" + screen2);
                } catch (Exception e) {
                    log.error("Parsing p's: " + e);
                    res.sendError(500, "Internal error");
                    return;
                }
                if (screen1 < 1  ||  screen2 > 2  ||  
                    showing1 < 0  ||  showing2 > 1) {

                    log.error("OOBounds p's: " + p1 + " " + p2 + " ");
                    res.sendError(500, "Internal error");
                    return;
                }
                //log.info("-- screens 2nd: " + screen1 + "/" + screen2);

                Screen s1 = session.getScreen(screen1, showing1);

                if (s1 == null) {
                    log.error("No Screen id/showing: " + screen1 + "/" + showing1);
                    res.sendError(500, "Internal error");
                    return;
                }
                Screen s2 = session.getScreen(screen2, showing2);
                if (s2 == null) {
                    log.error("No screen '2': using Screen id/showing: " + 
                                        screen2 + "/" + showing2);
                    res.sendError(500, "Internal error");
                    return;
                }

                log.info("Screens: userName: " + userName + 
                        " sessionId/0 " + session.id + "/" + s1.sessionId +
                        " browser_ids: " + s1.browserId + "/" + s2.browserId);

                id1 = s1.id_s;
                id2 = s2.id_s;
            }
         
            String retMsg = null;

            switch (status) {
                case 10:

                    retMsg = insertPair(conn, userName, session, 0, id1, id2);

                    if (retMsg != null  &&  retMsg.startsWith("Verticals")) {
                        res.sendError(500, "Verticals !=");
                        return;
                    }

                    break;

                case 11:
                    ApprovedPair ap = ApprovalDao.getApprovedPair(conn, 
                                                                  id1, id2);
                    if (ap == null) {
                        retMsg = insertPair(conn, userName, session, 2, 
                                                  id1, id2);

                        if (retMsg != null  &&  
                            retMsg.startsWith("Verticals")) {

                            res.sendError(500, "Verticals !=");
                            return;
                        }
                    } else {
                        log.info("LOG Update " + id1 + " " + id2 + 
                                 " status " + ap.status + " -> 2");
                        retMsg = ApprovalDao.update(conn, userName, 
                                                          2, id1, id2);
                    }

                    break;

                case -99:
                    log.info("LOG extrapolate " + userName + " " +
                              session.browserID + " " + 
                              id1 + " " + id2);

                    Set<String> k1set = new HashSet<>();

                    String k1 = KeywordsDao.getKeywordsByIdCoder(conn,
                                              id1, "m").keywords;
                    String ss[] = k1.split(" ");
                    for (String s : ss) {
//log.info("k0 " + s);
                        k1set.add(s);
                    }
                    log.info("Set1 kwds: " + k1set.size());

                    List<String> kwds = new ArrayList<>();

                    String k2 = KeywordsDao.getKeywordsByIdCoder(conn,
                                              id2, "m").keywords;
                    ss = k2.split(" ");
                    log.info("Set2 kwds: " + ss.length);
                    for (String s : ss) {
//log.info("k2 " + s);
                        if (k1set.contains(s)) {
                            kwds.add(s);
                        }
                    }
                    if (kwds.size() == 0) {
                        retMsg = "No kwds in common";
                        break;
                    }
                    StringBuilder sb = new StringBuilder();
                    boolean doit = st.endsWith("D");
                    if (kwds.size() > 0) {
                        sb.append("**** Keywords: ").append(
                                            String.join(" + ", kwds))
                          .append('\n');
                        procPairs(conn, sb, doit, session, userName, kwds);
                    }
                    if (sb.length() > 0) {
                        if (doit) {
                            retMsg = "DONE\n" + sb.toString();
                        } else {
                            retMsg = sb.toString();
                        }
                    }

                    break;


                default:
                    log.info("LOG update " + userName + " " +
                              session.browserID + " " + 
                              id1 + " " + id2 + " -> " + status);
                    retMsg = ApprovalDao.update(conn, userName, status, 
                                                        id1, id2);
                    if (retMsg != null  &&  status == 1  &&
                        retMsg.startsWith("No update: ")) {
                        log.info("Re-adding for presumed bulk delete " +
                                 "while in mnbpair");
                        retMsg = insertPair(conn, userName, session, 4, 
                                                        id1, id2);
                        if (retMsg != null) {
                            retMsg = "Was deleted. Reinsert err: " + retMsg;
                        } else {
                            retMsg = ApprovalDao.update(conn, userName, status, 
                                                        id1, id2);
                        }
                    }
                    break;
            } 

            res.setContentType( "text" );
            PrintWriter out = res.getWriter();
            if (retMsg != null) {
                out.println(retMsg);
                log.info("RET:\n" + retMsg);
            } else {
                out.println("logged: " + id1 + " " + id2);
            }
            out.close();

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

    private String approve(Connection conn, ApprovedPair ap, 
                                                  // boolean hasKwd, 
                                                  Picture pic1, Picture pic2)
            throws SQLException {

        //ap.hasKwd = hasKwd;

        ap.vertical = pic1.vertical;
        if (!PairDao.hasD0(ap.vertical ? "v" : "h")) {

            ap.d0 = -999;

        } else {

            try {
                ap.d0 = PairDao.getVal(conn, ap.id1, ap.id2,
                                     (ap.vertical ? "v" : "h"),
                                     "a_d0");
            } catch (SQLException sqe) {

                ap.d0 = -999; // will get fixed eventually by proj/update
                log.error("approve: using " + ap.d0 + " for d0 (" + sqe + ")", sqe);
            }
        }

        ap.matchPeople = pic1.people == pic2.people;

        ap.r = (pic1.r + pic2.r) / 2;
        ap.g = (pic1.g + pic2.g) / 2;
        ap.b = (pic1.b + pic2.b) / 2;
        ap.ll = (pic1.ll + pic2.ll) / 2;

        ap.rgbRadius = (pic1.rgbRadius + pic2.rgbRadius) / 2;
        ap.labContrast = (pic1.labContrast + pic2.labContrast) / 2;
        //ap.fracDim = (int) ((pic1.fracDim + pic2.fracDim) / 2); // bunk?
        ap.density = (pic1.density + pic2.density) / 2;

        return ApprovalDao.insert(conn, true, // global no dupes
                                        ap);
    }

    private void procPairs(Connection conn, StringBuilder sb, boolean doit,
                                            Session session, String userName,
                                            List<String> kwds) 
            throws SQLException {

        Set verticals = PictureDao.getPicsBool(conn, "vertical", true);

        int maxKwdLen = 0;
        for (String kwd : kwds) {
            if (kwd.length() > maxKwdLen) {
                maxKwdLen = kwd.length();
            }
        }

        // get all ids with all the kwds in common between current pair

        HashCount counts = new HashCount();
        Map<String, List> kwdMap = new HashMap<>();
        for (String kwd : kwds) {
            List<String> ids = KeywordsDao.getIdsCoderKwd(conn, "m", kwd);
            for (String id : ids) {
                counts.add(id);
            }
        }
        int all = kwds.size();
        List<String> ids = new ArrayList<>();
        for (String id: counts.keySet()) {
            if (counts.getCount(id) == all) {
                ids.add(id);
            }
        }

        // pair 'em up

        int count = 0;  // meat
        int rejected = 0; // gristle with meat: seen, not accepted
        int seen = 0;
        int approved = 0; // includes criss-cross pairs not 'seen'/served
        int approved_now = 0;

        // for doit
        HashCount collisions = null;
        if (doit) {
            collisions = new HashCount();
        }
        ApprovedPair ap = new ApprovedPair("x", "y"); // placeholder id1, id2
        ap.browserID = session.browserID;
        ap.curator = userName;
        ap.status = 4; // bulk approve
            
        for (int i=0; i<ids.size(); i++) {
            String id1 = ids.get(i);
            Picture pic1 = null;
            if (doit) {
                pic1 = PictureDao.getPictureById(conn, id1);
            }
            boolean vertical = verticals.contains(id1);

            for (int j=i+1; j<ids.size(); j++) {
                String id2 = ids.get(j);
                if (vertical != verticals.contains(id2)) {
                    continue;
                }

                // id1, id2

                boolean isSeen = UniquePairDao.seenPair(conn, id1, id2);
                if (isSeen) {
                    seen++;
                }

                if (ApprovalDao.any(conn, id1, id2)) {
                    approved++;
                } else {
                    count++;
                    if (isSeen) {
                        rejected++;
                    } else if (doit) {

                        Picture pic2 = PictureDao.getPictureById(conn, id2);
                        if (pic1.vertical != pic2.vertical) {
                            log.error("verticals not =: " + id1 + " " + id2);
                        } else {

                            ap.id1 = id1;
                            ap.id2 = id2;
                            ap.idP = ap.id1 + "|" + ap.id2;

                            String s = approve(conn, ap, // true, 
                                                     pic1, pic2);
                            if (s == null) {
                                approved_now++;
                            } else {
                                collisions.add(s);
                            }
                        }
                    }
                }

                // now the other way

                isSeen = UniquePairDao.seenPair(conn, id2, id1);
                if (isSeen) {
                    seen++;
                }   
                if (ApprovalDao.any(conn, id2, id1)) {
                    approved++;
                } else {
                    count++;
                    if (isSeen) {
                        rejected++;
                    } else if (doit) {


                        Picture pic2 = PictureDao.getPictureById(conn, id2);
                        if (pic1.vertical != pic2.vertical) {
                            log.error("verticals not =: " + id2 + " " + id1);
                        } else {

                            ap.id1 = id2;
                            ap.id2 = id1;
                            ap.idP = ap.id1 + "|" + ap.id2;

                            String s = approve(conn, ap, // true, 
                                                     pic2, pic1);
                            if (s == null) {
                                approved_now++;
                            } else {
                                collisions.add(s); // shouldn't happen
                            }
                        }
                    }
                }
            }
        }
        //log.info("kwd " + kwd + " " + count + " " + seen);
        if (doit) {
            if (sb.length() == 0) {
                sb.append("log:\n");
            }
            sb.append("Newly approved: ").append(approved_now).append('\n');
            if (collisions.size() > 0) {
                  log.error("Prev approved by other: " + 
                                  collisions.toString());
            }
        } else {
            if (sb.length() == 0) {
                sb.append("Summary\n");
            }
            sb.append("ready:  ").append(count).append('\n');
            sb.append("== history of this").append(
                         (kwds.size() == 1 ? " keyword" :
                                             " set of keywords")
                         ).append(":\n")
              .append("rejected: ").append((100 * rejected) / seen)
                                   .append("%\n")
              .append("seen:     ").append(seen).append('\n')
              .append("rejected: ").append(rejected).append('\n')
              .append("approved: ").append(approved).append('\n');
        }
    }

    private String insertPair(Connection conn, 
                              String userName, Session session,
                              int status, String id1, String id2)
            throws SQLException {

        if (id1 == null  ||  id2 == null) {
            String msg = "insertPair: null id: " + id1 + "/" + id2;
            log.error(msg);
            return msg;
        }

        log.info("LOG insert: " + userName + " " + session.browserID + 
                                 " status " + status + " " +
                                 id1 + " " + id2);

        Picture pic1 = PictureDao.getPictureById(conn, id1); // TODO mv to dao
        Picture pic2 = PictureDao.getPictureById(conn, id2); // TODO mv to dao

        if (pic1.vertical != pic2.vertical) {
            String msg = "Verticals !=: " + pic1.fileName + " " + pic2.fileName;
            log.error(msg);
            return msg;
        }

/*
        boolean hasKwd = false;

        try {
            hasKwd = PairDao.hasKwd(conn, id1, id2, 
                                            (pic1.vertical ? "v" : "h"));
        } catch (SQLException sqe) {
            String msg = "Getting hasKwd: " + 
                            "[" + id1 + " " + id2 + "]: " +
                            sqe;
            log.error(msg);
            // kwds not impt for now.. return msg;
        }
*/

        ApprovedPair ap = new ApprovedPair(id1, id2);
        ap.browserID = session.browserID;
        ap.curator = userName;
        ap.status = status;
        return approve(conn, ap, // hasKwd, 
                             pic1, pic2);
    }

}

