package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: MIT-0
 **/

import java.lang.Comparable;
import java.util.Arrays;

public class SortDoubleStrings implements Comparable {

    public double value;
    public String[] strings;

    @Override
    public String toString() {
        return "" + value + "  " + Arrays.toString(strings);
    }

    public SortDoubleStrings(double value, String... strings) {
        this.value = value;
        this.strings = strings;
    }

    @Override
    public int compareTo(Object oo) {
        SortDoubleStrings o = (SortDoubleStrings) oo;
        if (this.value > o.value) return 1;
        if (this.value < o.value) return -1;
        return 0;
    }
}
