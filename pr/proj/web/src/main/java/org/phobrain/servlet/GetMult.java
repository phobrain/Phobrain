package org.phobrain.servlet;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  GetMult - web interface for GetEngine.
 **     Potentially get more than pairs,
 **     e.g. number of walls in room.
 **
 */

import org.phobrain.util.ConfigUtil;
import org.phobrain.util.MathUtil;
import org.phobrain.util.MiscUtil;
import org.phobrain.util.FileRec;
import org.phobrain.util.MiscUtil.SeenIds;
import org.phobrain.util.HashCount;
import org.phobrain.util.AtomSpec;

import org.phobrain.db.record.DotHistory;

import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.SessionDao;
import org.phobrain.db.dao.BrowserDao;
import org.phobrain.db.dao.ShowingPairDao;
import org.phobrain.db.dao.PictureDao;
import org.phobrain.db.dao.PictureMapDao;
import org.phobrain.db.dao.PairDao;
import org.phobrain.db.dao.UniquePairDao;
import org.phobrain.db.dao.ApprovalDao;
import org.phobrain.db.record.Session;
import org.phobrain.db.record.Screen;
import org.phobrain.db.record.Browser;
import org.phobrain.db.record.ShowingPair;
import org.phobrain.db.record.Picture;
import org.phobrain.db.record.PictureMap;
import org.phobrain.db.record.ApprovedPair;
import org.phobrain.db.record.PictureResponse;
import org.phobrain.db.util.DBUtil;

import java.util.Random;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Vector;
import java.util.Collections;
import java.util.Properties;

import java.io.IOException;
import java.io.File;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.BufferedReader;

import java.sql.Connection;
import java.sql.Timestamp;
import java.sql.SQLException;

import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;

import java.net.URLEncoder;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.naming.NamingException;

import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletOutputStream;
import javax.servlet.AsyncContext;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//@WebServlet(urlPatterns={"/getmext"}, asyncSupported=true)
public class GetMult extends HttpServlet {

    private double TAP_MAX = 0.0;
/*
    for ref when working on old molecular simulation tie-in
    private enum AtomSpec {
        NO_ATOM(0),
        C_O2N3N4(1),
        C_O2N3(2),
        C_N3N4(3),
        A_N1N6N7(4),
        A_N1N6(5),
        A_N6N7(6),
        T_O2N3(7),
        T_N3O4(8);
        private int value;
        private AtomSpec(int value) {
            this.value = value;
        }
    }
*/

    public static final int RATING_SCHEME = -1; // not used.. yet

    private static final Logger log = LoggerFactory.getLogger(GetMult.class);

    private static final Random rand = new Random();

    private static final GetEngine engine = GetEngine.getEngine();

    private static String IMAGE_DIR;
    private static boolean WEBP = false;  // on load, replace dbase pr..picture.fileName
                                          //    ending in .jpg with .webp
                                          // set true for IMAGE_DIR.endsWith("_webp")
                                          //    31G images
                                          //    23G images_webp
                                          // Not needed if
                                          //    both db and IMAGE_DIR have .webp files

    private static String WEB_DIR;
    private static String GRAPH_DIR;
    private static String PULSE_FILE;
    private static String TAP_DIR;

    private static Map<Long, List<String>> browserLast =
                              Collections.synchronizedMap(new HashMap<>());

    private static String statusDir = null;


    @Override
    public void init(ServletConfig config) throws ServletException {

        final boolean FIX_APPROVED_KWD = false;
        final boolean FIX_VERTICAL = false;
        final boolean FIX_COLORS = false;
        //final boolean DUMP_DEEP = false;

        // final boolean UPDATE_PIC_D0 -> moved to proj/update/

        final boolean UPDATE_AP_D0 = false;

        final boolean ADD_FLIPPED_STUFF = false;

        final boolean ADD_LOST_STUFF = false;

        try {

            WEB_DIR = ConfigUtil.runtimeProperty("web.home.dir");
            if (!(new File(WEB_DIR)).isDirectory()) {
                throw new RuntimeException("WEB_DIR not a dir: " + WEB_DIR);
            }

            statusDir = ConfigUtil.runtimeProperty("status.dir");
            if (statusDir == null) {
                log.error("No status.dir");
            }
            IMAGE_DIR = ConfigUtil.runtimeProperty("images.dir");

            if (IMAGE_DIR.endsWith("_webp")) {
                WEBP = true;
            }

        } catch (Exception e) {
            log.error("init: misc: " + e, e);
        }
        log.info("GetMult Init OK,  IMAGE_DIR=" + IMAGE_DIR);
    }

    @Override
    public String getServletInfo() {
        return "GetMult";
    }

    private static final String ETAG_HEADER = "W/\"%s-%s\"";
    private static final long DEFAULT_EXPIRE_TIME_IN_MILLIS =
                                   TimeUnit.SECONDS.toMillis(20);
    private static final String CONTENT_DISPOSITION_HEADER =
                             "inline;filename=\"%1$s\"; filename*=UTF-8''%1$s";

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException  {

        processRequest(request, response, true);
    }

    private void sendFile(final HttpServletRequest request,
                          final HttpServletResponse response,
                          final File file)
            throws ServletException, IOException  {

        String addr = myRemoteHost(request);

        //log.info("Sending " + file.getAbsolutePath() + " to " + addr);

        String fakefname = URLEncoder.encode("resize_" +
                                             rand.nextInt(99) + "_" +
                                             rand.nextInt(20) + ".jpg");
        // cacheing

        long lastModified = System.currentTimeMillis() -
                             rand.nextLong() % DEFAULT_EXPIRE_TIME_IN_MILLIS;
        String eTag = String.format(ETAG_HEADER, fakefname, lastModified);
        response.setHeader("ETag", eTag);
        response.setDateHeader("Last-Modified", lastModified);
        response.setHeader("Cache-Control", "private, max-age: 2");
        // DON'T DO THIS (safari) response.setHeader("Pragma", "no-cache"); // HTTP 1.0
        response.setDateHeader("Expires", System.currentTimeMillis() +
                                     DEFAULT_EXPIRE_TIME_IN_MILLIS);

        // content

        if (file.getName().endsWith(".webp")) {
            response.setHeader("Content-Type", "image/webp");
        } else { // TODO check all?
            response.setHeader("Content-Type", "image/jpeg");
        }
        response.setHeader("Content-Disposition",
                      String.format(CONTENT_DISPOSITION_HEADER, fakefname));
        response.setHeader("Content-Length", String.valueOf(file.length()));

        writeContent(request, response, file);
    }

    public static final int DEFAULT_STREAM_BUFFER_SIZE = 102400;

    private void writeContent(final HttpServletRequest request,
                              final HttpServletResponse response,
                              final File file)
            throws IOException {
        try (
/*
            final InputStream in = new FileInputStream(file);
            final AsyncContext async = request.startAsync();
            final ServletOutputStream out = response.getOutputStream();
            final stdDataStream sd = new StandardDataStream(in,async,out);
            out.setWriteListener(sd);
*/
            ReadableByteChannel inputChannel = Channels.newChannel(
                                                new FileInputStream(file));
            WritableByteChannel outputChannel = Channels.newChannel(
                                                response.getOutputStream());
        ) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(
                                                DEFAULT_STREAM_BUFFER_SIZE);
            long size = 0;

            while (inputChannel.read(buffer) != -1) {
                buffer.flip();
                size += outputChannel.write(buffer);
                buffer.clear();
            }
            response.getOutputStream().flush();
            outputChannel.close();

        }

    }


    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException  {

        processRequest(request, response, false);
    }

    private int getInt(HttpServletRequest request, String param, String sessionTag)
            throws Exception {

        String str = request.getParameter(param);
        if (str == null) {
            log.error("NO '" + param + "' in request, sessionTag " +
                                                        sessionTag);
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

    private final class StandardDataStream implements WriteListener {
        private final InputStream content;
        private final AsyncContext async;
        private final ServletOutputStream out;

        private StandardDataStream(InputStream content,
                                AsyncContext async, ServletOutputStream out) {
            this.content = content;
            this.async = async;
            this.out = out;
        }

        public void onWritePossible() throws IOException {
            byte[] buffer = new byte[4096];

            // while we are able to write without blocking
            while(out.isReady()) {
                // read some content into the copy buffer
                int len=content.read(buffer);

                // If we are at EOF then complete
                if (len < 0) {
                    async.complete();
                    return;
                }

                // write out the copy buffer.
                out.write(buffer,0,len);
            }
        }

        public void onError(Throwable t) {
            getServletContext().log("Async Error",t);
            async.complete();
        }
    }

    private void processRequest(HttpServletRequest request,
                                HttpServletResponse response,
                                boolean get)
            throws ServletException, IOException  {

        try {

            processRequest2(request, response, get);

        } catch (ServletException se) {
            log.error(se.getMessage(), se);
            response.sendError(500, "Server error");
            return;
        } catch (IOException ioe) {
            log.error(ioe.getMessage(), ioe);
            response.sendError(500, "Server error");
            return;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            response.sendError(500, "Server error");
            return;
        }
    }

    private String myRemoteHost(HttpServletRequest request) {
        String s = request.getRemoteHost();
        if ("[0:0:0:0:0:0:0:1]".equals(s)) {
            return "[localhost]";
        }
        return s;
    }
    private void processRequest2(HttpServletRequest request,
                                HttpServletResponse response,
                                boolean get)
            throws ServletException, IOException  {

        Timestamp rateTime = new Timestamp(System.currentTimeMillis());
        long t1 = System.currentTimeMillis();

        String remoteHost = myRemoteHost(request);

        if (!"[localhost]".equals(remoteHost)) {
            log.info("GetMult " + (get? "GET " : "POST ") + remoteHost);
        }

        String sessionTag = request.getParameter("sess");
        if (sessionTag == null  ||  "none".equals(sessionTag)) {
            log.error("NO SESSION TAG " + remoteHost);
            response.sendError(500, "Failed to generate session tag");
            return;
        }
        sessionTag = sessionTag.trim();

        String cmd = request.getParameter("c");
        if (cmd == null  ||  "none".equals(cmd)) {
            log.error("NO CMD");
            response.sendError(500, "Failed to generate session tag");
            return;
        }

        String screen1 = request.getParameter("sc1");
        String screen2 = request.getParameter("sc2");
        //log.info("Parsing screenIds (sc1,2): "  + screen1 + "," + screen2);

        int screenId1 = -1;
        if (screen1 != null  &&  !"none".equals(screen1)) {
            try {
                screenId1 = Integer.parseInt(screen1);
            } catch (Exception e) {
                log.error("Handling per-screen: " + screen1 + " " + e);
                response.sendError(500, "Failed to generate session tag");
                return;
            }
        }
        int screenId2 = -1;
        if (screen2 != null  &&  !"none".equals(screen2)) {
            try {
                screenId2 = Integer.parseInt(screen2);
            } catch (Exception e) {
                log.error("Handling per-screen: " + screen2 + " " + e);
                response.sendError(500, "Failed to generate session tag");
                return;
            }
        }
        //log.info("screenIds: " + screenId1 + " " + screenId2);
        if (screenId1 == 0  &&  screenId2 == 0) {
            log.error("Both screenIds==0");
        }

        String clickLoc = request.getParameter("loc");
        if (clickLoc == null  ||  "none".equals(clickLoc)) {
            log.error("NO LOC sessionTag " + sessionTag);
            response.sendError(500, "Failed to generate session tag");
            return;
        }
        if (!"na".equals(clickLoc)) {
            int len = clickLoc.length();
            if (len > 3  ||
                "LR".indexOf(clickLoc.charAt(0)) == -1  ||
                "TB".indexOf(clickLoc.charAt(1)) == -1  ||
                (len == 3  &&  clickLoc.charAt(2) != 'c')) {

                log.error("UNKNOWN LOC [" + clickLoc + "] sessionTag " +
                                                          sessionTag);
                response.sendError(500, "Failed to generate session tag");
                return;
            }
        }
        String clickLocSpec = request.getParameter("locspec");
        if (clickLocSpec == null  ||  "none".equals(clickLocSpec)) {
            log.error("NO LOCSPEC sessionTag " + sessionTag);
            response.sendError(500, "Failed to generate session tag");
            return;
        }

        int[] locspec = null;

        if (!"na".equals(clickLocSpec)) {

            String locspecStr = request.getParameter("locspec");
            if (locspecStr == null  ||  "none".equals(locspecStr)) {
                log.error("Bad locspec: " + locspecStr);
                response.sendError(400,
                        "Failed to generate session tag - please refresh page");
                return;
            }
log.info("TT " + locspecStr);
            if (!"na".equals(locspecStr)  &&
                !"unk".equals(locspecStr)  &&
                !MiscUtil.NULL_BASE64.equals(locspecStr)) {

                try {
                    locspec = MiscUtil.base64ToIntArray(locspecStr);
log.info("TT " + Arrays.toString(locspec));
                } catch (Exception e) {
                    log.error("to-int'ing locspec: " + e);
                    locspec = null;
                }
            }
        }

        String dotHistory = request.getParameter("dh");

        if ("none".equals(dotHistory)) {
            log.info("no dot history");
        } else if (!"0".equals(dotHistory)  &&  !"l".equals(cmd)) {
            log.info("DH dotHistory: " + dotHistory.length());
            //log.info("DH " + dotHistory);
            //log.info("DH2 " + Arrays.toString(MiscUtil.parseIntList(dotHistory)));
        }


/*
        String toggleSides = request.getParameter("tgs"); // togside
        if (toggleSides == null  ||  "none".equals(toggleSides)) {
            log.error("NO TOGSIDE sessionTag " + sessionTag);
            toggleSides = MiscUtil.NULL_BASE64;
        }
*/
String toggleSides = "tm";
String toggleTStr = "tmp";

        int r; /* rating, originally */
        String cmdMod = null; // for latent space overlay, cos2 etc

        String str = request.getParameter("r");
        if (str == null) {
            response.sendError(400, "Failed to generate session tag - please refresh page");
            return;
        }
        String[] cmp = str.split("_");
        if (cmp.length < 1  ||  cmp.length > 2) {
            log.error("Unexpected rating split: " + str);
            response.sendError(400, "Failed to generate session tag - please refresh page");
            return;
        }
        if (cmp.length == 2  &&  !"none".equals(cmp[1])) {
            //  vgg16-2 -> vgg16_2
            cmdMod = cmp[1].replaceAll("-", "_");
        }
//else log.warn("WWWWWWWWWWWWWWW  " + str);
        try {
            r = Integer.parseInt(cmp[0]);
        } catch (Exception e) {
            log.error("Unexpected rating parse: " + str + " " + e);
            response.sendError(400, "Failed to generate session tag - please refresh page");
            return;
        }

        int viewTime;
        int viewTime2;
        int clickTime; // drawing dots
        int watchDotsTime;
        int mouseDownTime;
        int mouseDist;
        int mouseDist2;
        int mouseDx;
        int mouseDy;
        int mouseVecx;
        int mouseVecy;
        int mouseMaxv;
        int mouseMaxa;
        int mouseMina;
        int mouseMaxj;
        int lastBig;
        int loadTime;
        int dotCount;
        int dotDist;
        int dotVecLen;
        int dotVecAng;
        int dotMaxVel;
        int dotMaxAccel;
        int dotMaxJerk;
        int pixInPic;
        int pixOutPic;
        int callCount;
        int nToggleLast;

        try {

            callCount = getInt(request, "ct", sessionTag);
            viewTime = getInt(request, "t", sessionTag);
            viewTime2 = getInt(request, "t2", sessionTag);
            watchDotsTime = getInt(request, "w", sessionTag);
            mouseDownTime = getInt(request, "mt", sessionTag);
            mouseDist = getInt(request, "d", sessionTag);
            mouseDist2 = getInt(request, "d2", sessionTag);
            mouseDx = getInt(request, "dx", sessionTag);
            mouseDy = getInt(request, "dy", sessionTag);
            mouseVecx = getInt(request, "vcx", sessionTag);
            mouseVecy = getInt(request, "vcy", sessionTag);
            mouseMaxv = getInt(request, "mxv", sessionTag);
            mouseMaxa = getInt(request, "mxa", sessionTag);
            mouseMina = getInt(request, "mna", sessionTag);
            mouseMaxj = getInt(request, "mxj", sessionTag);
            clickTime = getInt(request, "ctm", sessionTag);
            loadTime = getInt(request, "ltm", sessionTag);
            pixInPic = getInt(request, "pp", sessionTag);
            pixOutPic = getInt(request, "po", sessionTag);

            dotCount = getInt(request, "dc", sessionTag);
            dotDist = getInt(request, "dd", sessionTag);
            dotVecLen = getInt(request, "dv", sessionTag);
            dotVecAng = getInt(request, "da", sessionTag);

            dotMaxVel = getInt(request, "dmv", sessionTag);
            dotMaxAccel = getInt(request, "dma", sessionTag);
            dotMaxJerk = getInt(request, "dmj", sessionTag);

            nToggleLast = getInt(request, "ntog", sessionTag);
        } catch (Exception e) {
            response.sendError(400, "Failed to generate session tag - please refresh page");
            return;
        }
        // be forgiving
        try {
            lastBig = getInt(request, "btm", sessionTag);
        } catch (Exception e) {
            lastBig = -1;
        }
/*
        String toggleTStr = request.getParameter("togt");
        if (toggleTStr == null  ||  "none".equals(toggleTStr)) {
            log.error("Bad toggleTStr: " + toggleTStr);
            response.sendError(400,
                        "Failed to generate session tag - please refresh page");
            return;
        }
log.info("TT " + toggleTStr);
try {
if (!MiscUtil.NULL_BASE64.equals(toggleTStr)) {
log.info("TT " + Arrays.toString(MiscUtil.base64ToIntArray(toggleTStr)));
}
} catch (Exception e) {
log.error("ToggleTimes: " + e);
toggleTStr = null;
}
*/

        if (clickTime < -1) {
            log.warn("CLICKTIME < -1 [" + clickTime + "] sessionTag " +
                     sessionTag);
            clickTime = -2;
        }
        if (loadTime < -1) {
            log.warn("LOADTIME < -1 [" + loadTime + "] sessionTag " +
                     sessionTag);
            loadTime = -2;
        }

        //log.info("session: " + sessionTag + " rating: " + rating);

        Connection conn = null;
        try {
            conn = DaoBase.getConn();

            // look up session and user

            Session session = SessionDao.getSessionByTag(conn, sessionTag);
            if (session == null  &&  sessionTag.endsWith("_")) {
                log.warn("Relaxing sessionTag _: " + sessionTag);
                session = SessionDao.getSessionByTag(conn,
                              sessionTag.substring(0, sessionTag.length()-1));
            }
            if (session == null) {
                log.error("NO session for tag " + sessionTag);
                response.sendError(500, "Please reload web page.");
                return;
            }
//log.info("SESSION " + session.id + " kwdChoice=" + session.kwdChoice);
            if (session.user == null) {
                log.error("NO user for session tag " + sessionTag);
                response.sendError(500, "Internal Error. Call Help.");
                return;
            }
            long sessionID = session.id;
            long browserID = session.browserID;
            if (session.user.startsWith("cu_")) {
                // admin function - curate.html
                if (!ServletUtil.isAdminIP(remoteHost)) {
                    String err = "NOT AN ADMIN HOST: " + remoteHost;
                    log.error(err);
                    response.sendError(403, err);
                    return;
                }
                session.curator = session.user.substring(3);
            }
            if (session.screens.size() == 0) {
                log.error("No session screens browser " + browserID);
                response.sendError(500, "Internal Error. Call Help.");
                return;
            }
            if (session.screens.size() != 4) {
                log.error("session screens != 4 browser " + browserID +": " +
                             session.screens.size());
            }
            //log.info("session screens " + session.screens.size());
            // session browser includes keyword if any, and kwdCoder

            Browser br = BrowserDao.getBrowserById(conn, browserID);
            int i = br.version.indexOf("k=");
            String kwdCoder = br.version.substring(i+2, i+3);
            if ("l".equals(kwdCoder)  ||  "b".equals(kwdCoder)) {
                kwdCoder = "m";
            }
            if (!"m".equals(kwdCoder)  &&  !"0".equals(kwdCoder) ) {
                log.error("Bad kwdCoder for tag [" + sessionTag + "]: "+
                          kwdCoder);
                response.sendError(500, "Please go to main page and return.");
                return;
            }
            // view=0 is 1..n aka 'all' config
            i = br.version.indexOf("v=");
            String view = br.version.substring(i+2, i+3);
            int viewNum;
            try {
                viewNum = Integer.parseInt(view);
            } catch (NumberFormatException nfe) {
                log.error("Bad viewnum for tag " + sessionTag + ": ["+ view +
                          "]: " + nfe);
                response.sendError(500, "Please reload web page.");
                return;
            }
            if (viewNum < 0  ||  viewNum > ConfigUtil.MAX_VIEW) {
                log.error("Bad viewnum [hard-code check] " +
                          sessionTag + ": "+ view);
                response.sendError(500, "Please reload web page.");
                return;
            }

            String orient = request.getParameter("o");
            if (orient == null  ||  "none".equals(orient)) {
                log.error("No orientation");
                response.sendError(500, "Failed to generate session tag");
                return;
            }
            if (orient.length() != 1) {
                log.error("Orientation len: " + orient);
                response.sendError(500, "Failed to generate session tag");
                return;
            }
            if ("vho".indexOf(orient) == -1) {
                log.error("Orientation code: " + orient);
                response.sendError(500, "Failed to generate session tag");
                return;
            }
            if ("o".equals(orient)) {
                orient = null;
            }

            if (screenId1 == -1  ||  screenId2 == -1) {
                log.error("missing screenid for load single " +
                          screenId1 + " " + screenId2);
                response.sendError(500, "Please reload web page.");
                return;
            }
            if (screenId1 > 2  ||  screenId2 > 2) {
                log.error("screenId > 2: " + screenId1 + " " + screenId2);
                response.sendError(500, "Please reload web page.");
                return;
            }

            // 2 l(oads) on top of initial is too much
            if (!"l".equals(cmd)) {
                log.info("REQ " + viewNum + "/" + orient + " r " + r +
                     " cmd " + cmd +
                     " screens " + screenId1 + "|" + screenId2 +
                     " repeat " + session.repeatPics +
                     " " + browserID + " " + remoteHost);
            }

            ShowingPair last = ShowingPairDao.getLastShowingToBrowser(
                                         conn, browserID);

            if (last != null) {

                last.rateTime = rateTime;
                last.rating = r;
                last.ratingScheme = RATING_SCHEME;

                last.userTime = viewTime;
                last.userTime2 = viewTime2;
                last.clickTime = clickTime;
                last.watchDotsTime = watchDotsTime;
                last.mouseDownTime = mouseDownTime;
                last.bigTime = lastBig;
                last.loadTime = loadTime;

                last.mouseDist = mouseDist;
                last.mouseDist2 = mouseDist2;
                last.mouseDx = mouseDx;
                last.mouseDy = mouseDy;
                last.mouseVecx = mouseVecx;
                last.mouseVecy = mouseVecy;
                last.mouseMaxv = mouseMaxv;
                last.mouseMaxa = mouseMaxa;
                last.mouseMina = mouseMina;
                last.mouseMaxj = mouseMaxj;

                last.pixInPic = pixInPic;
                last.pixOutPic = pixOutPic;

                last.dotStartScreen = screenId1;
                last.dotEndScreen = screenId2;

                last.dotCount = dotCount;
                last.dotDist = dotDist;
                last.dotVecLen = dotVecLen;
                last.dotVecAng = dotVecAng;

                last.dotMaxVel = dotMaxVel;
                last.dotMaxAccel = dotMaxAccel;
                last.dotMaxJerk = dotMaxJerk;

                last.picClik = clickLoc;
                last.nTogs = nToggleLast;
                last.togSides = toggleSides;
                last.toggleTStr = toggleTStr;

                last.locSpec = locspec; // not saved
                last.dotHistory = new DotHistory(last, dotHistory); // not saved
//log.info("LDH " + last.dotHistory);
            }

            if ("l".equals(cmd)) {

                // load a screen

                if (screenId1 != screenId2) {
                    log.error("l: INTERNAL placeholder screen id's !=");
                    response.sendError(500, "Please reload web page.");
                    return;
                }
                if (screenId1 < 1) { // needs to be in 1,2
                    log.error("l: INTERNAL screenId1=" + screenId1);
                    response.sendError(500, "Please reload web page.");
                    return;
                }
                if (session.screens.size() == 0) {
                    log.error("l: no session_screen");
                    response.sendError(500, "Please reload web page.");
                    return;
                }

                Screen scr = session.getScreen(screenId1, 0);
                if (scr == null) {
                    log.error("l: No screen: (" + screenId1 +
                                             "-1), 0: screens: " +
                                             session.screens.size());
                    response.sendError(500, "Please reload web page.");
                    return;
                }
                if (scr.sessionId == -1) {
                    // 2023_08 - significance long-forgotten
                    //  log.info("sessionId == -1!");
                    scr.sessionId = session.id;
                }

                boolean sendCurrent = (r==0);

                PictureResponse pr = new PictureResponse();

                Screen nbr_screen = null;
                if (sendCurrent) {

                    // send current, whether seen or not

                    if (scr.id_s == null) {

                        // actually no way for the user to recover.
                        // way to this should be blocked now.
                        // TODO: expose the hidden reset option
                        // as a popup option when this happens,
                        // since new session might have a chance

                        log.error("l: INTERNAL placeholder screen");
                        response.sendError(500, "Please reload web page.");
                        return;
                    }
                    boolean undo = false;
                    if (!session.repeatPics) {
                        undo = true;
                        session.repeatPics = true;
                    }
                    pr.p = PictureDao.getPictureById(conn, scr.id_s);
                    if (undo) {
                        session.repeatPics = false;
                    }
                    if (pr.p == null) {
                        log.error("l: INTERNAL no version " + scr.id_s);
                        response.sendError(500, "Please reload web page.");
                        return;
                    } else {
                        /*
                        moved to post-webp check
                        log.info("browser " + browserID + " " + remoteHost +
                            " load screen " + screenId1 + ": " + scr.id_s +
                            " " + pr.p.fileName + " METHOD " + scr.selMethod);
                        */
                        //pr.method = "sc/ld";
                    }

                } else {  // !sendCurrent

                    if (last == null) {
                        log.error("l: NO PREV ShowingPair: " + browserID + " " +
                                   remoteHost);
                    } else {
                        ShowingPairDao.updateShowingPair(conn, last);
                    }

                    // match screen to left
                    int nbr_screenId = screenId1 - 1;
                    if (nbr_screenId == 0) {
                        nbr_screenId = 2;
                    }
                    nbr_screen = session.getScreen(nbr_screenId, 0);
                    if (nbr_screen == null) {
                        log.error("null!! session.getScreen(nbr_screenId, 0), nbr_screenId == "
                                                    + nbr_screenId + " id1 " + screenId1);
                        response.sendError(500, "Please reload web page.");
                        return;
                    }

                    SeenIds seenIds = engine.getSeen(conn, session);
                    seenIds.exclude.add(scr.id_s); // need to see a change

                    if (r == 4) {
                        pr = engine.getNeuralMatch(conn, viewNum, orient, session,
                                                  last, nbr_screen.id_s,
                                                  nbr_screenId);
                    } else if (r == 5) {

                        pr = engine.replacePicOnClick(conn, viewNum, orient, session,
                                                        last, scr.id, clickLoc, locspec,
                                                        nbr_screen.id_s, scr.id_s);

                    } else {

                        log.error("l: unk rating" + r);
                        response.sendError(500, "Please reload web page.");
                        return;
                    }

                    if (session.curator != null  &&
                            pr != null  &&  pr.p != null) {
                        if (scr.id == 0) {
                            UniquePairDao.insert(conn, session.curator,
                                                       pr.p.id, nbr_screen.id_s);
                        } else {
                            UniquePairDao.insert(conn, session.curator,
                                                       nbr_screen.id_s, pr.p.id);
                        }
                    }
                }
                if (!sendCurrent &&  pr != null  &&  pr.p != null) {

                    // made a choice

                    if (last == null) {
                        throw new RuntimeException(
                               "l: Last is null on single-pic " +
                               browserID + " " + remoteHost);
                    }
                    ShowingPairDao.updateShowingPair(conn, last);

                    ShowingPair sp = new ShowingPair();
                    sp.browserID = browserID;
                    sp.callCount = callCount;
                    sp.orderInSession = engine.getOrderInSession(conn,
                                                                browserID);
                    sp.mouseDownTime = mouseDownTime;
                    if (scr.id == 0) {
                        sp.id1 = pr.p.id;
                        sp.archive1 = pr.p.archive;
                        sp.fileName1 = pr.p.fileName;
                        sp.selMethod1 = pr.method;

                        sp.id2 = last.id2;
                        sp.archive2 = last.archive2;
                        sp.fileName2 = last.fileName2;
                        sp.selMethod2 = last.selMethod2;

                    } else {
                        sp.id1 = last.id1;
                        sp.archive1 = last.archive1;
                        sp.fileName1 = last.fileName1;
                        sp.selMethod1 = last.selMethod1;

                        sp.id2 = pr.p.id;
                        sp.archive2 = pr.p.archive;
                        sp.fileName2 = pr.p.fileName;
                        sp.selMethod2 = pr.method;
                    }
                    sp.vertical = pr.p.vertical;
                    sp.atomImpact = AtomSpec.NO_ATOM;
                    sp.bigStime = (int) (System.currentTimeMillis() - t1);
                    ShowingPairDao.insertShowingPair(conn, sp);

                    scr.showingId = sp.id;
                    scr.id_s = pr.p.id;
                    scr.selMethod = pr.method;

                    engine.addSeen(conn, browserID, scr.id_s, orient);

                    SessionDao.updateSessionScreen(conn, scr);

                    String tag;
                    if (screenId1 == screenId2) {
                        if (screenId1 == 1) {
                            tag = "LL: ";
                        } else {
                            tag = "RR: ";
                        }
                    } else if (screenId1 == 1  &&  screenId2 == 2) {
                        tag = "LR: ";
                    } else {
                        tag = "RL: ";
                    }
                    if (engine.KEYWORDS) {
                        log.info(tag + DBUtil.kwdCompare(conn, kwdCoder, scr.id_s,
                                                             nbr_screen.id_s));
                    }
                    t1 = System.currentTimeMillis() - t1;
                    log.info("browser " + browserID + " " + sessionTag +
                        "[" + sessionID + "]/" + kwdCoder + " " +
                        " load_t: " + loadTime +
                        " view_t: " + viewTime +
                        " view_t2: " + viewTime2 +
                        " click_t: " + clickTime +
                        " watch_t: " + watchDotsTime + "/" + mouseDownTime
                        + "\n" +
                        " m_dist: " + mouseDist +
                        " m_dist2: " + mouseDist2 +
                        " m_maxv: " + mouseMaxv +
                        " m_maxa: " + mouseMaxa +
                        " m_mina: " + mouseMina +
                        " m_maxj: " + mouseMaxj + "\n" +
                        " m_xy: " + mouseDx + "/" + mouseDy +
                        " m_area: " + (mouseDx * mouseDy) +
                        " m_vec_xy: " + mouseVecx + "/" + mouseVecy +
                        " " + Math.round(Math.sqrt(
                                   mouseVecx*mouseVecx + mouseVecy*mouseVecy))
                            +"\n" +
                        " m_in_pic: " + pixInPic
                            + " m_out_pic: " + pixOutPic + "\n" +
                        " picClik: " + clickLoc +
                           " dots: " + dotCount + " ang: " + dotVecAng +
                           " dist: " + dotDist + "\n" +
                        " tog: " + nToggleLast + "/" + toggleSides + "\n" +
                         "==> " + pr.p.archive + "/" + pr.p.fileName +
                            " " + pr.method +
                        "]\nn: " + + sp.orderInSession +
                           " pct: " + engine.getPctSeen(conn, session,
                                                        viewNum, orient) +
                           " count: " + callCount +
                        " time " + t1 + " sel: " + sp.selMethod1 + "::" +
                                                   sp.selMethod2);
                }
                if (pr != null  &&  pr.p != null) {

                    // sendCurrent likely true too

                    String fname = pr.p.fileName;
                    if (WEBP  &&  fname.endsWith(".jpg")) {
                        fname = fname.replace(".jpg", ".webp");
                    }
                    fname = IMAGE_DIR + "/" + pr.p.archive + "/" + fname;

                    File file = new File(fname);

                    log.info("browser " + browserID + " " + remoteHost +
                            " load screen " + screenId1 + ": " + scr.id_s +
                            " " + fname + " METHOD " + scr.selMethod);

                    sendFile(request, response, file);

                    return;
                }
                log.info("DONE on single-pic load. Sending empty image");

                File file = new File(WEB_DIR + "/empty.jpg");
                sendFile(request, response, file);
                return;

            } else if ("m".equals(cmd)) {

                // multi

                if (r == -3) {
                    // let last screens ride TODO unless format has changed
                    //response.setStatus(HttpServletResponse.SC_NO_CONTENT);

                    response.setContentType( "text/plain" );
                    PrintWriter out = response.getWriter();

                    // TODO - look up pics; defaulting to neutral
                    // pr1.p.r + ":" + pr1.p.g + ":" + pr1.p.b + ":" +
                    //   pr1.p.rgbRadius + ":" + pr1.p.ll + ":" +
                    //   pr1.p.labContrast + ":" + nextDrawStyle

                    out.println("rgb," +
                                  "144:144:144:80:40:50:0," +
                                  "144:144:144:80:40:50:0");
                    out.close();
                    response.flushBuffer();
                    return;
                }

                // reset session_screen

                if (r != -2) { // past initial load
                    if (last == null) {
                        log.error("NO PREV ShowingPair: " + browserID + " " +
                                   remoteHost);

                    } else {
                        ShowingPairDao.updateShowingPair(conn, last);
                    }
                }
                List<String> ids = new ArrayList<>();
                if (r == 30) {
                    // screenId1,2 are depths
                    if (screenId1 < 0  ||  screenId1 > 1  ||
                        screenId2 < 0  ||  screenId2 > 1) {
                        log.error("flipEm screenIds");
                        response.sendError(500, "Flip broken, reload web page.");
                        return;
                    }

                    Screen s1 = session.getScreen(1, screenId1);
                    Screen s2 = session.getScreen(2, screenId2);

                    if (s1 == null  ||  s2 == null) {
                        log.error("flipEm screens");
                        response.sendError(500, "Flip broken, reload web page.");
                        return;
                    }

                    log.info("flip: " + s1.id_s + " " + s2.id_s);
                    ids.add(s1.id_s);
                    ids.add(s2.id_s);

                } else if (r != -2) { // past initial load

                    for (Screen scr : session.screens) {
                        if (scr.depth == 0) {
                            ids.add(scr.id_s);
                        }
                    }
                }

                // this may be default from browser after server comes up??
                if (screenId1 == 0  &&  screenId2 == 0) {

                    log.warn("?? startup?? calling getscreens w/ both screens 0: setting=1");

                    screenId1 = 1;
                    screenId2 = 1;
                }

                List<Screen> screens = null;
                try {

                    screens = engine.getScreens(conn, viewNum, orient,
                                                        kwdCoder,
                                                        session, r, cmdMod,
                                                        2, ids,
                                                        screenId1, screenId2,
                                                        lastBig, last);
                } catch (Exception e) {
                    log.error("Engine: " + e.getMessage(), e);
                    throw e;
                }
                if (screens == null) {
                    log.info("screens==null");
                    response.setContentType( "text/plain" );
                    PrintWriter out = response.getWriter();
                    out.println("DONE");
                    out.close();
                    return;
                }
                if (screens.size() == 0) {
                    response.setContentType( "text/plain" );
                    PrintWriter out = response.getWriter();
                    out.println("ODONE");
                    out.close();
                    return;
                }

                ids.clear();
                t1 = System.currentTimeMillis() - t1;

                PictureResponse pr1 = (PictureResponse) screens.get(0).pr;
                PictureResponse pr2 = (PictureResponse) screens.get(1).pr;

                if (pr1 == null  &&  pr2 == null) {
                    log.error("Inserting ShowingPair: both pr's null");
                } else if (pr1 == null  ||  pr2 == null) {
                    log.error("Inserting ShowingPair: Null pr on screen: " +
                                (pr1 == null ? "1" : "") +
                                (pr2 == null ? "2" : ""));
                }

                ShowingPair sp = new ShowingPair();
                sp.browserID = browserID;

                if (pr1 != null) {
                    sp.vertical = pr1.p.vertical;
                } else if (pr2 != null) {
                    sp.vertical = pr2.p.vertical;
                }
                if (pr1 != null) {
                    sp.id1 = pr1.p.id;
                    sp.archive1 = pr1.p.archive;
                    sp.fileName1 = pr1.p.fileName;
                    sp.selMethod1 = pr1.method;
                }
                if (pr2 != null) {
                    sp.id2 = pr2.p.id;
                    sp.archive2 = pr2.p.archive;
                    sp.fileName2 = pr2.p.fileName;
                    sp.selMethod2 = pr2.method;
                }
                sp.callCount = callCount;
                sp.mouseDownTime = mouseDownTime;
                sp.orderInSession = engine.getOrderInSession(conn, browserID);
                sp.bigStime = (int) t1;
                sp.atomImpact = AtomSpec.NO_ATOM;

                ShowingPairDao.insertShowingPair(conn, sp);
                screens.get(0).showingId = sp.id;
                screens.get(1).showingId = sp.id;

                screens.get(0).sessionId = session.id;
                screens.get(1).sessionId = session.id;

                engine.addSeen(conn, browserID, sp.id1, orient);
                engine.addSeen(conn, browserID, sp.id2, orient);

                for (Screen scr : screens) {
                    SessionDao.updateSessionScreen(conn, scr);
/*
                    log.info("\n==> " + s.archive + "/" + s.fileName + " " +
                        " n: " + + sp.orderInSession + " count: " + callCount +
                        " sel: " + ((PictureResponse)scr.pr).selMethod);
*/
                }
                if (session.curator != null) {
                    UniquePairDao.insert(conn, session.curator, sp.id1, sp.id2);
                }
                //t1 = System.currentTimeMillis() - t1;

                long nextDrawStyle = viewTime + viewTime2 + watchDotsTime +
                                    2 * clickTime + 3 * mouseDownTime +
                                    mouseDist + dotDist + dotCount + callCount;
		        //log.info("B4 " + nextDrawStyle);
                nextDrawStyle %= 5;

                log.info("browser " + browserID + " " + sessionTag +
                        "[" + sessionID + "]/" + kwdCoder + " " +
                        " load_t: " + loadTime +
                        " view_t: " + viewTime +
                        " view_t2: " + viewTime2 +
                        " click_t: " + clickTime +
                        " watch_t: " + watchDotsTime + "/" + mouseDownTime
                        + "\n" +
                        " m_dist: " + mouseDist +
                        " m_dist2: " + mouseDist2 +
                        " m_maxv: " + mouseMaxv +
                        " m_maxa: " + mouseMaxa +
                        " m_mina: " + mouseMina +
                        " m_maxj: " + mouseMaxj + "\n" +
                        " m_xy: " + mouseDx + "/" + mouseDy +
                        " m_area: " + (mouseDx * mouseDy) +
                        " m_vec_xy: " + mouseVecx + "/" + mouseVecy +
                        " " + Math.round(Math.sqrt(
                                   mouseVecx*mouseVecx + mouseVecy*mouseVecy))
                            +"\n" +
                        " m_in_pic: " + pixInPic
                            + " m_out_pic: " + pixOutPic + "\n" +
                        " picClik: " + clickLoc +
                           " dots: " + dotCount + " ang: " + dotVecAng +
                           " dist: " + dotDist + "\n" +
                        " tog: " + nToggleLast + "/" + toggleSides + "\n" +
                        "==: " +
                           sp.id1 + " " + sp.fileName1 + " " + sp.selMethod1 +
                           "\n==> " +
                           sp.id2 + " " + sp.fileName2 + " " + sp.selMethod2 +
                        "]\nn: " + + sp.orderInSession +
                           " pct: " + engine.getPctSeen(conn, session,
                                                        viewNum, orient) +
                           " count: " + callCount +
                        " rating: " + r + " nextDraw " + nextDrawStyle +
                           " time " + t1);

                StringBuilder sb = new StringBuilder();

                sb.append("rgb,").append(pr1.p.r).append(":")
                                 .append(pr1.p.g).append(":")
                                 .append(pr1.p.b).append(":")
                                 .append(pr1.p.rgbRadius).append(":")
                                 .append(pr1.p.ll).append(":")
                                 .append(pr1.p.labContrast).append(":")
                                 .append(nextDrawStyle)
                                 .append(",")
                                 .append(pr2.p.r).append(":")
                                 .append(pr2.p.g).append(":")
                                 .append(pr2.p.b).append(":")
                                 .append(pr2.p.rgbRadius).append(":")
                                 .append(pr2.p.ll).append(":")
                                 .append(pr2.p.labContrast).append(":")
                                 .append(nextDrawStyle)
                                 .append("\n");
                sb.append("m,").append(pr1.method).append(",").append(pr2.method)
                                 .append("\n");

                String msg = sb.toString();
                response.setBufferSize(1); // fingers crossed
                response.setContentType( "text/plain" );
                PrintWriter out = response.getWriter();

                out.println(msg);

                // "In short, If you are using .flush() or .close()
                //  on any Servlet stream/reader/writer, stop doing that,
                //  you are doing far more harm than good.
                //Joakim Erdfelt / joakim@webtide.com
                //out.flush(); //out.close(); //response.flushBuffer();

                if (pr1.p.vec_l != null  &&  pr2.p.vec_r != null) {

                    try {
                        double vec_d  = MathUtil.cartesianDist(pr1.p.vec_l, pr2.p.vec_r);
                        double cos_sim = MathUtil.cos_sim(pr1.p.vec_l, pr2.p.vec_r);
                        log.info("-- VECs d/sim " + vec_d + "/" + cos_sim);
                    } catch (Exception e) {
                        log.warn("old debug, so Ignoring " + e);
                    }
                }

                if (r == 10) {
                    // TODO - has 2-min block on restarting for now, needs complication
                    engine.cacheNeighborsThread(session, viewNum, orient, screens, 0);
                } else if (r == 20) {
                    // curate.html Sigma0 (slow) - cache d0-local pairs for next time?
                    engine.cacheNeighborsThread(session, viewNum, orient, screens, 1);
                } else if (r == 0) {
                    // curate.html neg/- (slow) - cache d0-local pairs for next time?
                    //log.info("NEW cache thread");
                    engine.cacheD0BadThread(session, viewNum, orient);
                }

                return;

            } else {

                log.error("cmd NOT IMPL " + cmd);
                response.sendError(500, "NOT IMPL.");
                return;
            }
        } catch (NamingException ne) {
            log.error("Naming", ne);
            response.sendError(500, "jndi error");
        } catch (SQLException sqe) {
            log.error("DB: " + sqe, sqe);
            response.sendError(500, "Database error");
        } catch (Exception e) {
            log.error("Other: " + e, e);
            response.sendError(500, "Generic error");
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignore) {}
            }
        }
    }


}

