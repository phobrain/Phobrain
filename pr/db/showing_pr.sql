/*
 *  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-or-later
 */

\echo Phobrain setup: pr.showing_pair log table
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
