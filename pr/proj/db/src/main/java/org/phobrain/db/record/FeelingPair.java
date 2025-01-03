package org.phobrain.db.record;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeelingPair extends HistoryPair {

    private static final Logger log = LoggerFactory.getLogger(FeelingPair.class);

    public int dialogDotsBlocked = 0;
    public int ratingAlerts = 0;

    public int flowRating = 0;

    @Override
    public String toString() {
        return super.toString() + " flowRating " + flowRating;
    }

}
