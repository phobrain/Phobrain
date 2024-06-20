package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

import java.lang.Comparable;

class IdValInt implements Comparable {
    String id;
    int value;

    IdValInt(String id, int value) {
        this.id = id;
        this.value = value;
    }

    @Override
    public int compareTo(Object oo) {
        IdValInt o = (IdValInt) oo;
        if (this.value > o.value) return 1;
        if (this.value < o.value) return -1;
        return 0;
    }
}
