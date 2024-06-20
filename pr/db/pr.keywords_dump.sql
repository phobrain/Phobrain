--
-- PostgreSQL database dump
--

-- Dumped from database version 14.11 (Ubuntu 14.11-1.pgdg22.04+1)
-- Dumped by pg_dump version 14.11 (Ubuntu 14.11-1.pgdg22.04+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: keywords; Type: TABLE; Schema: pr; Owner: epqe
--

CREATE TABLE pr.keywords (
    create_time timestamp without time zone DEFAULT now(),
    id character varying NOT NULL,
    type character varying NOT NULL,
    coder character varying(16) NOT NULL,
    keywords character varying(1024) NOT NULL
);


ALTER TABLE pr.keywords OWNER TO epqe;

--
-- Data for Name: keywords; Type: TABLE DATA; Schema: pr; Owner: epqe
--

COPY pr.keywords (create_time, id, type, coder, keywords) FROM stdin;
\.


--
-- Name: keywords_picid_idx; Type: INDEX; Schema: pr; Owner: epqe
--

CREATE UNIQUE INDEX keywords_picid_idx ON pr.keywords USING btree (id, coder);


--
-- Name: TABLE keywords; Type: ACL; Schema: pr; Owner: epqe
--

GRANT ALL ON TABLE pr.keywords TO pr;


--
-- PostgreSQL database dump complete
--

