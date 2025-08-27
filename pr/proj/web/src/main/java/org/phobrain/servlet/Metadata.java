package org.phobrain.servlet;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  Metadata - Serving info on pair, pics
 **
 */
import org.phobrain.util.ConfigUtil;
import org.phobrain.util.KwdUtil;
import org.phobrain.util.MiscUtil;

import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.PictureDao;
import org.phobrain.db.dao.SessionDao;
import org.phobrain.db.dao.UserDao;
import org.phobrain.db.dao.BrowserDao;
import org.phobrain.db.dao.KeywordsDao;
import org.phobrain.db.record.Session;
import org.phobrain.db.record.Browser;
import org.phobrain.db.record.User;
import org.phobrain.db.record.Keywords;
import org.phobrain.db.record.Screen;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;

import com.google.gson.Gson;

import javax.naming.NamingException;

import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Metadata extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(Metadata.class);

    @Override
    public void init(ServletConfig config) throws ServletException {

        loadArchiveOwners();

        log.info("Metadata servlet Init OK");
    }

    @Override
    public String getServletInfo() {
        return "Metadata";
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException  {

        res.setContentType( "text" );
        PrintWriter out = res.getWriter();

        out.println("Hello\n");

        out.close();
    }

    private static class MDExport {
        String credit;
        String kw;
    }

    /*
    **  loadArchiveOwners - for multiple owners
    */
    private Map<Integer, String> archiveOwners = new HashMap<>();

    private void loadArchiveOwners() {

        Set<Integer> archives = null;

        Connection conn = null;

        try {
            conn = DaoBase.getConn();

            archives = PictureDao.getArchives(conn);

            for (Integer i : archives) {
                String author = ConfigUtil.runtimeProperty("owner.archive." + i);
                if (author == null) {
                    log.error("Metadata.loadArchiveOwners: no owner for archive: " + i);
                } else {
                    archiveOwners.put(i, author);
                }
            }
        } catch (SQLException sqe) {
            log.error("Metadata.loadArchiveOwners: getting set of archives: " + sqe);
        } catch (Exception e) {
            log.error("Metadata.loadArchiveOwners: getting archive-owner: " + e);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignore) {}
            }
        }
        log.info("loadArchiveOwners: archives: " + archives.size() +
                    "  archiveOwners: " + archiveOwners.size());
    }

    private String getArchiveOwner(int archive) {

        // TODO - make runtime config setting
        //          part of an intake procedure

        String cred = archiveOwners.get(archive);
        if (cred == null) {
            return "Unknown";
        }
        return cred;
    }

    private String colorFunc(String func) {

        if (func.contains("/")) {

            String h[] = func.split("/");

            StringBuilder sb = new StringBuilder();

            sb.append("hybrid: ");
            for (String s : h) {
                sb.append(colorFunc(s)).append("/");
            }
            sb.deleteCharAt(sb.length()-1);

            return sb.toString();
        }

        if ("b0".equals(func)) {
            return "RGB";
        }
        if ("b1".equals(func)) {
            return "Lab 94";
        }
        if ("b2".equals(func)) {
            return "Lab E2k";
        }
        /** not used
        if ("b3".equals(func)) {
            return "Gist";
        }
        */
        if ("b4".equals(func)) {
            return "Greyscale";
        }
        if ("b5".equals(func)) {
            return "HueSat 24";
        }
        if ("b6".equals(func)) {
            return "HueSat 48";
        }
        if ("b7".equals(func)) {
            return "RGB 12";
        }
        if ("b8".equals(func)) {
            return "RGB 24";
        }
        if ("b9".equals(func)) {
            return "RGB 32";
        }
        if (func.startsWith("b8")) {
            return "RGB 24";
        }
        if (func.startsWith("b9")) {
            return "RGB 32";
        }
        if (func.startsWith("gry")) {
            return "Greyscale";
        }
        if (func.startsWith("hs24")) {
            func = "HueSat 24";
        }
        if (func.startsWith("hs48")) {
            func = "HueSat 48";
        }
        if (func.startsWith("gg")) {
            log.info("FUNC [" + func + "]");
            if (func.endsWith("b3b4")) {
                return "hybrid Gist/Greyscale";
            } else if (func.endsWith("b5b8")) {
                return "hybrid HueSat24/RGB24";
            }
            return "color hybrid";
        }
        if (func.startsWith("rgb1")) {
            return "RGB 32";
        }
        if (func.startsWith("dA")) {
            return "(hybrid vector)";
        }
        if ("c1".equals(func)) {
            return "Neural HS48";
        }
        if ("c2".equals(func)) {
            return "Neural RGB32";
        }
        return func;
    }

    private String parseGoldenAngle(String selMethod) {

        // selMethod starts w/ "gld/"

        String direction = "";

        if (selMethod.endsWith("in")) {
            direction = " inwards";
        } else if (selMethod.endsWith("out")) {
            direction = " outwards";
        }
        int end = selMethod.indexOf('/', "gld/".length());
        String num = selMethod.substring(4, end);

        if ("6".equals(num)) {
            return "[golden angle 2D" + direction + "]";
        }
        if ("7".equals(num)) {
            return "[golden angle 3D" + direction + "]";
        }
        if ("8".equals(num)) {
            return "[golden angle 27D" + direction + "]";
        }
        if ("9".equals(num)) {
            return "[golden angle 8D" + direction + "]";
        }
        if ("11".equals(num)) {
            return "[>120 degrees, 32K D" + direction + "]";
        }
        return "[golden angle" + direction + "]";
    }

    /*
    **  translateColorMethod - very old, mostly unused?
    */
    private String translateColorMethod(String selMethod) {

        if ("b9".equals(selMethod)) {
            return "[RGB 32]";
        }
        String dir = null;
        String func = null;
        if (selMethod.startsWith("clr/")) {
            dir = "close";
            func = selMethod.substring(4);
        } else if (selMethod.startsWith("phi/")) {
            dir = "close";
            func = selMethod.substring(4);
        } else if (selMethod.startsWith("cclr/")) {
            dir = "close";
            func = selMethod.substring(5);
        } else if (selMethod.startsWith("sopp/")) {
            dir = "close";
            func = selMethod.substring(5);
        } else if (selMethod.startsWith("sopp2/")) {
            // close-numbered pics were swapped to be in order
            if (selMethod.startsWith("opp/")) {
                dir = "far";
                func = selMethod.substring(4);
            } else if (selMethod.startsWith("copp/")) {
                dir = "far";
                func = selMethod.substring(5);
            } else if (selMethod.startsWith("clr/")) {
                dir = "close";
                func = selMethod.substring(4);
            } else if (selMethod.startsWith("cclr/")) {
                dir = "close";
                func = selMethod.substring(5);
            }
        } else if (selMethod.startsWith("opp/")) {
            dir = "far";
            func = selMethod.substring(4);
        } else if (selMethod.startsWith("copp/")) {
            dir = "far";
            func = selMethod.substring(5);
        } else if (selMethod.startsWith("minusv/")) {
            dir = "far";
            func = selMethod.substring(7);
        } else if (selMethod.startsWith("pck/dA")) {
            dir = "draw";
            func = selMethod.substring(4);
        } else if (selMethod.startsWith("pck/")) {
            dir = "close";
            func = selMethod.substring(4);
        }
        if (dir == null) {
            log.warn("No dir/ection: " + selMethod);
            return selMethod;
        }
        func = colorFunc(func);

        if (func.length() == 0) {
            return selMethod;
        }
        return dir + ": " + func;
    }

    private String vectorMethod(String selMethod) {

        String func = null;
        String type = null;
        String name = null;

        String[] ss = selMethod.split("\\.");

        if (ss.length < 3) {
            log.error("vectorMethod: expected func.[123].column: [" + selMethod +
                            "] got len " + ss.length);
            return "vector/" + selMethod;
        }

        // peek ahead for name and l->r method

        String lr_method = null;

        int ix = ss[2].indexOf('(');
        if (ix == -1) {
            name = ss[2];
        } else {
            name = ss[2].substring(0, ix);
            String rest = ss[2].substring(ix);

            if (rest.startsWith("(ll)")  ||
                rest.startsWith("(rr)")) {

                // ok

            } else if (rest.equals("(r)")) {

                if (ss.length == 6) {

                    // distance from l choice used
                    //      to weight r candidate list
                    // e.g.
                    // cos.2.vgg16_16(r).cos.2.mob_1280(l)
                    // ----------------- -----------------
                    //  normal right     func/vec against l

                    String lr_func = ss[3] + "." +
                                     ss[4] + "." +
                                     ss[5].replaceAll("\\(l\\)", "");
                    lr_method = vectorMethod(lr_func);
//log.info("LLLLLLLLLLLLLLLLLR " + lr_func + " -> " + lr_method);

                } else {

                    log.warn("vectorMethod: TODO: (r)?: " + selMethod);
                    return " vector/" + selMethod;
                }

            } else {

                log.warn("vectorMethod: unexpected: " + rest + " in " + selMethod);
                return " vector/" + selMethod;
            }
        }

        func = ss[0];
        if (!"cos".equals(func)  &&
            !"poi".equals(func)) {

            log.error("vectorMethod: expected func in [cos|poi]: " + selMethod);
            return " vector/" + selMethod;
        }

        if ("1".equals(ss[1])) {

            type = "hist";

            if (name.startsWith("hist")) {
                name = name.replace("hist", "");
            }
            if ("256".equals(name)  ||
                "histo_gss".equals(name)) {

                name = "gss";
            } else if ("1728".equals(name)  ||
                       "histo_rgb".equals(name)) {
                name = "rgb";
            } else if ("1984".equals(name)  ||
                       "histo_ml".equals(name)) {
                name = "gss_rgb";
            } else {
                log.error("vectorMethod: unexpected histogram: " + selMethod);
                return " vector/" + selMethod;
            }
        } else if ("2".equals(ss[1])) {
            type = "im_nn";
            if (name.startsWith("nnl_")  ||
                name.startsWith("vgg16_")  ||
                name.startsWith("mob_")  ||
                name.startsWith("dense_")) {

                // ok
            } else {

                log.error("vectorMethod: unexpected imagenet model code: " + selMethod);
                return " vector/" + selMethod;
            }

        } else if ("3".equals(ss[1])) {
            type = "pr_nn";
        } else {

            log.error("vectorMethod: expected type in [1|2|3]: " + selMethod);
            String stack = MiscUtil.getStack(7);
            log.warn("MISSING TYPE:\n" + stack);

            return " vector/" + selMethod;
        }

        if (lr_method == null) {
            return "[" + func + "." + type + "." + name + "]";
        }
        return "[" + func + "." + type + "." + name + "] L->R: " + lr_method;
    }

    private final String Q = " [?]";

    private String parseIt(String uitype, String selMethod) {

        // expected [12][cp][vdnm]<dim>

        if (selMethod == null  ||  selMethod.length() == 0) {
            log.error("parseIt: selMethod");
            return "missing info for uitype [" + uitype + "]";
        }

        Character cpic = selMethod.charAt(0);            // 0
        if (cpic < '0'  ||  cpic > '2') {
            return uitype + Q + " pic";
        }

        Character cfunc = selMethod.charAt(1);            // 1
        String func = null;
        switch (cfunc) {
            case 'c': func = "cos"; break;
            case 'd': func = "L2";  break;
            case 'p': func = "L2";  break;  // poi(ncare
            default: return uitype + Q + " func: " + cfunc;
        }

        Character cmodel = selMethod.charAt(2);           // 2
        String model = null;
        switch (cmodel) {
            case 'v': model = "VGG16"; break;
            case 'n': model = "NASNetLarge"; break;
            case 'm': model = "MobileNetV2"; break;
            case 'd': model = "DenseNet121"; break;
            default: return uitype + Q + " model: " + cmodel;
        }

        if (!Character.isDigit(selMethod.charAt(3))) {     // 3
            return uitype + Q + " dim";
        }
        int ix = 4;
        while (ix < selMethod.length()  &&
               Character.isDigit(selMethod.charAt(ix))) {
            ix++;
        }
        String dim = selMethod.substring(3, ix);

        return uitype + ": " + 
                 func + "(" + model + "." + dim + ")";
    }

    private String translateSelectionMethod(String selMethod) {

        // TODO - persist info on flip

        if (selMethod.equals("flip")) {
            return "[flip]";
        }

        if (selMethod.startsWith("rand")) {
            return "[random]";
        }

        // .dots not seeming helpful in UI

        selMethod = selMethod.replace("\\.dots\\.", "");
        selMethod = selMethod.replace("\\.dots", "");

        log.info("translateSelM [" + selMethod + "]");

        if (selMethod.startsWith("stk")) {

            // stroke w/ dots

            return parseIt("stroke", selMethod.substring(3));

        } else if (selMethod.startsWith("ends")) {

            // using ends of vector search list,
            //  initially for few dots

            return parseIt("ends", selMethod.substring(4));

        } else if (selMethod.startsWith("wig")) {

            // dots considered 'wiggly' since no easy def

            return parseIt("free", selMethod.substring(3));

        } else if (selMethod.startsWith("gIN")) {

            // short, horizontal case of gLR, gRL
            //   - with no directional distinction yet

            int ix = selMethod.indexOf('.');
            if (ix == -1) {
                return "[vector/" + selMethod + "]";
            }
            String rest = selMethod.substring(ix+1);

            return "scale-in: " + vectorMethod(rest);
        }

        if (selMethod.startsWith("gLR")  ||  selMethod.startsWith("gRL")) {

            // gesture == line l->r or r->l

            int ix = selMethod.indexOf('.');
            if (ix == -1) {
                return "[vector/" + selMethod + "]";
            }
            String rest = selMethod.substring(ix+1);

            return selMethod.substring(0, ix) + "." + vectorMethod(rest);
        }

        if (selMethod.equals("rand/bests")) {
            // user has labeled pairs as good
            return "[random+curated]";
        }
        if (selMethod.startsWith("rand/firsts")) {
            // user has labeled pairs as good
            return "[random+curated]";
        }

        if (selMethod.startsWith("d0")  ||
            selMethod.startsWith("+/a_d0")) {

            // pair-ml: user has labeled buncha pairs good/bad,
            //      then training/predictions ->
            //           pr.pairs_[vh].d0 column.

            return "[avg/pair-ml]";
        }
        if (selMethod.startsWith("-d0")  ||
            selMethod.startsWith("-/d0") ||
            selMethod.startsWith("-/a_d0")) {

            return "[worst avg/pair-ml]";
        }

        if (selMethod.startsWith("cos.")  ||
            selMethod.startsWith("poi.")) {

            return vectorMethod(selMethod);
        }

        // pair ml (user trained)

        if (selMethod.startsWith("P/")  ||
            selMethod.startsWith("Px/")) {

            // Px is criss-cross; called handles it

            int ix = selMethod.indexOf('|');
            if (ix == -1) {
                log.warn("P/Px/:  expected '|': " + selMethod);

                return "[" + selMethod + "]";
            } else {
                return "[pairml: " + selMethod.substring(
                                            selMethod.indexOf('/')+1, ix)
                         + "]";
            }
        }

        if (selMethod.startsWith("gld/")) {

            return parseGoldenAngle(selMethod);
        }

        // TODO - are the rest still used?

        if (selMethod.startsWith("pls/clr")) {
            return "[color]";
        }
        if (selMethod.startsWith("app/")  ||
            selMethod.startsWith("prc/")  ||
            selMethod.equals("prk")) {

            return "[curated]";
        }

        log.warn("Falling back to mostly-old translateColorMethod()");

        return translateColorMethod(selMethod);
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException  {

        long t1 = System.currentTimeMillis();

        String remoteHost = req.getRemoteHost();

        log.info("Metadata POST " + remoteHost);

        String sessionTag = req.getParameter("sess");
        if (sessionTag == null) {
            log.error("NO SESSION TAG");
            res.sendError(500, "Failed to generate session tag");
            return;
        }
        sessionTag = sessionTag.trim();
        //log.info("session: " + sessionTag);

        // insert a control function
        String pr = req.getParameter("pr");
        if (pr != null  &&  "none".equals(pr)) {
            pr = null;
        }

        // back to keywords / metadata

        String p1 = req.getParameter("p1");
        String p2 = req.getParameter("p2");
        if (pr == null  &&
            (p1 == null  ||  "none".equals(p1)  ||
             p2 == null  ||  "none".equals(p2))) {

            log.error("Missing P's (" + pr + "): [" + p1 + "][" + p2 + "]");
            res.sendError(500, "Internal error");
            return;
        }

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
            if (!session.user.endsWith("ooser")  &&
                !session.user.startsWith("cu_")) {
                log.error("User not '*ooser' for session tag " + sessionTag +
                          ": " + session.user);
                res.sendError(500, "Internal Error. Call Help.");
                return;
            }

            if (pr != null) {
                String reply = "OK";
                if ("true".equals(pr)) {
                    if (session.repeatPics) {
                        log.info("do nothing/true");
                    } else {
                        SessionDao.toggleRepeatPics(conn, session);
                        GetEngine.clearBrowserSeen(session.browserID);
                    }
                } else if ("false".equals(pr)) {
                    if (session.repeatPics) {
                        SessionDao.toggleRepeatPics(conn, session);
                    } else {
                        log.info("do nothing/false");
                    }
                } else if ("status".equals(pr)) {
                    reply = "" + session.repeatPics;
                } else {
                    log.error("Unpexpected 'pr' value: " + pr);
                    res.sendError(500, "Internal Error. Call Help.");
                    return;
                }
                log.info("PR: [" + pr + "] reply [" + reply + "]");
                res.setContentType( "text" );
                PrintWriter out = res.getWriter();
                out.println(reply);
                out.close();
                return;
            }

            Screen s1 = null;
            Screen s2 = null;

            boolean doColor = false;  // long-rare so not maintained

            // P1,P2 are 1.1 and 2.1 unless user has
            //      flipped one or both screens to prev
            log.info("P1 " + p1 + " P2 " + p2);

            int screen1, showing1, screen2, showing2;

            try {
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

            } catch (Exception e) {
                log.error("Parsing p's: " + e);
                res.sendError(500, "Internal error");
                return;
            }
            if (screen1 != 1  ||  screen2 != 2  ||
                showing1 < 0  ||  showing1 > 1  ||
                showing2 < 0  ||  showing2 > 1) {

                log.error("OOBounds p's: " + p1 + " " + p2 + " ");
                res.sendError(500, "Internal error");
                return;
            }

            s1 = session.getScreen(screen1, showing1);
            s2 = session.getScreen(screen2, showing2);

            log.info("PR sc1 " + screen1 + " sh1 " + showing1 + " " + s1.id_s +
                       " sc2 " + screen2 + " sh2 " + showing2 + " " + s2.id_s);
            if (s1.time == null  ||  s2.time == null) {
                log.error("NULL utime/s");
            }

            boolean swapped = false;
            if (s1.time == null) {
                log.warn("s1 time null");
            }
            if (s1.time != null  &&  s1.time.after(s2.time)) {
                log.info("Swapping screens for time");
                Screen t = s1;
                s1 = s2;
                s2 = t;
                swapped = true;
            }

            if (doColor) {

                if (showing1 != showing2) { // different depth

                    doColor = false;

                } else if (s1.selMethod == null  ||  s2.selMethod == null) {

                    log.warn("Null sel method [" + s1.selMethod +
                                        "][" + s2.selMethod  + "]");
                    doColor = false;
                }
            }

            log.info("MD " + s1.id_s + " " + s1.selMethod +
                             "  " +
                             s2.id_s + " " + s2.selMethod +
                             " doColor " + doColor);

            String credit = null;
            if ("x".equals(p1)) {
                credit = "Photo: " + getArchiveOwner(s1.archive);
            } else {
                // 2 screens
                if (s1.archive == s2.archive) {
                    credit = "Photos: " + getArchiveOwner(s2.archive);
                } else {
                    String c1 = getArchiveOwner(s1.archive);
                    String c2 = getArchiveOwner(s2.archive);

                    if (c1.equals(c2)) {
                        credit = "Photos: " + c1;
                    } else if (swapped) {
                        credit = "Photos: " + c2 + " / " + c1;
                    } else {
                        credit = "Photos: " + c1 + " / " + c2;
                    }
                }
            }

            boolean crossed = false;
            if ((s1.selMethod != null  &&  s1.selMethod.contains("Px/"))  ||
                (s2.selMethod != null  &&  s2.selMethod.contains("Px/"))) {

                crossed = true;
            }

            // pairing method

            StringBuilder sb = new StringBuilder();

            if (s1.selMethod != null  &&  s2.selMethod != null) {

                String s1m = translateSelectionMethod(s1.selMethod);
                String s2m = translateSelectionMethod(s2.selMethod);

                // extract overlap, if any

                StringBuilder same = new StringBuilder();
                for (int i=0;
                         i<s1m.length()  &&
                         i<s2m.length();
                         i++) {

                    if (s1m.charAt(i) != s2m.charAt(i)) {
                        break;
                    }
                    same.append(s1m.charAt(i));
                }

                if (same.length() == s1m.length()) {

                    sb.append(same);

                } else if (same.length() > 0) {

                    // cleaner punctuation

                    int len = same.length();

                    s1m = s1m.substring(len).replace(")", "");
                    s2m = s2m.substring(len).replace(")", "");

                    String common = same.toString()
                                        .replace(")", "");

                    sb.append(common).append(": ")
                      .append(s1m).append(" || ")
                      .append(s2m);

log.info("\nXXXXXXXXX \n"+
"s1 " + s1.selMethod + "\n" +
"s2 " + s2.selMethod + "\n" +
"--> same: " + same.toString() + "\n" +
"==>   sb: " + sb.toString());

                } else {

                    // no common prefix

log.info("MMMMMMMMMMM " +s1m + " " + s2m +"] MMMMMMMMMM");

                    sb.append(s1m).append(" || ")
                      .append(s2m);
                }

            } else if (s2.selMethod != null) {

                // TODO - this assumes pics chosen in 1,2 order

                sb.append("method: ")
                  .append(translateSelectionMethod(s2.selMethod));
            }

/*
            KEYWORDS CODE
            // HACK/TODO - put kwdCoder and view in Session
            Keywords k1 = KeywordsDao.getKeywordsByIdCoder(conn,
                                                                s1.id_s, "m");
            Keywords k2 = KeywordsDao.getKeywordsByIdCoder(conn,
                                                                s2.id_s, "m");

            if (k1 != null  &&  k2 != null) {

                Set<String> set = new HashSet<>();
                Set<String> geomset = new HashSet<>();
                String ka[] = k1.keywords.split(" ");
                for (String kwd : ka) {
                    if (KwdUtil.stripGeom(kwd) == null) {
                        geomset.add(kwd);
                    } else {
                        set.add(kwd);
                    }
                }
                ka = k2.keywords.split(" ");

                boolean addBreak = sb.length() > 0;

                // nogeom
                for (String kwd : ka) {
                    if (KwdUtil.stripGeom(kwd) != null) {
                        if (set.contains(kwd)) {
                            if (addBreak) {
                                sb.append("/ ");
                                addBreak = false;
                            }
                            sb.append(kwd).append(" ");
                        }
                    }
                }
                // geom
                int ct = 0;
                for (String kwd : ka) {
                    if (KwdUtil.stripGeom(kwd) == null) {
                        if (geomset.contains(kwd)) {
                            if (addBreak) {
                                sb.append("/ ");
                                addBreak = false;
                            }
                            if (ct++ == 0) {
                                sb.append("[");
                            }
                            sb.append(kwd).append(" ");
                        }
                    }
                }
                if (ct > 0) {
                    sb.replace(sb.length()-1, sb.length(), "] ");
                }
            }

            if (sb.length() == 0) {
                // TODO - public desc for the pairing method used
                if ("b9".equals(s1.selMethod)) {
                    sb.append("fallback: close RGB 32 ");
                } else if (s1.selMethod.startsWith("c/")) {
                    sb.append("fallback: ")
                      .append(colorFunc(s1.selMethod.substring(2)));
                } else if (s0.selMethod.startsWith("c/")) {
                    sb.append("fallback: ")
                      .append(colorFunc(s0.selMethod.substring(2)));
                } else if (s1.selMethod.startsWith("->seq")) {
                    sb.append("fallback: linear search ");
                } else {
                    //log.info("No kwds for " +
                        // s0.selMethod + " " + s1.selMethod);
                    sb.append("[no keywords] ");
                }
            }
*/

            // final esthetic / hacky cleanup
            String method = sb.toString()
                                // before contractions
                              .replaceAll("poi\\.", "L2 ")
                              .replaceAll("cos\\.", "Cos ")
                              .replaceAll("hist\\.", "")
                              .replaceAll("im_nn.", "")
                              //.replaceAll(" 1\\.", " ")
                              //.replaceAll(" 2\\.", " ")
                              //.replaceAll(" 3\\.", " ")
                              .replaceAll("\\._", ".");
            if (crossed) {
                method = "(crossed) " + method;
            }

            MDExport ret = new MDExport();
            ret.credit = credit;
            ret.kw = method;

            String response = new Gson().toJson(ret);

            //log.info("Translated? " + sb + " -> [ " + response + " ]");

            res.setContentType( "application/json" );
            PrintWriter out = res.getWriter();
            out.println(response);
            out.close();

        } catch (NamingException ne) {
            log.error("Naming", ne);
            res.sendError(500, "jndi error");
        } catch (SQLException sqe) {
            log.error("DB: " + sqe, sqe);
            res.sendError(500, "K/Database error");
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignore) {}
            }
        }
    }

}

