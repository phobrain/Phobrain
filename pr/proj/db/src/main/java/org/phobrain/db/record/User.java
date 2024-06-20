package org.phobrain.db.record;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

import java.sql.Timestamp;

public class User {

    public long        id;
    public Timestamp   createTime;
    public String      name;
    public long        browserID;
    public String      ipAddr;
    public Timestamp   accessTime;

    public User(String name, long browserID, String ipAddr) {
        this.name = name;
        this.browserID = browserID;
        this.ipAddr = ipAddr;
    }
}

