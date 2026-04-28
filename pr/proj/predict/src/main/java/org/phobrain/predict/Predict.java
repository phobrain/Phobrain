package org.phobrain.predict;

/**
 **  SPDX-FileCopyrightText: 2026 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

//  Predict interface - access to Machine Learning or substitute
//          for web server - might be app-wrap too.

public interface Predict {

    public double[] predict(String model, double[][] data2d)
            throws Exception;
}
