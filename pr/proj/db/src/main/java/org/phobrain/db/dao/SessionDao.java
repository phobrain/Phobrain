package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  SessionDao  - pr.session
 **/

import org.phobrain.util.RandomUtil;

import org.phobrain.db.record.User;
import org.phobrain.db.record.Session;
import org.phobrain.db.record.Screen;
import org.phobrain.db.record.Browser;

import java.util.List;
import java.util.ArrayList;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionDao extends DaoBase {

    private final static String RECORD_FIELDS =
        " id, create_time, ";

    private final static String SESSION_FIELDS =
        " host, browser_version, tag, hour, tzoff, " +
        " lang, platform, pr_user, kwd_choice, pic_repeats ";


    private static final Logger log = LoggerFactory.getLogger(SessionDao.class);

    public static String insertSessionIf(Connection conn, User u, String tag,
                int hour, int tzoff,
                String lang, String platform,
                String kwdChoice, List<Screen> screens) throws SQLException {

        log.info("insertSession id " + u.browserID + " tag " + tag);

        if (tag == null) {
            throw new SQLException("No session tag");
        }

        long id = getSessionIdByTag(conn, tag);

        if (id == -1) {
            insertSessionEntry(conn, tag, u.ipAddr, u.browserID,
                          hour, tzoff, lang, platform, u.name,
                          kwdChoice, screens);
        }

        return tag;
    }

    private final static String SQL_GET_SESSION_ID_BY_TAG =
        "SELECT id FROM pr.session WHERE tag = ?";

    public static long getSessionIdByTag(Connection conn, String sessionTag)
                 throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(SQL_GET_SESSION_ID_BY_TAG,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, sessionTag);
            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getLong(1);
            }
            return -1;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_GET_SESSION_BY_TAG =
        "SELECT " + RECORD_FIELDS + SESSION_FIELDS +
        " FROM pr.session WHERE tag = ?";

    private static final String SQL_GET_SESSION_BY_BROWSER =
        "SELECT " + RECORD_FIELDS + SESSION_FIELDS +
        " FROM pr.session WHERE browser_version = ?";

    private static final String SQL_GET_SESSION_SCREENS =
        "SELECT session_id, id, depth," +
        "  orientation, pic_id, sel_method, showing_id, utime" +
        " FROM pr.session_screen WHERE session_id = ?" +
        " ORDER BY id ASC";

    private static Screen screenFromResultSet(ResultSet rs)
                 throws SQLException {

        // Screen(long bid, long sid, int id, String o, String id_s,
        //          long shid, Object pr)

        long sid = rs.getLong(1);       // session_id
        int id = rs.getInt(2);          // id
        String o = rs.getString(4);     // orient
        String id_s = rs.getString(5);  // pic_id (archive/seq)
        long shid = rs.getLong(7);      // showing_id

        //log.info("screenFromResultSet: id " + id + " orient " + o + " id_s " + id_s + " shid " + shid);

        if (id_s == null  ||  id_s.length() < 3) {

            log.info("Null/short id [" + id_s + "], sid,o,shid: " +
                                    sid + ","+o+","+shid);
            id_s = null;

        }
/*
        else if (id_s.contains(":")) {

            log.info("Legacy id, setting to null: " + id_s);
            id_s = null;
        }
*/

        Screen s = new Screen(-1, id, o, id_s, null);

        s.depth = rs.getInt(3);
        s.selMethod = rs.getString(6);
        s.time = rs.getTimestamp(8);
/*
        log.info("screenFromResultSet: " + s.id_s +
                    " depth " + s.depth +
                    // always -1, why? boring. " browser " + s.browserId +
                    " method " + s.selMethod);
*/
        return s;
    }

    private static Session sessionFromResultSet(ResultSet rs)
                 throws SQLException {

        Session s = new Session();

        s.id = rs.getLong(1);
        s.createTime = rs.getTimestamp(2);
        s.host = rs.getString(3);
        s.browserID = rs.getLong(4);
        s.tag = rs.getString(5);
        s.hour = rs.getInt(6);
        s.tzoff = rs.getInt(7);
        s.lang = rs.getString(8);
        s.platform = rs.getString(9);
        s.user = rs.getString(10);
        s.kwdChoice = rs.getString(11);
        s.repeatPics = rs.getBoolean(12);

        return s;
    }

    public static Session getSessionByTag(Connection conn, String sessionTag)
                 throws SQLException {

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(SQL_GET_SESSION_BY_TAG,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setString(1, sessionTag);
            rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            Session s = sessionFromResultSet(rs);

            closeSQL(rs); rs = null;
            closeSQL(ps); ps = null;

            ps = conn.prepareStatement(SQL_GET_SESSION_SCREENS,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            //log.info("SQL_GET_SESSION_SCREENS:\n" +
            //        SQL_GET_SESSION_SCREENS + " s.id " + s.id);
            ps.setLong(1, s.id);
            s.screens = new ArrayList<Screen>();
            rs = ps.executeQuery();
            while (rs.next()) {
                s.screens.add(screenFromResultSet(rs));
            }

            //log.info("session " + s.id + " nscreens " + s.screens.size());

            return s;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    public static Session getSessionByBrowser(Connection conn,
                                              long browserID)
                 throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(SQL_GET_SESSION_BY_BROWSER,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setLong(1, browserID);
            rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            Session s = sessionFromResultSet(rs);

            closeSQL(ps);

            ps = conn.prepareStatement(SQL_GET_SESSION_SCREENS,
                                       ResultSet.TYPE_SCROLL_INSENSITIVE,
                                       ResultSet.CONCUR_UPDATABLE);
            ps.setLong(1, s.id);
            s.screens = new ArrayList<Screen>();
            rs = ps.executeQuery();
            while (rs.next()) {
                s.screens.add(screenFromResultSet(rs));
            }

            return s;
        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    final static String SQL_UPDATE_KWD_CHOICE =
        "UPDATE pr.session SET kwd_choice = ? WHERE id = ?";

    public static void updateKwdChoice(Connection conn, Session session)
            throws SQLException {
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(SQL_UPDATE_KWD_CHOICE);
            ps.setString(1, session.kwdChoice);
            ps.setLong(2, session.id);
            int ret = ps.executeUpdate();
            if (ret != 1) {
                throw new SQLException("Update returned " + ret);
            }
        } finally {
            closeSQL(ps);
        }
    }

    final static String SQL_UPDATE_REPEAT_PICS =
        "UPDATE pr.session SET pic_repeats = ? WHERE id = ?";

    public static void toggleRepeatPics(Connection conn, Session session)
            throws SQLException {
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(SQL_UPDATE_REPEAT_PICS);
            ps.setBoolean(1, !session.repeatPics);
            ps.setLong(2, session.id);
            int ret = ps.executeUpdate();
            if (ret != 1) {
                throw new SQLException("Update returned " + ret);
            }
        } finally {
            closeSQL(ps);
        }
        session.repeatPics = !session.repeatPics;
        log.info("toggleRepeatPics: " + session.browserID + " " +
                  session.repeatPics);
    }

    /////////////////////////// private, don't look /////////////////////////


    private final static String SQL_INSERT_SESSION =
        "INSERT INTO pr.session " +
        " (tag, host, browser_version, hour, tzoff, lang, " +
        "  platform, pr_user, kwd_choice)" +
        " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final static String SQL_INSERT_SESSION_SCREEN =
        "INSERT INTO pr.session_screen " +
        " (session_id, id, depth, orientation, pic_id," +
        "  sel_method, showing_id, utime)" +
        " VALUES (?, ?, ?, ?, ?, ?, ?, now())";

    private static void insertSessionEntry(Connection conn, String tag,
                String remoteAddr, long version_id, int hour, int tzoff,
                String lang, String platform, String user, String kwdChoice,
                List<Screen> screens)
            throws SQLException {

        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(SQL_INSERT_SESSION,
                                       Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, tag);
            ps.setString(2, remoteAddr);
            ps.setLong(3, version_id);
            ps.setInt(4, hour); // TINYINT
            ps.setInt(5, tzoff); // TINYINT
            ps.setString(6, lang);
            ps.setString(7, platform);
            ps.setString(8, user);
            ps.setString(9, kwdChoice);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Insert returned 0");
            }
            log.info("Inserted kwdchoice " + kwdChoice);

            ResultSet generatedKeys = ps.getGeneratedKeys();
            long id = -1;
            if (generatedKeys.next()) {
                id = generatedKeys.getLong(1);
            } else {
                throw new SQLException("Insert session failed, no ID obtained.");
            }

            if (screens != null) {
                closeSQL(ps);

                ps = conn.prepareStatement(SQL_INSERT_SESSION_SCREEN);
                //ps.setLong(1, version_id); // browser
                ps.setLong(1, id);
                int i = 0;
                for (Screen screen : screens) {
                    if (screen.id != 1  &&  screen.id != 2) {
                        log.error("BAD SCREENID " + screen.id);
System.exit(1);
                    }
                    ps.setInt(2, screen.id);
                    ps.setInt(3, 0);
                    ps.setString(4, screen.orientation);
                    ps.setString(5, screen.id_s);
                    ps.setString(6, screen.selMethod);
                    ps.setLong(7, screen.showingId);

                    rows = ps.executeUpdate();
                    if (rows != 1) {
                        throw new SQLException("Insert returned " + rows);
                    }

                    ps.setInt(2, screen.id);
                    ps.setInt(3, 1);
                    ps.setString(4, screen.orientation);
                    ps.setString(5, null);
                    ps.setString(6, null);
                    ps.setLong(7, -1L);

                    rows = ps.executeUpdate();
                    if (rows != 1) {
                        throw new SQLException("Insert 2 returned " + rows);
                    }
                }
                log.info("Inserted screens (depth 2): " + screens.size());
            }

        } finally {
            closeSQL(ps);
        }
    }

    private final static String SQL_UPDATE_SESSION_SCREEN1 =
        "UPDATE pr.session_screen SET depth = -1" +
        " WHERE session_id = ? AND id = ? AND depth = 0";

    private final static String SQL_UPDATE_SESSION_SCREEN2 =
        "UPDATE pr.session_screen SET pic_id = ?, sel_method = ?, " +
        "  showing_id = ?, utime = now()," +
        "  depth = 0" +
        " WHERE session_id = ? AND id = ? AND depth = 1";

    private final static String SQL_UPDATE_SESSION_SCREEN3 =
        "UPDATE pr.session_screen SET depth = 1" +
        " WHERE session_id = ? AND id = ? AND depth = -1";

    public static void updateSessionScreen(Connection conn, Screen screen)
            throws SQLException {

        if (screen.sessionId == -1) {
            throw new SQLException("updateSessionScreen: sessionId==-1");
        }

        PreparedStatement ps = null;
        try {
            // FIX - someday this will NPE after crash partway through this
            // and continue user session
            ps = conn.prepareStatement(SQL_UPDATE_SESSION_SCREEN1);
            ps.setLong(1,   screen.sessionId);
            ps.setInt(2,    screen.id);
            int rows = ps.executeUpdate();
            if (rows != 1) {
                // don't worry - this is a reset that might already have happ
                log.warn("updSessionScreen/1 update rows != 1: "
                                       + rows + ": sessid/screenid: " +
                                       screen.sessionId + "/" +
                                       screen.id);
            }
            ps.close();

            ps = conn.prepareStatement(SQL_UPDATE_SESSION_SCREEN2);
            ps.setString(1,    screen.id_s);
            ps.setString(2,    screen.selMethod);
            ps.setLong(3,      screen.showingId);
            ps.setLong(4,      screen.sessionId);
            ps.setInt(5,       screen.id);
            rows = ps.executeUpdate();
            if (rows != 1) {
                // blind retry
                log.error("updSessionScreen/2 update rows != 1: " + rows + ": - retry once");
                rows = ps.executeUpdate();
                if (rows != 1) {
                    log.error("updSessionScreen/2 update rows != 1: " + rows + ": - Done piddling around");
/*
                    throw new SQLException("updSessionScreen/2 update rows != 1: "
                                       + rows + ":\n" +
                                       " session " + screen.sessionId +
                                       " screenid " + screen.id +
                                       " depth 1, set to " + screen.id_s +
                                       " method " + screen.selMethod +
                                       " showingid " + screen.showingId);
*/
                }
            }
            ps.close();
            screen.time = null; // just updated to now() in db

            ps = conn.prepareStatement(SQL_UPDATE_SESSION_SCREEN3);
            ps.setLong(1,   screen.sessionId);
            ps.setInt(2,    screen.id);
            rows = ps.executeUpdate();
            if (rows != 1) {
                throw new SQLException("updateSessionScreen/3 update rows != 1: "
                                       + rows + ": " + screen.sessionId + " " +
                                       screen.id);
            }
        } finally {
            closeSQL(ps);
        }
    }

}

