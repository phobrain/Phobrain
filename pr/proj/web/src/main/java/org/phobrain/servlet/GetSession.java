package org.phobrain.servlet;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  GetSession - establish Session for continuity/nonrepetition of pics.
 **     With constant identity (e.g. across devices) would become a 
 **     login service.
 **
 */

import org.phobrain.util.ConfigUtil;
import org.phobrain.util.AtomSpec;

import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.BrowserDao;
import org.phobrain.db.dao.BIPDao;
import org.phobrain.db.dao.SessionDao;
import org.phobrain.db.dao.ShowingPairDao;
import org.phobrain.db.record.User;
import org.phobrain.db.record.Browser;
import org.phobrain.db.record.Screen;
import org.phobrain.db.record.Session;
import org.phobrain.db.record.Picture;
import org.phobrain.db.record.ShowingPair;
import org.phobrain.db.record.PictureResponse;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import java.io.IOException;
import java.io.PrintWriter;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetSession extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(GetSession.class);


    //private static HashSet<String> users = null;

/*
    private static String userFile = null;

    private static Set<String> loadUsers() {
        Set<String> ret = new HashSet<>();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(userFile));
            for (String s = null; (s = in.readLine()) != null;) {
                s = s.trim();
                if (s.length() == 0) {
                    continue;
                }
                if (s.startsWith("#")) {
                    continue;
                }
                ret.add(s);
            }
            return ret;
        } catch (Exception e) {
            throw new RuntimeException("Reading users from " + userFile + ": " +
                                        e);
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignore) {}
            }
        }
    }
*/

    private static final GetEngine engine = GetEngine.getEngine();

    @Override
    public void init (ServletConfig config) throws ServletException {
        //userFile = ConfigUtil.getConfigProperty("users.file");
        log.warn("Doing a fake checkExcludeIP to init:");
        ServletUtil.checkExcludeIP("82.80.1.1", true);
        log.info("GetSession Init OK");
    }

    @Override
    public String getServletInfo() {
        return "GetSession";
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException  {

        long t0 = System.currentTimeMillis();

        String remoteHost = req.getRemoteHost();
        String remoteAddr = req.getRemoteAddr();

        if (!"0:0:0:0:0:0:0:1".equals(remoteAddr)) {
            String excludeReason = ServletUtil.checkExcludeIP(remoteAddr, true);
            if (excludeReason != null) {
                log.warn("EXCLUDE " + remoteAddr);
                res.sendError(429, excludeReason);
                return;
            }
        }

        String SEP = " | ";
        log.info("GetSession POST " + remoteHost + SEP + remoteAddr + SEP +
            "hour=" + req.getParameter("hour") + SEP +
            "tzoff=" + req.getParameter("tzoff") + SEP +
            "lang=" + req.getParameter("lang") + SEP +
            "platform=" + req.getParameter("platform") + SEP +
            "browser=" + req.getParameter("browser") + SEP +
            "version=" + req.getParameter("version") + SEP +
            "username=" + req.getParameter("username") + SEP +
            "session=" + req.getParameter("session") + SEP +
            "cmd=" + req.getParameter("cmd") + SEP +
            "new=" + req.getParameter("new"));

        String userName = req.getParameter("username");
        String sessionTag = req.getParameter("session");
        if (sessionTag != null) {
            sessionTag = sessionTag.trim();
        }
        if (userName == null) {
            // bad hackery on the other end
            log.error("Login denied: username null: [" + 
                                    userName + "] " + remoteAddr);
            res.sendError(400, "Unrecognized protocol");
            return;
        }

        if (!"eooser".equals(userName)) {
            log.info("admin request, user=" + userName);
            if (!ServletUtil.isAdminIP(remoteHost)) {
                String err = "NOT AN ADMIN HOST: " + remoteHost;
                log.error("admin? user " + userName + ": " + err);
                res.sendError(403, err);
                return;
            }
        }

        if (!userName.equals("fooser")  &&  
            !userName.equals("eooser")  && 
            !userName.startsWith("cu_")  && 
            !userName.equals("mooser")  && 
            !userName.equals("ripplay") && 
            !userName.equals("moober") && 
            !userName.equals("kwooser")) {
            log.error("Login denied: (need cu_ ?) [" + userName + "] " + remoteAddr);
            res.sendError(400, "Unrecognized protocol");
            return;
        }

        if (sessionTag == null) {
            // bad hackery on the other end
            log.error("Login denied: [" + userName + "] session null " + 
                       remoteAddr);
            res.sendError(400, "Unrecognized protocol");
            return;
        }
        
        if ("ripplay".equals(userName)  &&  
            !"ripplay".equals(sessionTag)) {
             log.error("Login denied: [" + userName + "] session [" + 
                       sessionTag + "] " + remoteAddr);
             res.sendError(400, "Unrecognized protocol");
             return;
        }

        String origUser = userName;
        String kwdChoice = null;
        int i = userName.indexOf('_');
        if (i != -1) {
            kwdChoice = userName.substring(i+1);
            //userName = userName.substring(0, i);
            log.info("User [" + userName + "] kwd [" + kwdChoice + "]");
        }

        String kwdCoder = req.getParameter("k");
        if (kwdCoder == null  ||  "undefined".equals(kwdCoder)) {
            kwdCoder = "m"; // merged kwds
            log.info("OLD_PAGE defaulting 'm': [" + userName + "] session [" + 
                       sessionTag + "] " + remoteAddr);
        }
        if ("l".equals(kwdCoder)  ||  "b".equals(kwdCoder)) {
            kwdCoder = "m";
        }
        if (!"m".equals(kwdCoder)  &&  !"0".equals(kwdCoder)) {
            log.error("Login denied: [" + userName + "] session [" + 
                       sessionTag + "] " + remoteAddr + 
                       " - wrong k=[" + kwdCoder + "]");
            res.sendError(500, "Please go to main page and return.");
            return;
        }

        String view = req.getParameter("v");
        if (view == null  ||  "undefined".equals(view)) {
            view = "0";
        }
        int viewNum;
        try {
            viewNum = Integer.parseInt(view);
            if (viewNum < 0  ||  viewNum > ConfigUtil.MAX_VIEW) {
                log.error("Login denied: [" + userName + "] session [" + 
                           sessionTag + "] " + remoteAddr + 
                           " - wrong v=[" + view + "]");
                res.sendError(400, "Unrecognized protocol");
                return;
            }
        } catch (NumberFormatException nfe) {
            log.error("Login denied: [" + userName + "] session [" + 
                           sessionTag + "] " + remoteAddr + 
                           " - not an int v=[" + view + "]");
            res.sendError(400, "Unrecognized protocol");
            return;
        }

        String cmd = req.getParameter("cmd");
        
        String baseVersion = origUser + " % " + 
                                req.getParameter("browser") + 
                                " % " + req.getParameter("version");

        String sessionBrowserVersion = baseVersion +
                                " % " + remoteAddr +
                                " % k=" + kwdCoder +
                                " % v=" + view;
        // primal HACK
        if (sessionBrowserVersion.length() > 510) {
            sessionBrowserVersion = req.getParameter("version") +
                                " % " + remoteAddr +
                                " % k=" + kwdCoder +
                                " % v=" + view;
            if (sessionBrowserVersion.length() > 510) {
                sessionBrowserVersion = sessionBrowserVersion.substring(
                                       sessionBrowserVersion.length() - 510);
            }
        }
        if (cmd != null) {
            if ("undefined".equals(cmd)) {
                cmd = null;
            } else {
                if (!"kwd".equals(cmd)) {
                    log.error("ILLEGAL CMD [" + cmd + "] " + remoteAddr);
                    res.sendError(400, "Unrecognized protocol");
                    return;
                }
                sessionBrowserVersion += " % " + cmd;
            }
        }

        String nw = req.getParameter("new");
        if (nw != null  &&  !"undefined".equals(nw)) {
            if (!"x".equals(nw)) {
                log.error("ILLEGAL 'new' (not 'x') [" + nw + "] " + remoteAddr);
                res.sendError(400, "Unrecognized protocol");
                return;
            }
            sessionBrowserVersion += " | N." + 
                            (System.currentTimeMillis() % 100000);
        }


        Connection conn = null;
        try {
            conn = DaoBase.getConn();
    
            Browser browser = null;

            if ("fooser".equals(userName)  ||  
                "eooser".equals(userName)  ||
                userName.startsWith("cu_") ||
                "kwooser".equals(userName) ||
                "moober".equals(userName)  ||
                "mooser".equals(userName)) {
                if ("none".equals(sessionTag)) {
log.info("ST none");
                    // see if it's a legacy user, or one that lost its cookie

                    List<Browser> lb = BrowserDao.getBrowsersByVersion(conn, 
                                                                   baseVersion);
                    for (Browser b : lb) {
                        if (b.version.equals(sessionBrowserVersion)) {
                            browser = b; // let it be the most recent
                        }
                    }
                    if (browser != null) {
                        log.info("Mapping browser " + browser.id + " " + 
                                 browser.version + ": " + browser.sessionTag);
                        if (browser.sessionTag == null) {
                            BrowserDao.updateLegacyTag(conn, browser);
                        }
                    }

                    if (browser == null) {

                        // first time

                        browser = BrowserDao.insertBrowser(conn, 
                                                           sessionBrowserVersion);
                        BIPDao.insertIP(conn, browser.id, remoteAddr);
                     }

                } else {

                    // it's a return browser, real or faked

                    browser = BrowserDao.getBrowserBySession(conn, sessionTag);
                    if (browser == null  &&  sessionTag.endsWith("_")) {
                        log.warn("No browser for sessionTag, relaxing _: " +
                                  sessionTag);
                        browser = BrowserDao.getBrowserBySession(conn,
                            sessionTag.substring(0, sessionTag.length()-1));
                    }
                    if (browser == null) {
                        // total fake
                        List<Browser> l = BrowserDao.getBrowsersByVersion(
                                                           conn, baseVersion);
                        log.error("UNKNOWN SESSION [" + sessionTag + "]: " +
                                  sessionBrowserVersion + " previous cases: " +
                                  l.size());
                        res.sendError(400, "Unrecognized protocol");
                        return;
                    }

                    // see if cookie was migrated to a new version
                    boolean strikeOne = false;
                    if (!browser.version.equals(baseVersion)) {
                        if ("x".equals(nw)) {
                            // new session
                            long oldId = browser.id;
                            browser = BrowserDao.insertBrowser(conn, 
                                                           sessionBrowserVersion);
                            log.info("BROWSER CONVERT " + oldId + " => " +
                                         browser.id);
                            
                        } else {
                            // printed ip's may differ too, somewhat spuriously
                            log.warn("BROWSER " + browser.id + 
                                     " VERSION CHANGED: [" + 
                                     browser.version + "] to [" + 
                                     sessionBrowserVersion + "]");
                            strikeOne = true;
                        }
                    }

                    // latest IP

                    String prevAddr = BIPDao.getLastIP(conn, browser.id);
                    boolean update = false;
                    if (prevAddr == null) {
                        log.error("INTERNAL: no last b_ip for browser " + 
                                                   browser.id);
                        update = true;
                    } else if (!prevAddr.equals(remoteAddr)) {
                        if (strikeOne) {
                            log.warn("BOTH version and latest IP changed: " +
                                     prevAddr + " -> " + remoteAddr);
                        } else {
                            log.info("BROWSER " + browser.id + " IP changed: " +
                                     prevAddr + " -> " + remoteAddr);
                        }
                        update = true;
                    }
                    if (update) {
                        BIPDao.insertIP(conn, browser.id, remoteAddr);
                    }

                }

            } else { // ripplay
                String s = req.getParameter("browser");
log.info("BROWSER " + s);
                if (!s.startsWith("bz")  &&  !s.startsWith("kd")  &&
                    !s.startsWith("vi")  &&  !s.startsWith("pr")) {
                    log.error("Login denied: [" + s + "] no 'pr|bz|kd|vi'");
                    res.sendError(400, "Unrecognized protocol");
                    return;
                }
                if (s.startsWith("bz")) {
                    s = s.substring(2);
                    browser = new Browser();
                    browser.id = Integer.parseInt(s);
                    browser.sessionTag = "ripplay_" + s;
                    kwdChoice = "R";
                } else if (s.startsWith("kd")) {
                    // signal kwd choice
                    kwdChoice = s.substring(2);
                    browser = new Browser();
                    browser.id = -1L; // experiment
                    browser.sessionTag = "ripplay_" + s;
                } else if (s.startsWith("pr")) {
                    kwdChoice = "P" + s.substring(2);
                    browser = new Browser();
                    browser.id = -1L; // experiment
                    browser.sessionTag = "ripplay_" + s;
                } else {
                    // signal kwd choice
                    kwdChoice = "V" + s.substring(2);
                    browser = new Browser();
                    browser.id = -1L; // experiment
                    browser.sessionTag = "ripplay_" + s;
                }
            }
            if (kwdChoice == null  &&  cmd != null) {
                kwdChoice = cmd;
            }
            long sid = SessionDao.getSessionIdByTag(conn, browser.sessionTag);
            if (sid == -1  ||  browser.sessionTag.startsWith("ripplay_")) {

                log.info("sid " + sid + " tag " + browser.sessionTag);

                List<Screen> screens = null;
                String screenCode = req.getParameter("c");
// legacy for mexpt.html
String m = req.getParameter("M");
if (m != null) {
screenCode = "1";
}
                if (screenCode == null  ||  "undefined".equals(screenCode)) {
                    if (!browser.sessionTag.startsWith("ripplay_")) {
                        log.info("adding placeholder screens for future tiling");
                        screens = new ArrayList<>();
                        screens.add(
                         new Screen(browser.id, 1, "v", null, null));
                        screens.add(
                         new Screen(browser.id, 2, "v", null, null));
                    }
                    
                } else {
                    String orient = null;
                    if ("1".equals(screenCode)) {
                        orient = "v";
                    } else if ("2".equals(screenCode)  ||
                               "3".equals(screenCode)) {
                        orient = "h";
                    }
                    log.info("init screens for parallel load by browser screen code [" + screenCode + "] orient " + orient);

                    // make partial Session for getScreens
                    // TODO - worry about setting session.curator?
                    Session session = new Session();
                    session.browserID = browser.id;
                    session.repeatPics = true; // superstition
                    screens = engine.getScreens(conn, 
                                                    viewNum, orient, kwdCoder,
                                                    session,
                                                    /* option: */ -2, null,
                                                    2, null, 
                                                    /* screenIds */ 1, 2, 
                                                    -1, null);
                    if (screens == null) {
                        log.error("engine.getScreens null");
                    }

                    PictureResponse pr1 = (PictureResponse) screens.get(0).pr;
                    PictureResponse pr2 = (PictureResponse) screens.get(1).pr;
                    ShowingPair sp = new ShowingPair();
                    sp.browserID = browser.id;
                    sp.callCount = -1;
                    sp.orderInSession = 1;
                    sp.vertical = pr1.p.vertical;
                    sp.id1 = pr1.p.id;
                    sp.archive1 = pr1.p.archive;
                    sp.fileName1 = pr1.p.fileName;
                    sp.selMethod1 = pr1.method;
                    sp.id2 = pr2.p.id;
                    sp.archive2 = pr2.p.archive;
                    sp.fileName2 = pr2.p.fileName;
                    sp.selMethod2 = pr2.method;
                    sp.bigStime = (int) (System.currentTimeMillis() - t0);
                    sp.atomImpact = AtomSpec.NO_ATOM;

                    ShowingPairDao.insertShowingPair(conn, sp);
                    screens.get(0).showingId = sp.id;
                    screens.get(1).showingId = sp.id;

                    engine.addSeen(conn, browser.id, sp.id1, orient);
                    engine.addSeen(conn, browser.id, sp.id2, orient);
                }
            
                // relic class
                User u = new User(userName, browser.id, remoteAddr); 
                SessionDao.insertSessionIf(conn, u, browser.sessionTag, 
                          Integer.parseInt(req.getParameter("hour")),
                          Integer.parseInt(req.getParameter("tzoff")),
                          req.getParameter("lang"),
                          req.getParameter("platform"), kwdChoice, screens);
            }

            log.info("browser " + browser.id + " tag " + browser.sessionTag + 
                      " " + remoteAddr);

            res.setContentType( "text/plain" );
            PrintWriter out = res.getWriter();
            out.println(browser.sessionTag);
            out.close();

        } catch (NamingException ne) {
            log.error("Naming", ne);
            res.sendError(500, "jndi error");
        } catch (SQLException sqe) {
            log.error("DB: " + sqe, sqe);
            res.sendError(500, "Database tables may be loading, try again in a few minutes to hours.");
        } catch (Exception e) {
            log.error("Exception: " + e, e);
            res.sendError(500, "Generic problem.");
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignore) {}
            }
        }
    }

}

