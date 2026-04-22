package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

import java.lang.Comparable;

class IdValInt implements Comparable<IdValInt> {
    String id;
    int value;

    IdValInt(String id, int value) {
        this.id = id;
        this.value = value;
    }

    @Override
    public int compareTo(IdValInt o) {
        return Integer.compare(value, o.value);
    }
}
