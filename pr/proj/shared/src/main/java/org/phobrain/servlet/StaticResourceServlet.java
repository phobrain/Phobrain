package org.phobrain.servlet;

/*
 *  SPDX-FileCopyrightText: 2015 BaulusC, https://balusc.omnifaces.org/
 *
 *  SPDX-License-Identifier: CC-BY-SA-4.0
 */

// Original Source:
// http://stackoverflow.com/questions/132052/servlet-for-serving-static-content

import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;

import java.net.URLEncoder;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
/*
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
*/

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.*;
/*
.Logger;
import org.slf4j.LoggerFactory;
*/
public abstract class StaticResourceServlet extends HttpServlet {

    private static final Logger log = 
                         LoggerFactory.getLogger(StaticResourceServlet.class);

    private static final long serialVersionUID = 1L;
    private static final long ONE_SECOND_IN_MILLIS = TimeUnit.SECONDS.toMillis(1);
    private static final String ETAG_HEADER = "W/\"%s-%s\"";
    private static final String CONTENT_DISPOSITION_HEADER = "inline;filename=\"%1$s\"; filename*=UTF-8''%1$s";

    public static final long DEFAULT_EXPIRE_TIME_IN_MILLIS = TimeUnit.DAYS.toMillis(30);
    public static final int DEFAULT_STREAM_BUFFER_SIZE = 102400;

    @Override
    protected void doHead(HttpServletRequest request, 
                          HttpServletResponse response) 
            throws ServletException ,IOException {
        doRequest(request, response, true);
    }

    @Override
    protected void doGet(HttpServletRequest request, 
                         HttpServletResponse response) 
            throws ServletException, IOException {
        doRequest(request, response, false);
    }

    private void doRequest(HttpServletRequest request, 
                           HttpServletResponse response, 
                           boolean head) 
            throws IOException {
        response.reset();
        StaticResource resource;

        try {
            resource = getStaticResource(request);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
                               e.getMessage());
            return;
        }

        if (resource == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String fileName = URLEncoder.encode(resource.getFileName(), 
                                            StandardCharsets.UTF_8.name());
        boolean notModified = setCacheHeaders(request, response, fileName, 
                                              resource.getCache(),
                                              resource.getLastModified());

        if (notModified) {
            response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        setContentHeaders(response, fileName, resource);

        if (head) {
            return;
        }

        String remote = request.getRemoteAddr(); // cache for exceptions
        try {
            writeContent(response, resource);
        } catch (IOException ioe) {
            Cookie[] cookies = request.getCookies();
            log.info("IOException on write " + request.getPathInfo() + " to " + remote);
        } catch (TimeoutException te) {
            log.info("Timeout on write to " + remote);
        }
    }

    /**
     * Returns the static resource associated with the given HTTP servlet request. This returns <code>null</code> when
     * the resource does actually not exist. The servlet will then return a HTTP 404 error.
     * @param request The involved HTTP servlet request.
     * @return The static resource associated with the given HTTP servlet request.
     * @throws IllegalArgumentException When the request is mangled in such way that it's not recognizable as a valid
     * static resource request. The servlet will then return a HTTP 400 error.
     */
    protected abstract StaticResource getStaticResource(HttpServletRequest request) throws IllegalArgumentException;

    private boolean setCacheHeaders(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    String fileName, 
                                    boolean cache,
                                    long lastModified) {
        String eTag = String.format(ETAG_HEADER, fileName, lastModified);
        response.setHeader("ETag", eTag);
        if (cache) {
            response.setHeader("Cache-Control", "max-age=999999");
        } else {
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache"); 
        }
        response.setDateHeader("Last-Modified", lastModified);
        response.setDateHeader("Expires", System.currentTimeMillis() + DEFAULT_EXPIRE_TIME_IN_MILLIS);
        return notModified(request, eTag, lastModified);
    }

    private boolean notModified(HttpServletRequest request, String eTag, long lastModified) {
        String ifNoneMatch = request.getHeader("If-None-Match");

        if (ifNoneMatch != null) {
            String[] matches = ifNoneMatch.split("\\s*,\\s*");
            Arrays.sort(matches);
            return (Arrays.binarySearch(matches, eTag) > -1 || Arrays.binarySearch(matches, "*") > -1);
        }
        else {
            long ifModifiedSince = request.getDateHeader("If-Modified-Since");
            return (ifModifiedSince + ONE_SECOND_IN_MILLIS > lastModified); // That second is because the header is in seconds, not millis.
        }
    }

    private void setContentHeaders(HttpServletResponse response, 
                                   String fileName, 
                                   StaticResource resource) {
//response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Content-Type", 
                           getServletContext().getMimeType(fileName));
        response.setHeader("Accept-Charset",  "utf-8");
        response.setHeader("Content-Disposition", 
                           String.format(CONTENT_DISPOSITION_HEADER, fileName));
        if (resource.getGzip()) {
            response.setHeader("Content-Encoding", "gzip");
        }
        long contentLength = resource.getContentLength();
        if (contentLength != -1) {
            response.setHeader("Content-Length", String.valueOf(contentLength));
        }
    }

    private void writeContent(HttpServletResponse response, 
                              StaticResource resource) 
            throws IOException, TimeoutException  {
        try (
            ReadableByteChannel inputChannel = Channels.newChannel(
                                                   resource.getInputStream());
            WritableByteChannel outputChannel = Channels.newChannel(
                                                   response.getOutputStream());
        ) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(DEFAULT_STREAM_BUFFER_SIZE);
            long size = 0;

            while (inputChannel.read(buffer) != -1) {
                buffer.flip();
                size += outputChannel.write(buffer);
                buffer.clear();
            }

            if (resource.getContentLength() == -1 && !response.isCommitted()) {
                response.setHeader("Content-Length", String.valueOf(size));
            }
        }
    }

}
