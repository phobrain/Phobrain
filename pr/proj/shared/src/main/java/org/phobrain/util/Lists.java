package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  Lists: a base class for classes built on Lists.
 **
 */

import java.io.Serializable;

import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Lists implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(Lists.class);

    public static List<Long> invertDistribution(List<Long> l) {

        long max = - Long.MAX_VALUE;
        long min = Long.MAX_VALUE;
        for (long i : l) {
            if (i > max) max = i;
            if (i < min) min = i;
        }
        long range = max - min;
        if (range < 0) {
            log.error("invertDistribution: range negative");
            range *= -1;
        }
        if (min < 0) range -= 2 * min;
        else range += 2 * min;
        
        range++;
        List<Long> ll = new ArrayList<>(l.size());
        for (long i : l) {
            ll.add(range - i);
        }
        return ll;
    }
}
