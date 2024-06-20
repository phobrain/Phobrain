package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  MathUtil - title says it all.
 **
 */

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MathUtil extends Stdio {

    private static final Logger log = LoggerFactory.getLogger(MathUtil.class);

    private static double[] doublit(float[] it) {

        double[] ret = new double[it.length];

        for (int i=0; i<ret.length; i++) {
            ret[i] = (double) it[i];
        }

        return ret;
    }

    private static void chekvek(double[] l, double[] r)
            throws Exception {

        if (l == null  ||  r == null) {
            throw new Exception("MathUtil.chekvek: null vector");
        }
        if (l.length != r.length) {
            throw new Exception("vector size mismatch " +
                    l.length + "!=" + r.length);
        }
    }

    public static void normalize(double[] arr) {

        double a = 0.0;
        for (double x : arr) {
            a += Math.pow(x, 2);
        }
        a = Math.sqrt(a);
        for (int i=0; i<arr.length; i++) {
            arr[i] /= a;
        }
    }

    public static double cartesianDist(double[] l, double[] r)
            throws Exception {

        chekvek(l, r);

        double d2sum = 0.0;
        for (int i=0; i<l.length; i++) {
            double d = l[i] - r[i];
            d2sum += d * d;
        }
        return Math.sqrt(d2sum);
    }

    public static double cartesianDist(float[] a, float[] b)
            throws Exception {

        double[] A = doublit(a);
        double[] B = doublit(b);;

        return cartesianDist(A, B);
    }

    public static double cos_sim(double[] l, double[] r) throws Exception {

        chekvek(l, r);

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < l.length; i++) {

            dotProduct += l[i] * r[i];
            normA += Math.pow(l[i], 2);
            normB += Math.pow(r[i], 2);

        }
        double div = Math.sqrt(normA) * Math.sqrt(normB);
        if (div == 0.0) {
            if (dotProduct == 0.0) {
                return 1.0; // TODO - fantasy 0/0
            }
            return 0.0; // TODO - how wrong?
        }
        return dotProduct / div;
    }


    public static double cos_sim(float[] a, float[] b) throws Exception {

        double[] A = doublit(a);
        double[] B = doublit(b);;

        return cos_sim(A, B);
    }

    private static double hellingerDist(double[] h1, double[] h2) {

        // Hellinger distance from javatips.net/libsim
        // modified to half Hd squared

        int n = h1.length;
        double mean1 = 0.0, mean2 = 0.0;
        for (int i=0; i<n; i++) {
            mean1 += h1[i];
            mean2 += h2[i];
        }
        mean1 /= n;
        mean2 /= n;
        double sum = 0.0;
        for (int i=0; i<n; i++) {
            sum += Math.pow(
                Math.sqrt(h1[i]/mean1) -
                Math.sqrt(h2[i]/mean2), 2);
        }
        // uncomment for true Hellinger distance
        //return Math.sqrt(2*sum);
        return sum;
    }

    private static final double EPS = 1.0e-6;

    public static double poincareDist(double[] h1, double[] h2) {

        // Poincare ball model of hyperbolic space
        // https://arxiv.org/pdf/1705.08039.pdf

        double diff[] = new double[h1.length];
        for (int i=0; i<h1.length; i++) {
            diff[i] = h1[i] - h2[i];
        }

        double diff2 = 0.0;
        for (int i=0; i<h1.length; i++) {
            diff2 += diff[i] * diff[i];
        }

        double dubdif2 = diff2 * 2.0;

        double h1_2 = 0.0;
        double h2_2 = 0.0;
        for (int i=0; i<h1.length; i++) {
            h1_2 += h1[i] * h1[i];
            h2_2 += h2[i] * h2[i];
        }

        double alpha = 1.0 - h1_2;
        if (alpha <=0.0) alpha = EPS;
        double beta = 1.0 - h2_2;
        if (beta <= 0.0) beta = EPS;

        double div = dubdif2 / (alpha * beta);
        double x = div + 1.0;
        if (x < 1.0) x = 1.0;

        // http://forgetcode.com/Java/1747-acosh-Return-the-hyperbolic-Cosine-of-value-as-a-Argument
        double arcosh = Math.log(x + Math.sqrt(x*x - 1.0));
        return arcosh;
    }

    public static final String[] compFuncs = {
            "cosine", "poinca", "hellin", "cartes"
        };

    public static boolean notFunc(String func) {
        return !Arrays.asList(compFuncs).contains(func);
    }

    public static void checkFunc(String func) {

        if (notFunc(func)) {
            err("Not a comparison func: " + func +
                "\n\t\t\t funcs in [" + Arrays.toString(MathUtil.compFuncs) + "]");
        }
    }

    /*
    ** vec_compare() - not necessarily left, right
    */
    public static double vec_compare(String func,
                                        double[] a, double[] b)
            throws Exception {

        return vec_compare(func, a, b, false);
    }

    public static double vec_compare(String func,
                                        double[] a, double[] b,
                                        boolean zeroNaN)
            throws Exception {

        double val = 0.0;

        if ("cosine".equals(func)) {
            val = cos_sim(a, b);
        } else if ("poinca".equals(func)) {
            val = poincareDist(a, b);
        } else if ("hellin".equals(func)) {
            val = hellingerDist(a, b);
        } else if ("cartes".equals(func)) {
            val = cartesianDist(a, b);
        } else {
            throw new Exception("Unknown similarity function: " + func +
                        " Accepting: " + Arrays.toString(compFuncs));
        }
        if (Double.isNaN(val)) {
            String msg = "-- NaN from MathUtil.vec_compare(" +
                        func + ", a[], b[])" +
                        "\n\ta,b: " +
                            Arrays.toString(a) + "   " +
                            Arrays.toString(b);
            if (zeroNaN) {
                log.warn(msg);
                val = 0.0;
            } else {
                throw new Exception(msg);
            }
        }

        return val;
    }
}
