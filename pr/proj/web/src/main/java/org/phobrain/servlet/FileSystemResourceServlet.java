package org.phobrain.servlet;

/**
 **  Based on:
 **    http://stackoverflow.com/questions/132052/servlet-for-serving-static-content
 **  By:
 **    BaulusC, https://balusc.omnifaces.org/
 **  License:
 **    https://creativecommons.org/licenses/by-sa/4.0/
 **
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **  SPDX-License-Identifier: CC-BY-SA-4.0
 **/

/**
 **  Simple file server.
 **
 */

import org.phobrain.util.ConfigUtil;

import java.util.List;
import java.util.ArrayList;

import java.net.URLDecoder;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/pr/home/*")
public class FileSystemResourceServlet extends StaticResourceServlet {

    private static final Logger log = LoggerFactory.getLogger(
                                            FileSystemResourceServlet.class);

    private File folder;
    private String imgDir;

    @Override
    public void init() throws ServletException {
        String webHomeDir = ConfigUtil.runtimeProperty("web.home.dir");
        folder = new File(webHomeDir);
        if (!folder.isDirectory()) {
            log.error("CONFIG: EXITING: web.home.dir [" + webHomeDir + 
                                       "] IS NOT A DIRECTORY");
            System.exit(1);
        }
        imgDir = ConfigUtil.runtimeProperty("images.dir");
        File imgFolder = new File(imgDir);
        if (!imgFolder.isDirectory()) {
            log.error("CONFIG: images.dir [" + imgDir + 
                                       "] IS NOT A DIRECTORY, EXITING: path=" + imgFolder.getPath());
            System.exit(1);
        }

    }

    @Override
    protected StaticResource getStaticResource(HttpServletRequest request) 
               throws IllegalArgumentException {

        String queryString = request.getQueryString();
        String pathInfo = request.getPathInfo();
        String host = request.getRemoteHost();

        if (queryString != null) {
            log.info("QUERY " + queryString + " Path " + pathInfo + " host " + host);
        }


        // IP exclusion

        String excludeReason = ServletUtil.checkExcludeIP(host, false);
        if (excludeReason != null) {
            log.warn("EXCLUDE " + host + " wanting " + pathInfo);
            throw new IllegalArgumentException(excludeReason);
        }

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

        // another IP check

        if (ServletUtil.failAdminPage(name, host)) {
            log.error("Admin page w/o ip: " + name + " / " + host);
            throw new IllegalArgumentException("Admin page w/o ip");
        }
        //log.info("admin check ok: " + name + "/" + host);

        File ftmp = null;
        if (name.startsWith("@@")) {
            int i = name.indexOf('@', 2);
            String arch = name.substring(2, i);
            String f = name.substring(i+1);
            ftmp = new File(imgDir + "/" + arch + "/" + f);
        } else {
            ftmp = new File(folder, name);
        }

        boolean tgzip = false;

        if (!ftmp.exists()) {
            File tf = new File(ftmp.getPath() + ".gz");
            if (tf.exists()) {
                log.info("Found .gz: " + name);
                tgzip = true;
                ftmp = tf;
            } else {
                log.error("Couldn't find: " + name + 
                          " (" + ftmp.getAbsolutePath() + "): " + 
                          request.getRemoteAddr());
                return null;
            }
        }
        final boolean gzip = tgzip;
        if (ftmp.isDirectory()) {
            if (name.endsWith("/")) {
                log.error("Dir, trying +/index.html: " + name + 
                          " (" + ftmp.getAbsolutePath() + "): " + 
                          request.getRemoteAddr());
                name += "index.html";
                ftmp = new File(folder, name);
                if (!ftmp.exists()) {
                    log.error("Couldn't find: " + name +
                              " (" + ftmp.getAbsolutePath() + "): " +
                              request.getRemoteAddr());
                    return null;
                }
            } else {
                ftmp = new File(folder, name + ".html");
                if (!ftmp.exists()) {

                    ftmp = new File(folder, name + "/index.html");
                    if (ftmp.exists()) {
                        log.warn("Is dir, no '/': " + name + 
                                 ", have '/index.html'");
                        throw new IllegalArgumentException(
                                      "Try adding '/' to URL: " + name + "/");
                    }
                }
            }
        }
        log.info("Mapping " + name + " to " + ftmp.getAbsolutePath() + 
                       " " + request.getRemoteAddr());

        final File file = ftmp;
        final String finalName = name;

        boolean cache = false;
        //if ((name.endsWith(".jpg")  ||  name.endsWith(".JPG"))  &&
        if (name.startsWith("gallery/")) {
            cache = true;
//log.info("--CACHE");
        }
        final boolean cacheit = cache;

        return new StaticResource() {
            @Override
            public boolean getCache() {
                return cacheit;
            }
            @Override
            public long getLastModified() {
                return System.currentTimeMillis();
            }
            @Override
            public InputStream getInputStream() throws IOException {
                return new FileInputStream(file);
            }
            @Override
            public String getFileName() {
                return finalName;
            }
            @Override
            public long getContentLength() {
                return file.length();
            }
            @Override
            public boolean getGzip() {
                return gzip;
            }
        };
    }

}
