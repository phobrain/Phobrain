package org.phobrain.db.record;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

import org.phobrain.util.AtomSpec;
import org.phobrain.util.ListHolder;

public class PictureResponse {
    public Picture p;
    public long value = -1;
    public String method;
    public boolean first = false;
    public ListHolder lh;
    public AtomSpec atoms = AtomSpec.NO_ATOM;
    public double factor = -1.0;
}
