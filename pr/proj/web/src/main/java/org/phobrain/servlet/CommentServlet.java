package org.phobrain.servlet;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  CommentServlet - a good thing in principle, but this one w/out
 **    human verification is just a robo-probe/spam collector,
 **    in light of log4j bugs. Switched from log4j a while back,
 **    maybe have changed hosting since, fingers crossed but nothing
 **    goes from web site back into the distribution except edited
 **    html, which is diffed first.
 **
 */

import org.phobrain.util.ConfigUtil;

import org.phobrain.db.record.Comment;
import org.phobrain.db.dao.DaoBase;
import org.phobrain.db.dao.CommentDao;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import java.net.URLEncoder;

import java.util.Enumeration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;

import javax.naming.NamingException;

import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.annotation.WebServlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/pr/home/comment")
public class CommentServlet extends HttpServlet {

    private static String commentFile = null;

    private static final Logger log = LoggerFactory.getLogger(
                                                    CommentServlet.class);

    @Override
    public void init(ServletConfig config) throws ServletException {
/*
log.info(" -- initial params");
Enumeration<String> e = config.getInitParameterNames();
while(e.hasMoreElements()) {
  String s = e.nextElement();
  log.info(" P " + s + "=" + config.getInitParameter(s));
}
log.info(" -- context attributes");
e = config.getServletContext().getAttributeNames();
while(e.hasMoreElements()) {
  String s = e.nextElement();
  log.info(" Q " + s + "=" + config.getServletContext().getInitParameter(s));
}
*/
        try {
            commentFile = ConfigUtil.runtimeProperty("local.dir") + "/" +
                          ConfigUtil.runtimeProperty("comment.file");
        } catch (RuntimeException re) {
            log.error("CommentServlet " + re);
            log.error("Getting comment file - EXITING", re);
            System.exit(1);
        }
        log.info("CommentServlet Init OK, file is " + commentFile);
    }

    @Override
    public String getServletInfo() {
        return "CommentServlet";
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException  {

        String remoteAddr = req.getRemoteAddr();

        // get past the gatekeeper

        Connection conn = null;
        try {
            conn = DaoBase.getConn();
            List<Comment> l = CommentDao.getByIP(conn, remoteAddr);
            Comment com = (l.size() == 0 ? null : l.get(0));
            if (com == null) {
                com = new Comment(remoteAddr);
                CommentDao.insert(conn, com);
            } else {
                if (com.count > 10) {
                    log.error("Comment Count > 10: " + remoteAddr);
                    res.sendError(403,
                          "More than 10 comments from this address - " +
                          "please wait for reset");
                    return;
                }
                CommentDao.incrementCount(conn, com);
            }
        } catch (NamingException ne) {
            log.error("getStaticResource: Naming", ne);
        } catch (SQLException sqe) {
            log.error("getStaticResource: DB: " + sqe, sqe);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignore) {}
            }
        }

        String name = req.getParameter("name");
        String email = req.getParameter("email");
        String comment = req.getParameter("comment");

        log.info("COMMENT to " + commentFile + ": [" + name + "] [" + email + "] [" + comment +
                          "] [" + remoteAddr + "]");

        PrintWriter out = null;
        try {
            out = new PrintWriter(new FileWriter(commentFile, true));
            out.println("-- " + new Date() + "  " + remoteAddr);
            out.println("NAME : " + name);
            out.println("EMAIL: " + email);
            out.println("COMMENT: " + comment);
        } finally {
            if (out != null) {
                try { out.close(); } catch (Exception ignore) {}
            }
        }
        try {
            res.setContentType( "text/html" );
            out = res.getWriter();
            out.println("<html><head><title>Thanks</title></head>" +
                       "<body><h1>Thanks for your comment!</h1></body></html>");
        } catch (Exception e) {
            log.error("Responding: " + e);
        } finally {
            if (out != null) {
                try { out.close(); } catch (Exception ignore) {}
            }
        }
    }
}
