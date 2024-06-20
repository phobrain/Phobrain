package you.name.it

/*
 *  SPDX-FileCopyrightText: 2022 Bill Ross <phobrain@sonic.net>
 *
 *  SPDX-License-Identifier: MIT-0
 */

import your.ConfigUtil; // or recode a little

import java.util.List;
import java.util.ArrayList;

import com.google.gson.Gson;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.WebServlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/your/endpoint")
public class CrdServlet extends HttpServlet {

    private static final int NATOMS = 95;

    private final static int LINES = (NATOMS * 3) / 10 + ((NATOMS * 3) / 10) % 2;

    private static String crdFile;

    private static int requestCount = 0;

    private static final Logger log = LoggerFactory.getLogger(
                                                    CrdServlet.class);

    @Override
    public void init() throws ServletException {
        crdFile = ConfigUtil.getConfigProperty("crd.file");
        File f = new File(crdFile);
        if (!f.isFile()) {
            throw new ServletException("CONFIG: crd.file [" + f + 
                                       "] IS NOT A FILE");
        }
        log.info("CrdServlet serving " + crdFile);
    }

    @Override
    public String getServletInfo() {
        return "CrdServlet";
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException  {

        long t1 = System.currentTimeMillis();

        String remoteHost = req.getRemoteHost();

        try {
            List<Float> crdlist = new ArrayList<>();
            Path path = Paths.get(crdFile);
            //byte[] encoded = Files.readAllBytes(Paths.get(crdFile));

            List<String> lines = null;
            while (true) {
                lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                if (lines != null  &&  lines.size() > 1+LINES) {
                    break;
                }
                log.info("got no lines: " + 
                             (lines == null ? "null" : lines.size()));
            }

            for (int i=1; i<1+LINES; i++) {
                String[] crds = lines.get(i).split("\\s+");
                for (String s : crds) {
                    if ("".equals(s)) {
                        continue;
                    }
                    crdlist.add(Float.parseFloat(s));
                }
            }
            Gson gson = new Gson();
            String json = gson.toJson(crdlist);
            res.setContentType( "text/plain" );
            PrintWriter out = res.getWriter();
            out.println(json);
            out.close();
        } catch (Exception e) {
            log.error("Exception " + e, e);
        }

        if (requestCount++ % 1000 == 0) {
            log.info("Served: " + requestCount);
        }
    }
}
