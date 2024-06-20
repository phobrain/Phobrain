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
-- Name: picture_prop; Type: TABLE; Schema: pr; Owner: epqe
--

CREATE TABLE pr.picture_prop (
    id character varying NOT NULL,
    p_name character varying(16),
    p_value character varying(16)
);


ALTER TABLE pr.picture_prop OWNER TO epqe;

--
-- Data for Name: picture_prop; Type: TABLE DATA; Schema: pr; Owner: epqe
--

COPY pr.picture_prop (id, p_name, p_value) FROM stdin;
\.


--
-- Name: picture_prop_seq_idx; Type: INDEX; Schema: pr; Owner: epqe
--

CREATE INDEX picture_prop_seq_idx ON pr.picture_prop USING btree (id);


--
-- Name: TABLE picture_prop; Type: ACL; Schema: pr; Owner: epqe
--

GRANT ALL ON TABLE pr.picture_prop TO pr;


--
-- PostgreSQL database dump complete
--

