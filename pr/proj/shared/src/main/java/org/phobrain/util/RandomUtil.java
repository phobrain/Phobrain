package org.phobrain.util;

/*
 *  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 *
 *  SPDX-License-Identifier: MIT-0
 */

import java.util.Random;
import java.lang.StringBuilder;

public class RandomUtil {

    private static final Random rand = new Random();

    private final static String ID_BASE =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890-";

    public static String makeRandomTag(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<len; i++) {
            sb.append(ID_BASE.charAt(rand.nextInt(ID_BASE.length())));
        }
        return sb.toString();
    }

}
