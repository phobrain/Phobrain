package org.phobrain.servlet;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  ReNext - web.xml=renext - mnbpair.html
 **     Selecting pics from pr.approved_pair for review.
 **
 **  The cmd is (one of each in [] req'd):
 **
 **    'pr[vh][q][fr][0+]'
 **
 **    vh - orientation
 **    q  - quality/d0, other opts forgotten/unused
 **    fr - forward/reverse, only f used long time
 **    0+ - pr.approved_pair.status, typically 0 or 8.
 **    opts after - kwd selection, others forgotten
 **
 */

import org.phobrain.util.ConfigUtil;
import org.phobrain.util.MiscUtil;
import org.phobrain.util.HashCount;
import org.phobrain.util.ListHolder;

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
import org.phobrain.db.dao.TrainingPairsDao;
import org.phobrain.db.record.Session;
import org.phobrain.db.record.Browser;
import org.phobrain.db.record.User;
import org.phobrain.db.record.HistoryPair;
import org.phobrain.db.record.Keywords;
import org.phobrain.db.record.Picture;
import org.phobrain.db.record.PictureMap;
import org.phobrain.db.record.ApprovedPair;

import com.google.gson.Gson;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Vector;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

import java.io.IOException;
import java.io.File;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
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

public class ReNext extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(ReNext.class);

    private static final GetEngine engine = GetEngine.getEngine();

    protected static final ServletData data = ServletData.get();

    private String graphDir = null; // relic

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
        log.info("ReNext Init OK");
    }

    @Override
    public String getServletInfo() {
        return "ReNext";
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
        long value;
        double dbl; // so far value^x

        @Override
        public int compareTo(Object oo) {
            KV o = (KV) oo;
            if (this.value > o.value) return 1;
            if (this.value < o.value) return -1;
            return 0;
        }
    }

    // class for json and use by js in browser

    static class ShowingInfo {
        HistoryPair showing;
        String kwd;
        String kwdMatch;
        String kwdNoMatch;
        long dbTime;
        String hash;
        ListHolder lh;
    }

    static class PairInfo {
        ApprovedPair ap;
        boolean hide1 = false; // browser only
        boolean hide2 = false; // browser only
        String hash1;
        String hash2;
    }

    private static final int V1_SIZE = 10;
    private static final int V2_SIZE = 50;

    private static class VectorPair {
        Vector<Double> v1 = new Vector<>(V1_SIZE);
        Vector<Double> v2 = new Vector<>(V2_SIZE);
    }

    private void addV(VectorPair v, int val) {

        Double d = new Integer(val).doubleValue();

        v.v1.add(d);
        if (v.v1.size() > V1_SIZE) {
            v.v1.remove(0);
        }
        v.v2.add(d);
        if (v.v2.size() > V2_SIZE) {
            v.v2.remove(0);
        }
    }

    private ListHolder getCandidateList(long browserID, long showingID) {

        if (graphDir == null) {
            return null;
        }

        String fname = graphDir + "/" + browserID + "/" + showingID;
        ListHolder lh = new ListHolder();
        try {
            File f = new File(fname);
            if (!f.isFile()) {
                //log.error("Not a file: " + fname);
                fname += ".gz";
                f = new File(fname);
                if (!f.isFile()) {
                    return null;
                }
            }
/*
InputStream fileStream = new FileInputStream(filename);
InputStream gzipStream = new GZIPInputStream(fileStream);
Reader decoder = new InputStreamReader(gzipStream, encoding);
BufferedReader buffered = new BufferedReader(decoder);
*/
            BufferedReader in = null;
            if (fname.endsWith(".gz")) {
                log.info("trying gz " + fname);
                in = new BufferedReader(new InputStreamReader(
                      new GZIPInputStream(new FileInputStream(fname)), "UTF-8"));
            } else {
                in = new BufferedReader(new FileReader(fname));
            }
            List<KV> kvl = new ArrayList<>();
            boolean useFirst = false;
            boolean firstLine = true;
            String line;
            while ((line = in.readLine()) != null) {

                line = line.trim();

                if (firstLine) {
                    if (line.equals("first")) {
                        useFirst = true;
                        continue;
                    }
                    firstLine = false;
                }
                String ss[] = line.split(" ");
                KV kv = new KV();
                kv.key = ss[0];
                kv.value = Long.parseLong(ss[1]);
                if (ss.length > 2) {
                    kv.dbl = Double.parseDouble(ss[2]);
                }
                kvl.add(kv);
            }
            if (!useFirst) {
                Collections.sort(kvl, Collections.reverseOrder());
                lh.dbl_l = new ArrayList<>(kvl.size());
            }
            for (int i=0; i<kvl.size(); i++) {
                KV kv = kvl.get(i);
                lh.id2_l.add(kv.key);
                lh.value_l.add(kv.value);
                if (lh.dbl_l != null) {
                    lh.dbl_l.add(kv.dbl);
                }
            }
        } catch (Exception e) {
            log.error("Reading " + fname + ": " + e, e);
        }
        return lh;
    }

    private static List<String> parsePref(String fname) {
        File f = new File(fname);
        if (!f.isFile()) {
            throw new RuntimeException("Not a file: " + fname);
        }

        List<String> ret = new ArrayList<>();

        BufferedReader in = null;
        int lineN = 0;
        String line = "start";
        try {
            in = new BufferedReader(new FileReader(f));
            while ((line = in.readLine()) != null) {
                lineN++;
                String ss[] = line.split(" ");
                ret.add(ss[0]);
            }
        } catch (Exception e) {
            throw new RuntimeException("Getting pref: line " + lineN + "\n" +
                                       line, e);
        } finally {
            try { in.close(); } catch (Exception ignore) {}
        }

        return ret;
    }

    private PictureMap getPicMap(Connection conn, Session session,
                                                  String id, String orient)
            throws SQLException {

        //log.info("ID " + id);
        Picture p = PictureDao.getPictureById(conn, id);
        if (p == null) {
            //log.error("No picture: " + id);
            return null;
        }
        PictureMap pm = PictureMapDao.insertPictureMap(conn, p.xid,
                                                     p.archive, p.fileName);
        return pm;
    }

    private static class FileRec implements Comparable {
        //int arch;
        //int seq;
        //String id;
        //long sum_d0;
        Picture p;

        //FileRec(String id, long sum_d0) {
        FileRec(Picture p) {
            this.p = p;
            //this.id = id;
            //int as[] = IndexHolder.getArchSeq(id);
            //this.arch = as[0];
            //this.seq = as[1];
            //this.sum_d0 = sum_d0;
        }
        @Override
        public int compareTo(Object o) {

            FileRec fr = (FileRec) o;

            if (p.d0Sum < fr.p.d0Sum) return -1;
            if (p.d0Sum > fr.p.d0Sum) return 1;

            if (p.archive < fr.p.archive) return -1;
            if (p.archive > fr.p.archive) return 1;

            if (p.sequence < fr.p.sequence) return -1;
            if (p.sequence > fr.p.sequence) return 1;

            return 0;
        }
    }

    private static void addAPtoMap(Map<String, List<ApprovedPair>> kwdMap,
                                        String kwd, ApprovedPair ap) {
        List<ApprovedPair> lap = kwdMap.get(kwd);
        if (lap == null) {
            lap = new ArrayList<ApprovedPair>();
            kwdMap.put(kwd, lap);
        }
        lap.add(ap);
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException  {

        long t1 = System.currentTimeMillis();

        String remoteHost = req.getRemoteHost();

        //log.info("ReNext POST " + remoteHost);

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
            if (!session.user.equals("ripplay")) {
                log.error("User not 'ripplay' for session tag " + sessionTag +
                          ": " + session.user);
                res.sendError(500, "Internal Error. Call Help.");
                return;
            }
            if (session.kwdChoice == null) { // TODO - use for kwdCoder?
                log.error("NO kwd_choice for session tag " + sessionTag);
                res.sendError(500, "Internal Error. Call Help.");
                return;
            }
            List<ShowingInfo> shows = new ArrayList<>();
            List<PairInfo> pairs = new ArrayList<>();
            String sessionType = "no_sess";
            if (session.kwdChoice.startsWith("P")) {

                sessionType = "pr"; // translated to 'P' in kwdChoice

                // pr + v | h
                //    + 0 | 1 | h | q  - sort pic if pic sort /history/quality
                //    + f | r          - forward, reverse
                //    + 0..   - status
                //    [+ P|U|L] - P: 'people' on left but not right
                //              - U: uppercase curator (from bulk confirm)
                //              - L: lowercase curator (manual curate+confirm)
                //    [+ +kwd_one+kwd_two]
                // pr + h0f4 [+ d for confirm all]

                String orient = null;

                // charAt 1: v|h
                if (session.kwdChoice.length() > 1) {
                    orient = "" + session.kwdChoice.charAt(1);
                    if (!"v".equals(orient)  &&  !"h".equals(orient)) {
                        log.error("Expected v/h: " + session.kwdChoice);
                        res.sendError(500, "Internal Error. Call Help.");
                        return;
                    }
                }

                // charAt 2: 0|1|h|q|t
                boolean history = false;
                boolean quality = false;
                boolean first = true;  // sort by left or right pic
                boolean train = false;
                boolean kwdQuality = false;
                if (session.kwdChoice.length() > 2) {
                    switch(session.kwdChoice.charAt(2)) {
                        case '0': first = true; break;
                        case 'h': history = true; break;
                        case 'q': quality = true; break;
                        case 'k': kwdQuality = true; break;
                        case 't': train = true; break;
                    }
                }
                log.info("Flags: " +
                        " 0==first: " + first +
                        " h==history: " + history +
                        " q==quality: " + quality +
                        " k==kwdQuality: " + kwdQuality +
                        " t==train: " + train);

                // charAt 3: f|r (sort dir: fwd|reverse)
                boolean forward = true;
                if (session.kwdChoice.length() > 3) {
                    forward = 'f' == session.kwdChoice.charAt(3);
                    if (!forward  &&  'r' != session.kwdChoice.charAt(3)) {
                        log.error("Expected f/r: " + session.kwdChoice);
                        res.sendError(500, "Internal Error. Call Help.");
                        return;
                    }
                }

                List<ApprovedPair> apl;
                List<ApprovedPair> apl2 = new ArrayList<>();

                // for train==false
                List<String> kwds = null;
                int status = -1;
                Boolean curatorUpper = null;
                Boolean peopleMismatch = null;
                // charAt 4+: status, 0..108
                int start = 4;
                int end = 4;

                if (train) {

                    if (session.kwdChoice.length() > 3) {
                        log.error("train: too long: " + session.kwdChoice);
                        res.sendError(500, "Internal Error. Call Help.");
                        return;
                    }

                    apl = TrainingPairsDao.getSameArchSeqsPosNeg(orient);

                } else {

                    while (end < session.kwdChoice.length()  &&
                            Character.isDigit(session.kwdChoice.charAt(end))) {
                        end++;
                    }
                    status = Integer.parseInt(
                                      session.kwdChoice.substring(start, end));

                    // charAt end: U|L == curator case (upper==bulk confirm)
                    if (end < session.kwdChoice.length()) {
                        switch(session.kwdChoice.charAt(end)) {
                            case 'P':
                                peopleMismatch = true;
                                end++;
                                break;
                            case 'U':
                                curatorUpper = true;
                                end++;
                                break;
                            case 'L':
                                curatorUpper = false;
                                end++;
                                break;
                        }
                    }
                    if (end < session.kwdChoice.length()  &&
                              session.kwdChoice.charAt(end) == ' ') {
                        // '+' => ' '

                        end++;

                        String s = session.kwdChoice.substring(end);
                        String ss[] = s.split(" ");

                        log.info("Got kwds: " + ss.length +
                                 ": [" + session.kwdChoice + "] [" + s +
                                "] " + end);

                        kwds = new ArrayList<>();

                        for (String k : ss) {
                            kwds.add(k);
                        }
                    }
                    if (history) {
                        apl = ApprovalDao.getAllApprovedPairsByTime(conn,
                                            status, orient, curatorUpper,
                                            forward, null);
                    } else {
                        apl = ApprovalDao.getAllApprovedPairs(conn,
                                            status, orient, curatorUpper,
                                            quality, first, forward, null);
                    }

                    if (peopleMismatch != null  &&  peopleMismatch) {
                        // people on left but not right
                        boolean vertical = "v".equals(orient);
                        for (ApprovedPair ap : apl) {
                            if (ap.matchPeople) {
                                continue;
                            }
                            Picture p = PictureDao.getPictureById(conn, ap.id1);
                            if (p.people) {
                                continue;
                            }
                            apl2.add(ap);
                        }
                        log.info("'P'eople mismatch: " +
                                    apl.size() + "->" + apl2.size());
                        apl = apl2;
                        apl2 = new ArrayList<>();
                    }
                }

                // got apl

/*
                log.info("Getting kwds " + session.user +
                         " pairs: " + apl.size());
                int kwdSkip = 0;
                int matchPeople = 0;

                for (ApprovedPair ap : apl) {

                    //log.info("KWDS " + ap.id1 + " " + ap.id2);

                    // intersect kwds
                    ap.noGeomKwds = DBUtil.kwdCompare2(conn, "m", true,
                                                             ap.id1, ap.id2);
                    ap.geomKwds = DBUtil.kwdCompare2(conn, "m", false,
                                                             ap.id1, ap.id2);
                    if (kwds != null) {
                        boolean skip = false;
                        for (String kwd : kwds) {
                            if ((ap.noGeomKwds != null  &&
                                 ap.noGeomKwds.contains(kwd))  ||
                                (ap.geomKwds != null  &&
                                 ap.geomKwds.contains(kwd))) {
                                // kwd matches
                            } else {
                                skip = true;
                                break;
                            }
                        }
                        if (skip) {
                            kwdSkip++;
                            continue;
                        }
                    }

                    if (ap.matchPeople) {
                        matchPeople++;
                    }
                    apl2.add(ap);
                }
                apl = apl2;

                log.info("Got kwds " + session.user +
                         " kwdc " + session.kwdChoice +
                         " " + orient + " status " + status +
                         " upper " + curatorUpper +
                         " size " + apl.size() +
                         " matchPeople " + matchPeople);
*/

                if (status == 4) {
                    log.info("Status 4, size: " + apl.size());
                    if (end < session.kwdChoice.length()  &&
                            session.kwdChoice.charAt(end) == 'd') {
log.info("SU " + session.user + " " + apl.size());
                        for (ApprovedPair apx : apl) {
                            // status == -1 => uppercase curator for bulk
                            String retMsg = ApprovalDao.update(conn,
                                                      session.user,
                                                      -1, apx.id1, apx.id2);
                            if (retMsg != null) {
                                log.info("Update: " + retMsg);
                            }
                        }
                        apl = ApprovalDao.getAllApprovedPairs(conn,
                                          status, orient, curatorUpper,
                                          quality, first, forward, null);
                    }
                }

                if (kwdQuality) {

                    log.error("kwdQuality skipped since kwds shelved");
/*
                    Map<String, List<ApprovedPair>> kwdMap = new HashMap<>();
                    HashCount kwdCt = new HashCount();

                    for (ApprovedPair ap : apl) {

                        log.info("KQual " + ap.id1 + " " + ap.id2 +
                                " / " + ap.noGeomKwds +
                                " / " + ap.geomKwds);

                        for (String kwd : ap.noGeomKwds) {
                            addAPtoMap(kwdMap, kwd, ap);
                            kwdCt.add(kwd);
                        }
                        log.info("KQual " + ap.id1 + " " + ap.id2);
                        for (String kwd : ap.geomKwds) {
                            addAPtoMap(kwdMap, kwd, ap);
                            kwdCt.add(kwd);
                        }
                    }

                    log.info( "kwdQuality map / ct size " +
                            kwdMap.size() + " / " + kwdCt.size());

                    apl2 = new ArrayList<>();

                    // sets of kwds that have the same # of pairs
                    //    pairs can appear under multiple kwds so
                    //    the ordering won't be perfect
                    List<Set<String>> sets = kwdCt.getSetsInOrder();

                    for (Set<String> set : sets) {
                        for (String kwd : set) {
                            List<ApprovedPair> apl3 = new ArrayList<>();
                            for (ApprovedPair ap : apl) {
                                if (ap.geomKwds.contains(kwd)  ||
                                    ap.noGeomKwds.contains(kwd)) {

                                    apl2.add(ap);
                                } else {
                                    apl3.add(ap); // easier to add than remove
                                }
                            }
                            apl = apl3;
                        }
                    }
                    // non-kwd-matching pairs
                    apl2.addAll(apl);

                    apl = apl2;
*/
                }

                Map<String, PictureMap> pmap = new HashMap<>();

                Set<String> outset = new HashSet<>(); // local optimization
                int noutpr = 0;

                for (ApprovedPair ap : apl) {

                    if (outset.contains(ap.id1)  ||  outset.contains(ap.id2)) {
                        noutpr++;
                        continue;
                    }

                    PairInfo pi = new PairInfo();
                    pi.ap = ap;

                    PictureMap pm = pmap.get(ap.id1);
                    if (pm == null  ||  "undefined".equals(pm.hash)) {
                        pm = getPicMap(conn, session, ap.id1, orient);
                        if (pm == null  ||  "undefined".equals(pm.hash)) {
                            //log.error("No PicMap for " + ap.id1 + "/" + orient);

                            outset.add(ap.id1);
                            noutpr++;

                            continue;
                        }
                        pmap.put(ap.id1, pm);
                    }
                    pi.hash1 = pm.hash;

                    pm = pmap.get(ap.id2);
                    if (pm == null  ||  "undefined".equals(pm.hash)) {
                        pm = getPicMap(conn, session, ap.id2, orient);
                        if (pm == null  ||  "undefined".equals(pm.hash)) {
                            //log.error("No pic map? " + ap.id2 + "/" + orient);

                            outset.add(ap.id2);
                            noutpr++;

                            continue;
                        }
                        pmap.put(ap.id2, pm);
                    }
                    pi.hash2 = pm.hash;

                    pairs.add(pi);
                }
                log.info("Added pairs " + pairs.size() +
                         " skipped " + noutpr +
                         " due to " + outset.size() + " nonexistent ids");
                if (outset.size() > 0) {
                    log.info("Pics not found: " + outset);
                }

            } else if ("R".equals(session.kwdChoice)  // replay session
                   || "0".equals(session.kwdChoice)) { // legacy
                long sessionID = session.id;
                long browserID = session.browserID;
                sessionType = "browser " + browserID;
                // need the session's kwd coder for choosing kwds
                Browser br = BrowserDao.getBrowserById(conn, browserID);
                if (br == null) {
                    log.error("No record of browser " + browserID);
                    res.sendError(500, "Internal Error. Call Help.");
                    return;
                }
                int ix = br.version.indexOf("k=");
                String kwdCoder = br.version.substring(ix+2, ix+3);

                List<HistoryPair> sl = ShowingPairDao.getAll(conn,
                                                        browserID);
                if (sl == null  ||  sl.size() == 0) {
                    log.error("NO pics for session tag " + sessionTag +
                          " browserID " + browserID);
                    res.sendError(500, "Internal Error. Call Help.");
                    return;
                }
                for (HistoryPair s : sl) {
log.info("S " + s.nTogs);
//System.exit(1);
                    ShowingInfo si = new ShowingInfo();
                    si.showing = s;
                    si.lh = getCandidateList(browserID, s.id);
/*
                    String id = "" + s.archive + ":" + s.picSeq;
                    Keywords k = KeywordsDao.getKeywordsByIdCoder(conn,
                                                                    id, kwdCoder);
                    if (k == null) {
                        si.kwd = "pic_rmvd";
                    } else {
                        si.kwd = k.keywords;
                    }
*/
                    shows.add(si);
                }

                VectorPair clickTimes = new VectorPair();
                VectorPair userTimes = new VectorPair();
                VectorPair userTimes2 = new VectorPair();
                VectorPair dists = new VectorPair();
                VectorPair dists2 = new VectorPair();
                VectorPair areas = new VectorPair();

                log.info("showings: " + shows.size());

                for (int i=0; i<shows.size(); i++) {

                    ShowingInfo si = shows.get(i);
                    HistoryPair s = si.showing;

                    if (s.rateTime == null) {
                        si.dbTime = 0;
                    } else {
                        si.dbTime = s.rateTime.getTime() - s.createTime.getTime();
                    }
//if(true)throw new RuntimeException("Not impl /multi");
/*
                    PictureMap pm = PictureMapDao.insertPictureMap(conn, s.picID,
                                                     s.archive, s.fileName);
                    si.hash = pm.hash;
*/
                    // calc keyword match
                    si.kwdMatch = null;
                    si.kwdNoMatch = null;
                    if (i == 0) {
                        si.kwdNoMatch = si.kwd;
                    } else {
                        ShowingInfo prev_si = shows.get(i-1);
                        String[] words = si.kwd.split(" ");
                        String[] prevwords = prev_si.kwd.split(" ");

                        StringBuilder sb_match = new StringBuilder();
                        StringBuilder sb_nomatch = new StringBuilder();
                        for (String w : words) {
                            boolean match = false;
                            for (String pw : prevwords) {
                                if (pw.equals(w)) {
                                    match = true;
                                    sb_match.append(w).append(" ");
                                    break;
                                }
                            }
                            if (!match) {
                                sb_nomatch.append(w).append(" ");
                            }
                        }
                        if (sb_match.length() > 0) {
                            si.kwdMatch = sb_match.toString().trim();
                        }
                        if (sb_nomatch.length() > 0) {
                            si.kwdNoMatch = sb_nomatch.toString().trim();
                        }
                        //log.info("kwd " + si.kwdMatch + " | " + si.kwdNoMatch);
                    }
                    // calculate cumulative window values

                    // update arrays
                    addV(clickTimes, s.clickTime);
                    addV(userTimes,  s.userTime);
                    addV(userTimes2, s.userTime2);
                    addV(dists,      s.mouseDist);
                    addV(dists2,     s.mouseDist2);
                    addV(areas,      Math.abs(s.mouseDx * s.mouseDy));

                }
                // kwds have been split, not needed
                for (ShowingInfo si : shows) {
                    si.kwd = null;
                }
            } else if (session.kwdChoice.charAt(0) == 'V') {  // load view
                sessionType = "view " + session.kwdChoice;
                log.info(sessionType);
                Map<Integer, List<String>> picLists;
                boolean vertical;
                if (session.kwdChoice.charAt(1) == 'v') {
                    picLists = data.picListsV;
                    vertical = true;
                } else if (session.kwdChoice.charAt(1) == 'h') {
                    vertical = false;
                    picLists = data.picListsH;
                } else {
                    log.error("No v|h: Parsing v|h " + session.kwdChoice);
                    res.sendError(500, "Internal Error. Call Help.");
                    return;
                }

                int view;
                try {
                    view = Integer.parseInt(session.kwdChoice.substring(2));
                } catch (NumberFormatException nfe) {
                    log.error("Parsing view " + session.kwdChoice, nfe);
                    res.sendError(500, "Internal Error. Call Help.");
                    return;
                }

                List<String> picList = picLists.get(view);
                if (picList == null) {
                    log.error("bad view " + view + " " + session.kwdChoice);
                    res.sendError(500, "Internal Error. Call Help.");
                    return;
                }
                List<FileRec> fileList = new ArrayList<>();
                for (String id : picList) {
                    Picture p = PictureDao.getPictureById(conn, id);
                    if (p.d0Sum == -1) {
                        log.info("d0Sum -1: " + p.id);
                    }
                    fileList.add(new FileRec(p));
                }
                Collections.sort(fileList);
                log.info("view " + view + "/" + (vertical ? "v" : "h") +
                         " pics: " + fileList.size() + " d0Sum: " +
                         fileList.get(0).p.d0Sum + " -> " +
                         fileList.get(fileList.size()-1).p.d0Sum);

                for (FileRec fr : fileList) {
//log.info("ID " + id);
                    Keywords k = KeywordsDao.getKeywordsByIdCoder(conn,
                                                          fr.p.id, "m");
                    PictureMap pm = PictureMapDao.insertPictureMap(conn,
                                    fr.p.xid, fr.p.archive, fr.p.fileName);

                    ShowingInfo si = new ShowingInfo();

                    if (k != null) {
                        si.kwdMatch = k.keywords;
                    } else {
                        si.kwdMatch = "[no kwd]";
                    }
                    si.hash = pm.hash;
                    si.showing = new HistoryPair();
                    si.showing.id = -1;
                    si.showing.id1 = fr.p.id;
                    // FIXME - 2 screens
                    si.showing.archive1 = fr.p.archive;
                    si.showing.fileName1 = fr.p.fileName;
                    si.showing.userTime = 3000;

                    shows.add(si);
//if (as[0]==1) {
//log.info("QQQ " + id);
//}
                }
            } else {
                sessionType = "kwd " + session.kwdChoice;
                log.info(sessionType);
                // select all pics with coder_kwd and fake Showings for them
                String[] ss = session.kwdChoice.split(":");
                if (ss.length != 2) {
                    log.error("Bad len splitting user kwdChoice: " + ss.length);
                    res.sendError(500, "Internal Error. Call Help.");
                    return;
                }
                List<String> ids;

                boolean best = "best".equals(ss[1]);
                boolean first = "first".equals(ss[1]);

                if (best) {

                    String prefFile = ConfigUtil.runtimeProperty("local.dir") +
                                "/" + ConfigUtil.runtimeProperty("pref.file");
                    ids = parsePref(prefFile);
                } else if (first) {
                    String firstsFile = ConfigUtil.runtimeProperty("local.dir") +
                                  "/" + ConfigUtil.runtimeProperty("firsts.file");
                    ids = parsePref(firstsFile);
                } else {
                    ids = KeywordsDao.getIdsCoderKwd(conn, ss[0], ss[1]);
                }
                log.info("kwds: " + ids.size());

                for (String id : ids) {
                    //log.info("ID " + id);
                    Picture p = PictureDao.getPictureById(conn, id);
                    ShowingInfo si = new ShowingInfo();
                    si.kwdMatch = ss[1];
                    Keywords k = KeywordsDao.getKeywordsByIdCoder(conn,
                                                                      id, ss[0]);
                    boolean match = false;
                    if (best || first) match = true;

                    StringBuilder sb = new StringBuilder();
                    String ka[] = k.keywords.split(" ");
                    for (String kwd : ka) {
                        if (kwd.equals(ss[1])) {
                            match = true;
                        } else {
                            sb.append(kwd).append(" ");
                        }
                    }
                    if (!match) {  // e.g. 'stone' picked up 'stone_wall' /ignore
                        continue;
                    }
                    si.kwdNoMatch = sb.toString().trim();
                    PictureMap pm = PictureMapDao.insertPictureMap(conn,
                                    p.xid, p.archive, p.fileName);
                    si.hash = pm.hash;
                    si.showing = new HistoryPair();
                    si.showing.id = -1;
                    // FIX for screens > 1
                    si.showing.id1 = id;
                    si.showing.archive1 = p.archive;
                    si.showing.fileName1 = p.fileName;
                    si.showing.userTime = 3000;
                    shows.add(si);
                }
            }
for (ShowingInfo si : shows) {
if (si.showing.togTimes != null) {
log.info("SIZE " + si.showing.id + " " + si.showing.togTimes.length);
}
}
            Gson gson = new Gson();
            String json = null;
            if (shows.size() > 0) {
                json = gson.toJson(shows);
            } else {
                json = gson.toJson(pairs);
            }
            res.setContentType( "text/plain" );
            PrintWriter out = res.getWriter();
            out.println(json);
            out.close();

            t1 = System.currentTimeMillis() - t1;
            log.info(sessionType + " done " + sessionTag +
                     " time " + t1);

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

