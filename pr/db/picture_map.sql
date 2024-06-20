/*
 *  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-or-later
 */

/* pr.picture_map holds temporary tokens/names for image files 
    for hiding for outside access */

\echo
\echo Phobrain setup: creating pr.picture_map for hiding photo file names
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
