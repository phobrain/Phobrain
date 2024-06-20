package org.phobrain.db.dao;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  IPDao  - ips
 **/

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IPDao extends DaoBase {

    private static final Logger log = LoggerFactory.getLogger(
                                                 IPDao.class);

    private final static String SQL_INSERT_IP =
        "INSERT INTO pr.ips (ip) SELECT ? WHERE NOT EXISTS " +
        "(SELECT 1 FROM pr.ips WHERE ip = ?)";

    public static boolean insertIP(Connection conn, String ip)
                                                  throws SQLException {
        
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(SQL_INSERT_IP);
            ps.setString(1, ip);
            ps.setString(2, ip);
            int rows = ps.executeUpdate();
            if (rows != 1) {
                log.info("Insert returned " + rows + " " + ip);
                return false;
            }
            return true;

        } finally {
            closeSQL(rs);
            closeSQL(ps);
        }
    }

}

