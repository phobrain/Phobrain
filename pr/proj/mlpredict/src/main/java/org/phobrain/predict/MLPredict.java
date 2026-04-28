package org.phobrain.predict;

/**
 **  SPDX-FileCopyrightText: 2026 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

//  MLPredict - wraps Machine Learning for web server

import org.phobrain.util.ConfigUtil;

import org.phobrain.predict.Predict;

import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MLPredict implements Predict {

    private static final Logger log = LoggerFactory.getLogger(MLPredict.class);

    // developmental

    private MultiLayerNetwork model_vgg16_4;
    private MultiLayerNetwork model_nnl_7;

    public MLPredict() {

        try {
            String MODEL_vgg16_4 = ConfigUtil.runtimeProperty("vgg16_4");

            String MODEL_nnl_7 = ConfigUtil.runtimeProperty("nnl_7");

            log.info("Loading models " + MODEL_vgg16_4 + " " + MODEL_nnl_7);
            model_vgg16_4 = KerasModelImport.
                        importKerasSequentialModelAndWeights(MODEL_vgg16_4, false);
            log.info("Loaded MODEL " + MODEL_vgg16_4 + ":   " + model_vgg16_4.summary());
            model_nnl_7 = KerasModelImport.
                        importKerasSequentialModelAndWeights(MODEL_nnl_7, false);
            log.info("Loaded MODEL " + MODEL_nnl_7 + ":   " + model_nnl_7.summary());

        } catch (Exception e) {
            log.error("Loading models", e);
            System.exit(1);
        }

        log.info("Init ok");
    }

    /*
     *  predict() - using model since it's ML
     */
    @Override
    public double[] predict(String modelName, double[][] data2d)
            throws Exception {

        MultiLayerNetwork model;
        if ("vgg16_4".equals(modelName)) {
            model = model_vgg16_4;
        } else if ("nnl_7".equals(modelName)) {
            model = model_nnl_7;
        } else {
            // TODO - check vector size
            log.info("defaulting model [" + modelName + "] to nnl_7");
            model = model_nnl_7;
        }

        log.info("predict(" + modelName + ", " + data2d.length + "x" + data2d[0].length + ")");

        final long t0 = System.currentTimeMillis();

        // run the model

        try {

            INDArray aaa = Nd4j.create(data2d);
            INDArray bbb = model.output(aaa);

            if (bbb.rank() != aaa.rank()) {
                log.error("rank !=: " + aaa.rank() + " " + bbb.rank());
                return null; // or throw
            }

            double ret[] = new double[data2d.length];
            for (int i=0; i<ret.length; i++) {
                ret[i] = bbb.getDouble(i);
            }
            return ret;

        } catch (Exception e) {
            log.error("PREDICT " + e, e);
        } finally {
            log.info("t=" + (System.currentTimeMillis()-t0));
        }
        return null;
    }
}
