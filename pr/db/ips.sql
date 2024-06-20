/*
 *  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-or-later
 */

CREATE TABLE IF NOT EXISTS ips
(
        create_time     TIMESTAMP DEFAULT NOW(),
        ip		VARCHAR(45)
);
grant all privileges on TABLE ips to pr;
CREATE UNIQUE INDEX ip_idx ON ips(ip);
