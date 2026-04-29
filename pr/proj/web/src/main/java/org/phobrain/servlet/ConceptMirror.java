package org.phobrain.servlet;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

//  ConceptMirror - Serves photo pairs or single photos.
//      Inheritor of GetEngine - first 'brain'.

import org.phobrain.util.MiscUtil.SeenIds;

import org.phobrain.db.record.Session;
import org.phobrain.db.record.HistoryPair;
import org.phobrain.db.dao.ShowingPairDao;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.List;

import java.util.Arrays;

import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConceptMirror extends MirrorJuice {

    private static final Logger log = LoggerFactory.getLogger(ConceptMirror.class);

    final boolean KEYWORDS = false;

    private long DEBUG_SLEEP_O_KINGLY_BUG = 0;

    boolean checkSeen(Connection conn, long browserID, String id1, String id2)
            throws SQLException {

        return ShowingPairDao.checkSeen(conn, browserID, id1, id2);
    }

    // -- Overrides

    @Override
    Set<String> getSeen(Connection conn, long browserID)
            throws SQLException {

        return ShowingPairDao.getSeen(conn, browserID);
    }

    @Override
    int countPairs(Connection conn, long browserID)
            throws SQLException {
        return ShowingPairDao.countPairs(conn, browserID);
    }
    @Override
    int countPairs(Connection conn, long browserID, String orient)
            throws SQLException {
        return ShowingPairDao.countPairs(conn, browserID, orient);
    }

    @Override
    List<HistoryPair> getLastPairs(Connection conn, long browserID, int n)
            throws SQLException {
        return ShowingPairDao.getLastPairs(conn, browserID, 10);
    }


    @Override
    SeenIds getSeen(Connection conn, Session session)
            throws SQLException {
        return getSeen(conn, session, false);
    }

    private SeenIds getSeen(Connection conn, Session session, boolean force)
            throws SQLException {

        SeenIds ret = browserSeen.get(session.browserID);
        if (ret != null) {
            if (ret.seen != null) {
                if (session.repeatPics  &&  !force) {
                    ret.seen = null;// in case user changed repeat status
                }
            } else {
                if (force  ||  !session.repeatPics) {
                    ret.seen = ShowingPairDao.getSeen(conn, session.browserID);
                    ret.vertical = ShowingPairDao.countPairs(conn,
                                                  session.browserID, "v");
                    ret.horizontal = ShowingPairDao.countPairs(conn,
                                                  session.browserID, "h");
                }
            }
            return ret;
        }

        // TODO - COLLISION network jitter may lead to collision here
        //                  if user clicks a lot and things arrive all at once
        // synchronized (anObject) would be a club

        log.info("new SeenIds browserID " + session.browserID);

        ret = new SeenIds();
        if (force  ||  !session.repeatPics) {
            ret.seen = ShowingPairDao.getSeen(conn, session.browserID);
            ret.vertical = ShowingPairDao.countPairs(conn,
                                                  session.browserID, "v");
            ret.horizontal = ShowingPairDao.countPairs(conn,
                                                  session.browserID, "h");
        }
        browserSeen.put(session.browserID, ret);

        log.info("new SeenIds browserID " + session.browserID +
                    " seen " +
                            (ret.seen == null ? "null" :
                                " size " + ret.seen.size()) +
                    " v: " + ret.vertical + " h: " + ret.horizontal);

        return ret;
    }

    // 'seen' is app-dependent, Concept/Feeling.
    // TODO move more code to MirrorJuice?

    // TODO - could be in MirrorJuice?

    public int getPctSeen(Connection conn, Session session,
                                           int viewNum, String orient)
            throws SQLException {
        SeenIds seenids = getSeen(conn, session);
        if (seenids.seen == null  ||  seenids.seen.size() == 0) {
            return 0;
        }
        Set<String> picSet = ServletData.getPicSet(viewNum, orient);
        if (picSet == null  ||  picSet.size() == 0) {
            return -1;
        }
        return (100 * seenids.seen.size()) / picSet.size();
    }

    @Override
    public void addSeen(Connection conn, long browserID, String orient,
                        String... ids)
            throws SQLException {

        log.info("addSeen " + browserID + " " + Arrays.toString(ids));

        if (ids.length == 0) {
            log.error("NO IDS");
            throw new IllegalArgumentException("NO IDS");
        }
        SeenIds set = browserSeen.get(browserID);
        if (set == null) {
            log.info("SET NULL " + browserID);
            set = new SeenIds();
            browserSeen.put(browserID, set);
        }
        if (set.seen == null) {
            // TODO - roll into 1 query
            set.seen = ShowingPairDao.getSeen(conn, browserID);
            set.vertical = ShowingPairDao.countPairs(conn,
                                                  browserID, "v");
            set.horizontal = ShowingPairDao.countPairs(conn,
                                                  browserID, "h");
        }
        for (String id : ids) {
            set.seen.add(id);
        }
        if ("v".equals(orient)) {
            set.vertical += ids.length;
        } else {
            set.horizontal += ids.length;
        }

        log.info("AddSeen: " + set.seen.size());

        // by the time we are adding is a good time to
        // purge any exclude
        set.exclude.clear();
    }

    private static Map<Long, Integer> browserLastOrderInSession =
                                                        new HashMap<>();

    @Override
    public int getOrderInSession(Connection conn, long browserID)
            throws SQLException {

        synchronized(browserLastOrderInSession) {

            Integer order = browserLastOrderInSession.get(browserID);
            if (order == null) {
                int n = ShowingPairDao.countPairs(conn, browserID);
                order = n + 1;
            } else {
                order++;
            }
            browserLastOrderInSession.put(browserID, order);
            return order;
        }
    }

    // -- end of Overrides

    private static ConceptMirror instance = null;

    public static ConceptMirror getMirror() {
        synchronized(ConceptMirror.class) {
            if (instance == null) {
                instance = new ConceptMirror();
            }
        }
        return instance;
    }

    private ConceptMirror() {

        super();

        log.info("ConceptMirror Init OK");
    }
}
