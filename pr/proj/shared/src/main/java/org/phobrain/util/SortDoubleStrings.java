package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: MIT-0
 **/

import java.lang.Comparable;
import java.util.Arrays;

public class SortDoubleStrings implements Comparable<SortDoubleStrings> {

    public double value = -111.111;
    public String[] strings;

    @Override
    public String toString() {
        return "" + value + "  " + Arrays.toString(strings);
    }

    public SortDoubleStrings(double value, String... strings) {
        this.value = value;
        this.strings = strings;
    }

    public SortDoubleStrings(String... strings) {
        this.strings = strings;
    }

    public void setVal(double d) {
        this.value = d;
    }

    @Override
    public int compareTo(SortDoubleStrings o) {
        return Double.compare(value, o.value);
    }
}
