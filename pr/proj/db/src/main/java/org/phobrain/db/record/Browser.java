package org.phobrain.db.record;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

import java.sql.Timestamp;

public class Browser {

    public long id;
    public Timestamp createTime;
    public String version;
    public String sessionTag;
}

