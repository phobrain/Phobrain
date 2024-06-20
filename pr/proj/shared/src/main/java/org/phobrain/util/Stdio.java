package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  Stdio - simple i/o for cmdline progs: pout(), err()
 **
 */

import java.util.Date;

public abstract class Stdio {

    public static void err(String s) {
        System.err.println(new Date().toString() + " - Error: " + s);
        System.exit(1);
    }

    public static void pout(String s) {
        System.out.println(s);
    }

    public static void dot() {
        System.out.print('.');
    }

    public static void println() {
        System.out.print('\n');
    }

}
