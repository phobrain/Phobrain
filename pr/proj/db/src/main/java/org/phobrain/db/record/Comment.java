package org.phobrain.db.record;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

import java.sql.Timestamp;

// temporarily used to keep cumulative count of Comments from IP

public class Comment {

    public long        id;
    public Timestamp   createTime;
    public String      ip;
    public int         count = 1;
/*
    public String      name;
    public String      email;
    public String      comment;
*/

    public Comment(String ip) {
        this.ip = ip;
    }
}

