/*
 *  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-or-later
 */

/* setup.sql - create basic operating tables that normally
                aren't initially loaded from dumps created
                by code for loading. */

\echo
\echo Phobrain db setup - base tables
\echo

CREATE SCHEMA IF NOT EXISTS pr;

drop table if exists pr.browser_version;
drop table if exists pr.browser_ip;
drop table if exists pr.pr_user;
drop table if exists pr.session;
drop table if exists pr.session_screen;

CREATE TABLE IF NOT EXISTS pr.browser_version
(
	id		        BIGSERIAL PRIMARY KEY,
	create_time     TIMESTAMP DEFAULT NOW(),
	version         VARCHAR(512),
	session_tag	    VARCHAR(128)
);
grant all privileges on SEQUENCE pr.browser_version_id_seq to pr;
grant all privileges on TABLE pr.browser_version to pr;
CREATE UNIQUE INDEX browser_tag_idx ON pr.browser_version (session_tag);

CREATE TABLE IF NOT EXISTS pr.browser_ip
(
    id              BIGSERIAL PRIMARY KEY,
    create_time     TIMESTAMP DEFAULT NOW(),
	browser_version	BIGINT,
    ip		        VARCHAR(32)
);
grant all privileges on SEQUENCE pr.browser_ip_id_seq to pr;
grant all privileges on TABLE pr.browser_ip to pr;
CREATE INDEX browser_ip_version_idx ON pr.browser_ip(browser_version);

CREATE TABLE IF NOT EXISTS pr.pr_user
(
	id		        BIGSERIAL PRIMARY KEY,
	create_time     TIMESTAMP DEFAULT NOW(),
	name		    VARCHAR(64),
	browser_version	BIGINT NOT NULL,
	ip_addr		    VARCHAR(32),
	access_time	    TIMESTAMP
);
grant all privileges on SEQUENCE pr.pr_user_id_seq to pr;
grant all privileges on TABLE pr.pr_user to pr;
CREATE UNIQUE INDEX user_name_idx ON pr.pr_user (name);

CREATE TABLE IF NOT EXISTS pr.session
(
	id		        BIGSERIAL PRIMARY KEY,
	create_time     TIMESTAMP DEFAULT NOW(),
	host            VARCHAR(100) NOT NULL,
	browser_version BIGINT NOT NULL, 
	tag		        VARCHAR(32),
	hour            SMALLINT,
	tzoff           SMALLINT,
	lang            VARCHAR(32),
	platform	    VARCHAR(32),
	pr_user		    VARCHAR(32),
	kwd_choice	    VARCHAR(32),
	pic_repeats	    BOOLEAN
);
grant all privileges on SEQUENCE pr.session_id_seq to pr;
grant all privileges on TABLE pr.session to pr;
CREATE UNIQUE INDEX session_tag_idx ON pr.session (tag);

CREATE TABLE IF NOT EXISTS pr.session_screen
(
	utime		    TIMESTAMP,
	session_id	    BIGINT NOT NULL,
	id		        SMALLINT NOT NULL,
	orientation	    VARCHAR(1) NOT NULL,
	pic_id		    VARCHAR(10),
	sel_method	    VARCHAR(128),
	showing_id	    BIGINT,
	depth		    SMALLINT
);
grant all privileges on TABLE pr.session_screen to pr;
CREATE INDEX session_screen_sid_ix ON pr.session_screen(session_id);

CREATE TABLE IF NOT EXISTS pr.rating_scheme
(
	id		        SERIAL PRIMARY KEY,
	type		    VARCHAR(32),
	description	    VARCHAR(1024)
);
grant all privileges on SEQUENCE pr.rating_scheme_id_seq to pr;
grant all privileges on TABLE pr.rating_scheme to pr;

CREATE TABLE IF NOT EXISTS pr.approved_pair
(
	create_time	    TIMESTAMP DEFAULT NOW(),
	browser_id	    BIGINT NOT NULL,
	curator		    VARCHAR NOT NULL,
	id1		        VARCHAR NOT NULL,
	id2		        VARCHAR NOT NULL,
	status		    SMALLINT DEFAULT 0
);
grant all privileges on TABLE pr.approved_pair to pr;
CREATE INDEX approved_pair_id1 ON pr.approved_pair(id1);
CREATE INDEX approved_pair_id2 ON pr.approved_pair(id2);

CREATE TABLE IF NOT EXISTS pr.pair_local 
(
	create_time 	TIMESTAMP DEFAULT NOW(),
	curator		    VARCHAR NOT NULL,
    id1             VARCHAR NOT NULL,
    id2             VARCHAR NOT NULL
);
grant all privileges on TABLE pr.pair_local to pr;
CREATE INDEX pair_local_id1 ON pr.pair_local(id1);
CREATE INDEX pair_local_id2 ON pr.pair_local(id2);


\echo
\echo Phobrain tables for importing photos 
\echo === (via pr/proj/1_mk_archive.sh)
\echo

SET client_min_messages = error;

create schema if not exists pr authorization pr;

drop table if exists pr.picture;
drop table if exists pr.picture_prop;
drop table if exists pr.keywords;

CREATE TABLE pr.picture
(
	xid		        BIGSERIAL PRIMARY KEY,
	create_time     TIMESTAMP DEFAULT NOW(),
	id		        VARCHAR(16),
	archive		    INTEGER,
	file_name	    VARCHAR(32),
	sequence	    INTEGER,
    seq2            INTEGER,
	variation_tag	VARCHAR(8),
	variation_type	VARCHAR(8),
	scene_sequence	INTEGER,
	scene_type	    VARCHAR(8),
    lighting        VARCHAR(12),
    angle           VARCHAR(12),
    place           VARCHAR(32),
	comments	    VARCHAR(1024),
	r		        SMALLINT,
	g		        SMALLINT,
	b		        SMALLINT,
	rgb_radius	    SMALLINT,
	ll		        SMALLINT,
	aa		        SMALLINT,
	bb		        SMALLINT,
	lab_radius	    SMALLINT,
	lab_contrast	SMALLINT,
	width		    SMALLINT,
	height		    SMALLINT,
	vertical	    BOOLEAN,
    outdoors        BOOLEAN,
	people		    BOOLEAN,
	face		    BOOLEAN,
	blur		    BOOLEAN,
	sign		    BOOLEAN,
	number		    BOOLEAN,
	block_display	BOOLEAN,
	block_reason	VARCHAR(128),
	pref		    BOOLEAN,
	density		    INTEGER,
	sum_d0		    BIGINT,
	sum_d0_l	    BIGINT,
	sum_d0_r	    BIGINT,
	avg_ok_d0	    BIGINT,
	avg_bad_d0	    BIGINT,
	d_ctr_rgb	    INTEGER,
	d_ctr_ab	    INTEGER,
	d_ctr_8d	    INTEGER,
	ang_ab		    INTEGER,
	d_ctr_27d	    INTEGER,
	d_ctr_64d	    INTEGER,
    vec_l           DOUBLE PRECISION ARRAY,
    vec_r           DOUBLE PRECISION ARRAY
);

grant all privileges on TABLE pr.picture to pr;
grant all privileges on SEQUENCE pr.picture_xid_seq to pr;
CREATE UNIQUE INDEX picture_fname_idx ON pr.picture(archive, file_name);
CREATE UNIQUE INDEX picture_id_idx ON pr.picture(id);

CREATE TABLE pr.picture_prop
(
    id              VARCHAR NOT NULL,
    p_name          VARCHAR(16),
    p_value         VARCHAR(16)
);
grant all privileges on TABLE pr.picture_prop to pr;
CREATE INDEX picture_prop_seq_idx ON pr.picture_prop(id);

CREATE TABLE IF NOT EXISTS pr.comment
(
    id              BIGSERIAL PRIMARY KEY,
    create_time     TIMESTAMP DEFAULT NOW(),
    ip              VARCHAR(20) NOT NULL,
    count           SMALLINT NOT NULL
);
grant all privileges on SEQUENCE pr.comment_id_seq to pr;
grant all privileges on TABLE pr.comment to pr;
CREATE INDEX  comment_ip_idx ON pr.comment(ip);

CREATE TABLE pr.keywords
(
    create_time     TIMESTAMP DEFAULT NOW(),
    id              VARCHAR NOT NULL,
    type            VARCHAR NOT NULL,
    coder           VARCHAR(16) NOT NULL,
    keywords        VARCHAR(1024) NOT NULL
);
grant all privileges on TABLE pr.keywords to pr;
CREATE UNIQUE INDEX keywords_picid_idx ON pr.keywords(id, coder);

\echo
\echo Phobrain log table - pr.showing_pair
\echo

CREATE TABLE IF NOT EXISTS pr.showing_pair
(
	id		            BIGSERIAL PRIMARY KEY,
	create_time 	    TIMESTAMP DEFAULT NOW(),
	browser_id	        BIGINT NOT NULL,
	call_count	        INTEGER,
	order_in_session    INTEGER,
	sel_method	        VARCHAR(32),
	id1		            VARCHAR NOT NULL,
	archive1	        INTEGER,
	file_name1	        VARCHAR NOT NULL,
	sel_method1	        VARCHAR,
	id2		            VARCHAR NOT NULL,
	archive2	        INTEGER,
	file_name2	        VARCHAR NOT NULL,
	sel_method2	        VARCHAR,
	vertical	        BOOLEAN,
	big_stime	        SMALLINT,
	big_time	        SMALLINT,
	rating		        SMALLINT,
	rating_scheme	    SMALLINT,
	rate_time	        TIMESTAMP,
	load_time	        INTEGER,
	user_time	        INTEGER,
	user_time2	        INTEGER,
	watch_dots_time     INTEGER,
	mouse_down_time     INTEGER,
	mouse_dist	        INTEGER,
	mouse_dist2	        INTEGER,
	mouse_dx	        INTEGER,
	mouse_dy	        INTEGER,
	mouse_vecx	        INTEGER,
	mouse_vecy	        INTEGER,
	mouse_maxv	        INTEGER,
	mouse_maxa	        INTEGER,
	mouse_mina	        INTEGER,
	mouse_maxj	        INTEGER,
	mouse_time	        INTEGER,
	pix_in_pic	        INTEGER,
	pix_out_pic	        INTEGER,
	dot_start_scrn	    INTEGER,
	dot_end_scrn	    INTEGER,
	dot_count	        INTEGER,
	dot_dist	        INTEGER,
	dot_vec_len	        INTEGER,
	dot_vec_ang	        INTEGER,
	dot_max_vel	        INTEGER,
	dot_max_acc	        INTEGER,
	dot_max_jerk	    INTEGER,
	pic_clik	        VARCHAR(8),
	atom_impact	        SMALLINT,
	impact_factor	    REAL,
	tog_times	        VARCHAR,
	n_tog_last	        SMALLINT,
    tog_sides	        VARCHAR(2)
);
grant all privileges on SEQUENCE pr.showing_pair_id_seq to pr;
grant all privileges on TABLE pr.showing_pair to pr;

CREATE INDEX  showing_pair_browser_idx ON pr.showing_pair (browser_id);
CREATE INDEX  showing_pair_browser_seq_idx ON pr.showing_pair (browser_id, id1, id2);


/* pr.picture_map holds temporary tokens/names for image files 
    for hiding for outside access */

\echo
\echo Phobrain pr.picture_map for hiding photo file names
\echo 

CREATE TABLE IF NOT EXISTS pr.picture_map
(
        id              BIGSERIAL PRIMARY KEY,
        create_time     TIMESTAMP DEFAULT NOW(),
        archive         INTEGER,
        file_name       VARCHAR(64) NOT NULL,
        picture_id      BIGINT NOT NULL,
        hash            VARCHAR(16) NOT NULL
);
grant all privileges on SEQUENCE pr.picture_map_id_seq to pr;
grant all privileges on TABLE pr.picture_map to pr;
CREATE UNIQUE INDEX picture_map_hash_idx ON pr.picture_map(hash);
