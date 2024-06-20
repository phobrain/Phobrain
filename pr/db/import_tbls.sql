/*
 *  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-or-later
 */

/* import_tbls.sql - create the tables that
        pr/proj/import populates.
*/

\echo Phobrain setup: creating photo import tables.
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
grant all privileges on SEQUENCE comment_id_seq to pr;
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

