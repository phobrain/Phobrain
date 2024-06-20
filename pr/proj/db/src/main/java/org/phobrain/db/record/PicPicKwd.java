package org.phobrain.db.record;

/*
 *  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-or-later
 */

import java.sql.Timestamp;

public class PicPicKwd {

    public int          coder;
    public String       id1;
    public String       id2;
    public long         closeness; // bigger == closer

    public PicPicKwd(int coder, String id1, String id2, long closeness) {
        this.coder = coder;
        this.id1 = id1;
        this.id2 = id2;
        this.closeness = closeness;
    }

}

