package org.phobrain.db.record;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

import java.sql.Timestamp;

public class PictureMap {

    public long        id;
    public Timestamp   createTime;
    public int         archive;
    public String      fileName;
    public long        picID;
    public String      hash;
}

