package org.phobrain.db.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  DumpGen - methods for writing postgres
 **     table dump files for loading to the db.
 **/

import org.phobrain.util.Stdio;
import org.phobrain.util.HashCount;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

import java.io.File;
import java.io.PrintStream;

import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DumpGen extends Stdio {

    private static final Logger log = LoggerFactory.getLogger(DumpGen.class);


    public static void pairtopHeader(PrintStream out, String tbl_name) 
                throws Exception {

        out.println("--\n-- Postgres dump file created by phobrain\n--");
        out.println("SET statement_timeout = 0;");
        out.println("SET lock_timeout = 0;");
        out.println("SET client_encoding = 'UTF8';");
        out.println("SET standard_conforming_strings = on;");
        out.println("SET check_function_bodies = false;");
        out.println("SET client_min_messages = warning;");
        out.println("SET search_path = public, pg_catalog;");
        out.println("SET default_tablespace = '';");
        out.println("SET default_with_oids = false;");

        out.println("-- Name: " + tbl_name + 
                        "; Type: TABLE; Schema: public; Owner: pr;");

        out.println("\\echo 'IGNORE possible Error: " +
                "[relation does not exist] " +
                "on truncate/drop " + tbl_name +"'");
        out.println("TRUNCATE TABLE " + tbl_name + ";");
        out.println("DROP TABLE IF EXISTS " + tbl_name + ";");
        out.println("CREATE TABLE " + tbl_name + " (");
        out.println("    id1 character varying(20),");
        out.println("    id2 character varying(20),");
        out.println("    tag character varying(10),");
        out.println("    val bigint);"); 
        out.println("ALTER TABLE " + tbl_name +" OWNER TO pr;");

        out.println("\\timing on");
        out.println("\\echo 'Loading " + tbl_name +"'");
        out.println("COPY " + tbl_name + " (id1, id2, tag, val) FROM stdin;");
    }

    public static void finishPairtopDump(PrintStream out, 
                                    String tbl_name,
                                    HashCount tagsCount,
                                    List<String> tags,
                                    List<File> inFiles) 
                throws Exception {

        out.println("\\.");

        String ix_name = tbl_name.replaceAll("pr.", "");
        pout("== ix_name: " + ix_name);

        out.println("CREATE INDEX " + ix_name +"_1_idx ON " + 
                                        tbl_name +" USING btree (id1, tag);");
        out.println("CREATE INDEX " + ix_name +"_2_idx ON " + 
                                        tbl_name +" USING btree (id2, tag);");
        out.println("\\timing off");

        out.println("REVOKE ALL ON TABLE " + tbl_name +" FROM PUBLIC;");
        out.println("REVOKE ALL ON TABLE " + tbl_name +" FROM pr;");
        out.println("GRANT ALL ON TABLE " + tbl_name +" TO pr;");

        if (tagsCount != null) {

            // base tags table

            String tags_tbl_name = tbl_name + "_tags";

            pout("== Adding " + tags_tbl_name + "  tags: " + tagsCount.size());

            out.println("-- Name: " + tags_tbl_name + 
                        "; Type: TABLE; Schema: public; Owner: pr;");
            out.println("DROP TABLE IF EXISTS " + tags_tbl_name + ";");
            out.println("CREATE TABLE " + tags_tbl_name + " (");
            out.println("    tag character varying(20),");
            out.println("    ct integer);"); 
            out.println("ALTER TABLE " + tags_tbl_name +" OWNER TO pr;");
            out.println("\\echo 'Loading " + tags_tbl_name +"'");

            out.println("COPY " + tags_tbl_name + " (tag, ct) FROM stdin;");
            Set<String> baseTags = tagsCount.keySet();
            for (String tag : baseTags) {
                int n = tagsCount.getCount(tag);
                out.println(tag + '\t' + n);
            }
            out.println("\\.");

            out.println("REVOKE ALL ON TABLE " + tags_tbl_name + " FROM PUBLIC;");
            out.println("REVOKE ALL ON TABLE " + tags_tbl_name + " FROM pr;");
            out.println("GRANT ALL ON TABLE " + tags_tbl_name + " TO pr;");

        }

        if (tags != null) {

            // tags-filenames pairs

            String tags_files_tbl_name = tbl_name + "_tags_file";

            pout("== Adding " + tags_files_tbl_name + "  tags: " + tags.size());

            out.println("-- Name: " + tags_files_tbl_name + 
                            "; Type: TABLE; Schema: public; Owner: pr;");
            out.println("DROP TABLE IF EXISTS " + tags_files_tbl_name + ";");
            out.println("CREATE TABLE " + tags_files_tbl_name + " (");
            out.println("    tag character varying(20),");
            out.println("    fname character varying(100));"); 
            out.println("ALTER TABLE " + tags_files_tbl_name +" OWNER TO pr;");
            out.println("\\echo 'Loading " + tags_files_tbl_name +"'");

            out.println("COPY " + tags_files_tbl_name + 
                                    " (tag, fname) FROM stdin;");

            for (int i=0; i<tags.size(); i++) {

                String tag = tags.get(i);
                String fname = inFiles.get(i).getName();

                // m_v_model_pn_80_82_ber_10240_2_6__min_6_ki_7_V4_8__nathan_relu_STEEP_SGD.pairs
                fname = fname.replaceFirst("m_v_model_",  "v_");
                fname = fname.replaceFirst("m_vb_model_", "vb_");
                fname = fname.replaceFirst("m_h_model_",  "h_");
                fname = fname.replaceFirst("m_hb_model_", "hb_");
                fname = fname.replace(".pairs", "");

                out.println(tag + '\t' + fname);
            }
            out.println("\\.");

            out.println("REVOKE ALL ON TABLE " + tags_files_tbl_name + 
                                " FROM PUBLIC;");
            out.println("REVOKE ALL ON TABLE " + tags_files_tbl_name + " FROM pr;");
            out.println("GRANT ALL ON TABLE " + tags_files_tbl_name + " TO pr;");
        }

        out.close();

        pout("Write done: " + tbl_name);
        pout("" + new Date());
    }
}
