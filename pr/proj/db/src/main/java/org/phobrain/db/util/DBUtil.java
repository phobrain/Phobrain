package org.phobrain.db.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  DBUtil - keyword routines, not used as of 2022/03
 **/

import org.phobrain.util.KwdUtil;

import org.phobrain.db.dao.KeywordsDao;
import org.phobrain.db.record.Keywords;
import org.phobrain.db.dao.ApprovalDao;
import org.phobrain.db.record.ApprovedPair;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBUtil {

    private static final Logger log = LoggerFactory.getLogger(DBUtil.class);

    public static String kwdCompare(Connection conn, String kwdCoder, 
                                          String id, String prevId)
            throws SQLException {

        // log the juicy details for kwd debug

        Keywords k = null;
        if (!"0".equals(kwdCoder)) {
            k = KeywordsDao.getKeywordsByIdCoder(conn, id, kwdCoder);
        }

        String kwdIntersect = "def";

        if (k == null) {

            kwdIntersect = "[no kwds]";

        } else {
            Keywords prevk = KeywordsDao.getKeywordsByIdCoder(conn, 
                                                               prevId, kwdCoder);

            if (prevk == null) {

                kwdIntersect = "(" + k.keywords + ")";

            } else {
                boolean nogeom = true;

                String[] words = k.keywords.split(" ");
                String[] prevwords = prevk.keywords.split(" ");

                Set<String> hw = new HashSet<>();
                for (String w : prevwords) {
                    String w2 = w;
                    if (nogeom) {
                        w2 = KwdUtil.stripGeom(w);
                    }
                    if (w2 != null) {
                        hw.add(w2);
                    }
                }

                StringBuilder sb = new StringBuilder();
                StringBuilder sb2 = new StringBuilder();
                StringBuilder sb3 = new StringBuilder();
                for (String w : words) {
                    if (nogeom  &&  KwdUtil.stripGeom(w) == null) {
                        sb3.append(w).append(" ");
                    } else if (hw.contains(w)) {
                        sb.append(w).append(" ");
                    } else {
                        sb2.append(w).append(" ");
                    }
                }
                if (sb.length() > 0) {
                    kwdIntersect = sb.toString().trim();
                } else {
                    //log.info("no intersect w/ prev: " + prevk.keywords);
                    kwdIntersect = "[" + prevk.keywords + "]";
                }
                if (sb2.length() > 0) {
                    kwdIntersect += " (" + sb2.toString().trim() + ")";
                }
                if (sb3.length() > 0) {
                    kwdIntersect += " [" + sb3.toString().trim() + "]";
                }
            }
        }
        return prevId + " " + id + " - " + kwdIntersect;
    }

    public static Set<String> kwdCompare2(Connection conn, 
                                            String kwdCoder, boolean nogeom, 
                                            String id, String prevId)
            throws SQLException {

        // log the juicy details for kwd debug

        Keywords k = null;
        if (!"0".equals(kwdCoder)) {
            k = KeywordsDao.getKeywordsByIdCoder(conn, id, kwdCoder);
        }

        if (k == null) {

            log.warn("NO KWDS for " + id);
            return null;

        }
        Keywords prevk = KeywordsDao.getKeywordsByIdCoder(conn, 
                                                          prevId, kwdCoder);

        if (prevk == null) {

            log.warn("NO KWDS for prev " + prevId);
            return null;

        }

        String[] words = k.keywords.split(" ");
        String[] prevwords = prevk.keywords.split(" ");

        Set<String> hw = new HashSet<>();
        for (String w : prevwords) {
            String w2 = w;
            if (nogeom) {
                w2 = KwdUtil.stripGeom(w);
            }
            if (w2 != null) {
                hw.add(w2);
            }
        }

        Set<String> ret = new HashSet<>();
        for (String w : words) {
            boolean isGeom = KwdUtil.stripGeom(w) == null;
            if (nogeom  &&  isGeom) {
                continue;
            }
            if (!nogeom  &&  !isGeom) {
                continue;
            }
            if (hw.contains(w)) {
                ret.add(w);
            }
        }
        //if (ret.size() == 0) {
        //    log.warn("NO INTERSECT KWDS for prev " + prevId + " w/ " + id);
        //}
        return ret;
    }

    public static String kwdCompare(Connection conn, String kwdCoder, 
                                          String id,
                                          String prevId0, String prevId1)
            throws SQLException {

        // log the juicy details for kwd debug

        Keywords k = null;
        if (!"0".equals(kwdCoder)) {
            k = KeywordsDao.getKeywordsByIdCoder(conn, id, kwdCoder);
        }

        String kwdIntersect = "def";

        if (k == null) {

            kwdIntersect = "[no kwds]";

        } else {

            String pk = "";

            Keywords prevk = KeywordsDao.getKeywordsByIdCoder(conn, 
                                                          prevId0, kwdCoder);
            if (prevk != null) {
                pk += prevk.keywords;
            }
            prevk = KeywordsDao.getKeywordsByIdCoder(conn, prevId1, kwdCoder);
            if (prevk != null) {
                pk += " " + prevk.keywords;
            }
            pk = pk.trim();
            pk = pk.replaceAll("( )+", " ");

            if (pk.length() == 0) {

                kwdIntersect = "(" + k.keywords + ")";

            } else {
                boolean nogeom = true;

                String[] words = k.keywords.split(" ");
                String[] prevwords = pk.split(" ");

                Set<String> hw = new HashSet<>();
                for (String w : prevwords) {
                    String w2 = w;
                    if (nogeom) {
                        w2 = KwdUtil.stripGeom(w);
                    }
                    if (w2 != null) {
                        hw.add(w2);
                    }
                }

                StringBuilder sb = new StringBuilder();
                StringBuilder sb2 = new StringBuilder();
                StringBuilder sb3 = new StringBuilder();
                for (String w : words) {
                    if (nogeom  &&  KwdUtil.stripGeom(w) == null) {
                        sb3.append(w).append(" ");
                    } else if (hw.contains(w)) {
                        sb.append(w).append(" ");
                    } else {
                        sb2.append(w).append(" ");
                    }
                }
                if (sb.length() > 0) {
                    kwdIntersect = sb.toString().trim();
                } else {
                    //log.info("no intersect w/ prev: " + prevk.keywords);
                    kwdIntersect = Arrays.toString(hw.toArray());// has brackets
                }
                if (sb2.length() > 0) {
                    kwdIntersect += " (" + sb2.toString().trim() + ")";
                }
                if (sb3.length() > 0) {
                    kwdIntersect += " [" + sb3.toString().trim() + "]";
                }
            }
        }
        return prevId0 + "|" + prevId1 + " - " + kwdIntersect;
    }
}
