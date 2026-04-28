package org.phobrain.predict;

/**
 **  SPDX-FileCopyrightText: 2026 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

//  SimplePredict - lightweight, symmetric, all-java for web server

import org.phobrain.util.MathUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimplePredict implements Predict {

    private static final Logger log = LoggerFactory.getLogger(SimplePredict.class);

    public SimplePredict() {

        log.info("Init ok (no-op)");
    }

    private double funcY(double[] a, double[] b) {
        return MathUtil.poincareDist(a, b);
    }

    /*
     *  predict() - not using model - modelName just indicates data layout.
     */
    @Override
    public double[] predict(String modelName, double[][] data2d)
            throws Exception {

        log.info("predict(" + modelName + ", " + data2d.length + "x" + data2d[0].length + ")");

        int picVecSize = 0;
        int nextPairIx = 0;
        if (modelName.contains("_4")) {
            picVecSize = 4;
            nextPairIx = 182;
        } else if (modelName.contains("_7")) {
            picVecSize = 7;
            nextPairIx = 188;
        } else {
            throw new Exception("pic vec size not 4 or 7: " + modelName);
        }

        final int prev_pair_ix = 0;

        // lay it out

        double[] ret = new double[data2d.length];

        // prev pic pair is assumed the same for every case,
        //      here copied from the first row.
        double[] prev_pair_vec = new double[2 * picVecSize];
        System.arraycopy(data2d[0], prev_pair_ix, prev_pair_vec, 0, 2 * picVecSize);

        // same length, copy per-row in loop at nextPairIx:
        double[] eval_pair_vec = new double[2 * picVecSize];

        final long t0 = System.currentTimeMillis();

        for (int irow=0; irow<data2d.length; irow++) {

            System.arraycopy(data2d[irow], nextPairIx, eval_pair_vec, 0, 2 * picVecSize);
         
            ret[irow] = funcY(prev_pair_vec, eval_pair_vec);   
        }
        log.info("t=" + (System.currentTimeMillis()-t0));
        return ret;
    }
}
