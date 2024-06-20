package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  AtomSpec: ID's for atoms to interact with in dCAT
 **
 **  Fanciful feature needing live molecular simulation, not
 **  in use, AMBER Sander code mods out of date.
 */

public enum AtomSpec {
    NO_ATOM(0),
    C_O2N3N4(1),
    C_O2N3(2),
    C_N3N4(3),
    A_N1N6N7(4),
    A_N1N6(5),
    A_N6N7(6),
    T_O2N3(7),
    T_N3O4(8);

    private static AtomSpec[] arr;
    static {
        int max = -1;
        AtomSpec[] arr2 = AtomSpec.values();
        for (int i=0; i<arr2.length; i++) {
            int v = arr2[i].intValue();
            if (v > max) max = v;
        }
        arr = new AtomSpec[max+1];
        for (int i=0; i<arr2.length; i++) {
            int v = arr2[i].intValue();
            arr[v] = arr2[i];
        }
    }
    private int value;
    private AtomSpec(int value) {
        this.value = value;
    }
    public int intValue() {
        return this.value;
    }
    public static AtomSpec fromInt(int i) {
        return arr[i];
    }
}
