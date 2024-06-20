
/*
 *  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-or-later
 */

drop index browser_version_idx;

ALTER TABLE browser_version ADD COLUMN session_tag VARCHAR(128);
CREATE UNIQUE INDEX browser_tag_idx ON browser_version (session_tag);


CREATE TABLE IF NOT EXISTS browser_ip
(
        id              BIGSERIAL PRIMARY KEY,
        create_time     TIMESTAMP DEFAULT NOW(),
        browser_version BIGINT,
        ip              VARCHAR(32)
);
grant all privileges on SEQUENCE browser_ip_id_seq to pr;
grant all privileges on TABLE browser_ip to pr;
CREATE INDEX browser_ip_version_idx ON browser_ip(browser_version);

