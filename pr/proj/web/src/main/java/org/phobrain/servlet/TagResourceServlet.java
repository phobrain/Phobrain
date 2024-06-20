package org.phobrain.servlet;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  TagResourceServlet - view.html, curate.html picture-getter,
 **     using per-session tags via database,
 **     to hide identity of photos.
 **/

import org.phobrain.util.ConfigUtil;

import org.phobrain.db.record.Picture;
import org.phobrain.db.record.PictureMap;
import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.PictureDao;
import org.phobrain.db.dao.PictureMapDao;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import java.net.URLDecoder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;

import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.annotation.WebServlet;

import javax.naming.NamingException;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/pr/images/*")
public class TagResourceServlet extends StaticResourceServlet {

    private String imagesDir;

    private static final Logger log = LoggerFactory.getLogger(
                                                    TagResourceServlet.class);
    private static final Random rand = new Random();

    private static final GetEngine engine = GetEngine.getEngine();

    @Override
    public void init() throws ServletException {
        imagesDir = ConfigUtil.runtimeProperty("images.dir");
        File folder = new File(imagesDir);
        if (!folder.isDirectory()) {
            log.error("CONFIG: images.dir [" + imagesDir + 
                                       "] IS NOT A DIRECTORY - EXITING");
            System.exit(1);
        }
    }

    @Override
    protected StaticResource getStaticResource(HttpServletRequest request) 
               throws IllegalArgumentException {
        String pathInfo = request.getPathInfo();

        if (pathInfo == null || pathInfo.isEmpty() || "/".equals(pathInfo)) {
            throw new IllegalArgumentException();
        }

        String name = null;
        try {
            name = URLDecoder.decode(pathInfo.substring(1), 
                                     StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException("Encoding: " + uee, uee);
        }
        if ("undefined".equals(name)) {
            throw new IllegalArgumentException("Name undefined from pathinfo: " + pathInfo);
        }

//log.info("$$$ name is " + name);

        int i = name.indexOf('/');
        String id2 = null;
        if (i != -1) {
            id2 = name.substring(i+1);
            name = name.substring(0, i);
        }
//log.info("$$$ name.2 is " + name);

        String remoteHost = request.getRemoteHost();

        // enter the db

        File file = null;
        Connection conn = null;
        try {
            conn = DaoBase.getConn();
            PictureMap pm = PictureMapDao.getPictureMap(conn, name);
            if (pm == null) {
                log.error("DANGER: UNKNOWN HASH ATTACK [" + name + "] " + 
                      remoteHost); // maybe hash oil
                // TODO - count up to 1000 incidents then block IP
                return null;
            }
            // TODO - comment this log out:
            log.info("TIME " + 
                  (System.currentTimeMillis() - pm.createTime.getTime()) + 
                  " " + remoteHost);

            // not so much an issue since hash is only used for workbench
            if (System.currentTimeMillis() - pm.createTime.getTime() > 
                                         TimeUnit.DAYS.toMillis(14)) {
                log.error("DANGER: OLD HASH ATTACK (>14 days) [" + 
                      name + "] AGE " + 
                      (System.currentTimeMillis() - pm.createTime.getTime()) + 
                      " " + remoteHost);
                // TODO - count up to 1000 incidents then block IP
                return null;
            }

            if (id2 != null) {
                Picture p = PictureDao.getPictureById(conn, id2);
                file = new File(imagesDir + "/" + p.archive + "/" + p.fileName);

            } else {
                file = new File(imagesDir + "/" + 
                                   pm.archive + "/" + pm.fileName);
            }

            if (!file.exists()) {
                log.error("Internal Error: Couldn't find: " + 
                                file.getAbsolutePath());
                return null;
            }

        } catch (NamingException ne) {
            log.error("getStaticResource: Naming", ne);
            return null;
        } catch (SQLException sqe) {
            log.error("getStaticResource: DB: " + sqe, sqe);
            return null;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignore) {}
            }
        }

        log.info("Mapping " + name + " to " + file.getAbsolutePath() +
                 " for " + remoteHost);

        final File finalFile = file;

        return new StaticResource() {
            @Override
            public boolean getCache() {
                return true;
            }
            @Override
            public long getLastModified() {
                // within last 22.21558 days :-)
                return System.currentTimeMillis() - rand.nextInt(1919426112); 
            }
            @Override
            public InputStream getInputStream() throws IOException {
                return new FileInputStream(finalFile);
            }
            @Override
            public String getFileName() {
                return "resized_" + rand.nextInt(7) + "_" + 
                       rand.nextInt(100000) + ".jpg"; // :-)
            }
            @Override
            public long getContentLength() {
                return finalFile.length();
            }
            @Override
            public boolean getGzip() {
                return false; // jpgs for now
            }
        };
    }

}
