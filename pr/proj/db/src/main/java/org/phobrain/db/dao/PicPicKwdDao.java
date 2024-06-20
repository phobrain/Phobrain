package org.phobrain.db.dao;

/*
 *  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-or-later
 */

import org.phobrain.db.record.PicPicKwd;

import java.util.List;
import java.util.ArrayList;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PicPicKwdDao extends DaoBase {

    private static final Logger log = LoggerFactory.getLogger(
                                                 PicPicKwdDao.class);

    private final static String PICPIC_KWD_FIELDS =
        " coder, id1, id2, closeness ";

    private final static String SQL_INSERT_PICPIC_KWD =
        "INSERT INTO pic_pic_kwd (" + PICPIC_KWD_FIELDS + 
                                ") VALUES (?, ?, ?, ?)";

    public static void insertPicPicKwd(Connection conn, PicPicKwd ppk) 
                throws SQLException {

        if (ppk.id1.equals(ppk.id2)) {
            throw new SQLException("id1==id2: " + ppk.id1);
        }
        //if (NOOP) return;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(SQL_INSERT_PICPIC_KWD,
                                      Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1,    ppk.coder);
            ps.setString(2, ppk.id1);
            ps.setString(3, ppk.id2);
            ps.setLong(4,   ppk.closeness);

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Insert returned 0");
            }
            return;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

    private static final String SQL_PICPIC_KWD_Q3  =
        "SELECT " + PICPIC_KWD_FIELDS + " FROM pic_pic_kwd " +
        "WHERE coder = ? AND id1 = ? ORDER BY closeness @ #";

    public static List<PicPicKwd> getPicPicKwdByCoderAndId1(Connection conn, 
                          int coder, String id1, String dir, int limit)
                          throws SQLException {
//log.info("getPicPicKwdByCoderAndId1 " + id1 + " " + coder);
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            String query = SQL_PICPIC_KWD_Q3.replace("@", dir);
            if (limit > 0) {
                query = query.replace("#", "LIMIT " + limit);
            } else {
                query = query.replace("#", "");
            }
            ps = conn.prepareStatement(query);

            ps.setInt(1, coder);
            ps.setString(2, id1);

            rs = ps.executeQuery();

            List<PicPicKwd> picpic_kwd = new ArrayList<>();

            while (rs.next()) {

                //int coder = rs.getInt(1);
                //String id1 = rs.getString(2);
                String id2 = rs.getString(3);
                long closeness = rs.getLong(4);

                PicPicKwd ppk = new PicPicKwd(coder, id1, id2, closeness);

                picpic_kwd.add(ppk);
            }

            return picpic_kwd;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }


}

