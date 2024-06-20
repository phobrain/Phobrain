package org.phobrain.util;

/*
 * XYZ/HSV conversion copied by Bill Ross from:
 *
 * Copyright (c) 2011-2016, Peter Abeles. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * ColorXyz.java
 *
 * <p>Color conversion between CIE XYZ and RGB color models.</p>
 * <p>The XYZ color space is an international standard
 * developed by the CIE (Commission Internationale de l’Eclairage). This model is based on three hypothetical
 * primaries, XYZ, and all visible colors can be represented by using only positive values of X, Y, and Z.
 * The CIE XYZ primaries are hypothetical because they do not correspond to any real light wavelengths.
 * The Y primary is intentionally defined to match closely to luminance, while X and Z primaries give
 * color information. The main advantage of the CIE XYZ space (and any color space based on it) is
 * that this space is completely device-independent.<br>
 * </p>
 *
 * <p>The above text is and equations below are copied from [1], which cites [2] as their source.
 *
 * <p>
 * [1] <a href="http://software.intel.com/sites/products/documentation/hpc/ipp/ippi/ippi_ch6/ch6_color_models.html">
 *     Intel IPP Color Models</a><br>
 * [2] David Rogers. Procedural Elements for Computer Graphics. McGraw-Hill, 1985.
 * </p>
 *
 * @author Peter Abeles
 * @modified Bill Ross
 */

/**
 * ColorHsv.java
 * <p>
 * Color conversion between RGB and HSV color spaces.  HSV stands for Hue-Saturation-Value.  "Hue" has a range of [0,2*PI]
 * and "Saturation" has a range of [0,1], the two together represent the color.  While "Value" has the same range as the
 * input pixels and represents how light/dark the color is. Original algorithm taken from [1] and modified slightly.
 * </p>
 *
 * <p>
 * NOTE: The hue is represented in radians instead of degrees, as is often done.<br>
 * NOTE: Hue will be set to NaN if it is undefined.  It is undefined when chroma is zero, which happens when the input
 * color is a pure gray (e.g. same value across all color bands).
 * </p>
 *
 * <p> RGB to HSV:</pr>
 * <pre>
 * min = min(r,g,b)
 * max = max(r,g,b)
 * delta = max-min  // this is the chroma
 * value = max
 *
 * if( max != 0 )
 *   saturation = delta/max
 * else
 *   saturation = 0;
 *   hue = NaN
 *
 * if( r == max )
 *   hue = (g-b)/delta
 * else if( g == max )
 *   hue = 2 + (b-r)/delta
 * else
 *   hue = 4 + (r-g)/delta
 *
 * hue *= 60.0*PI/180.0
 * if( hue < 0 )
 *   hue += 2.0*PI
 *
 * </pre>
 *
 * <p>
 * [1] http://www.cs.rit.edu/~ncs/color/t_convert.html
 * </p>
 *
 * @author Peter Abeles
 * @modified Bill Ross
 */

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Collections;

public class ColorUtil extends Stdio {

	/**
	 * Conversion from 8-bit RGB into XYZ.
	 */
	public static void rgbToXyz( int r , int g , int b , double xyz[] ) {
		rgbToXyz(r/255.0,g/255.0,b/255.0,xyz);
	}


	/**
	 * Conversion from normalized RGB into XYZ.  Normalized RGB values have a range of 0:1
	 */
	public static void rgbToXyz( double r , double g , double b , double xyz[] ) {

		if ( r > 0.04045 ) r = Math.pow((r + 0.055) / 1.055, 2.4);
		else r = r / 12.92;
		if ( g > 0.04045 ) g = Math.pow((g + 0.055) / 1.055, 2.4);
		else g = g / 12.92;
		if ( b > 0.04045 ) b = Math.pow((b + 0.055) / 1.055, 2.4);
		else b = b / 12.92;

		r *= 100.0;
		g *= 100.0;
		b *= 100.0;

		xyz[0] = 0.4124*r + 0.3576*g + 0.1805*b;
		xyz[1] = 0.2126*r + 0.7152*g + 0.0722*b;
		xyz[2] = 0.0193*r + 0.1192*g + 0.9505*b;
	}


	/**
	 */
	final static double CC = 0.0031308;

	public static void xyzToRgb( double x , double y , double z , double rgb[] ) {
		double xr = x * 3.2406 + y * -1.5372 + z * -0.4986;
		double xg = x * -0.9689 + y *  1.8758 + z *  0.0415;
		double xb = x * 0.0557 + y * -0.2040 + z *  1.0570;

		if (xr > CC) xr = 1.055 * Math.pow(xr, 1 / 2.4 ) - 0.055;
		else xr = 12.92 * xr;
		if (xg > CC) xg = 1.055 * Math.pow(xg, 1 / 2.4 ) - 0.055;
		else xg = 12.92 * xg;
		if (xb > CC) xb = 1.055 * Math.pow(xb, 1 / 2.4 ) - 0.055;
		else xb = 12.92 * xb;

		rgb[0] = 255.0 * xr;
		rgb[1] = 255.0 * xg;
		rgb[2] = 255.0 * xb;
	}

	// --- public class ColorHsv
/*
 Developer Notes:


 The number of comparisons to find min/max can be reduced by one using the following code:

 // Maximum and Minimum values
 float min,max;
 if( r > g ) {
 	if( r > b ) { max = r; } else { max = b; }
 	if( g < b ) { min = g; } else { min = b; }
 } else {
 	if( g > b ) { max = g; } else { max = b; }
 	if( r < b ) { min = r; } else { min = b; }
 }

 This doesn't seem to improve the runtime noticeably and makes the code uglier.
  */


	// 60 degrees in radians
	public static final double d60_F64 = 60.0*Math.PI/180.0;
	public static final float d60_F32 = (float)d60_F64;

	// 360 degrees in radians
	public static final double PI2_F64 = 2*Math.PI;
	public static final float PI2_F32 = (float)PI2_F64;

	/**
	 * Convert HSV color into RGB color
	 *
	 * @param h Hue [0,2*PI]
	 * @param s Saturation [0,1]
	 * @param v Value
	 * @param rgb (Output) RGB value
	 */
	public static void hsvToRgb( double h , double s , double v , double []rgb ) {
		if( s == 0 ) {
			rgb[0] = v;
			rgb[1] = v;
			rgb[2] = v;
			return;
		}
		h /= d60_F64;
		int h_int = (int)h;
		double remainder = h - h_int;
		double p = v * ( 1 - s );
		double q = v * ( 1 - s * remainder );
		double t = v * ( 1 - s * ( 1 - remainder ) );

		if( h_int < 1 ) {
			rgb[0] = v;
			rgb[1] = t;
			rgb[2] = p;
		} else if( h_int < 2 ) {
			rgb[0] = q;
			rgb[1] = v;
			rgb[2] = p;
		} else if( h_int < 3 ) {
			rgb[0] = p;
			rgb[1] = v;
			rgb[2] = t;
		} else if( h_int < 4 ) {
			rgb[0] = p;
			rgb[1] = q;
			rgb[2] = v;
		} else if( h_int < 5 ) {
			rgb[0] = t;
			rgb[1] = p;
			rgb[2] = v;
		} else {
			rgb[0] = v;
			rgb[1] = p;
			rgb[2] = q;
		}
	}

	/**
	 * Convert HSV color into RGB color
	 *
	 * @param h Hue [0,2*PI]
	 * @param s Saturation [0,1]
	 * @param v Value
	 * @param rgb (Output) RGB value
	 */
	public static void hsvToRgb( float h , float s , float v , float []rgb ) {
		if( s == 0 ) {
			rgb[0] = v;
			rgb[1] = v;
			rgb[2] = v;
			return;
		}
		h /= d60_F32;
		int h_int = (int)h;
		float remainder = h - h_int;
		float p = v * ( 1 - s );
		float q = v * ( 1 - s * remainder );
		float t = v * ( 1 - s * ( 1 - remainder ) );

		if( h_int < 1 ) {
			rgb[0] = v;
			rgb[1] = t;
			rgb[2] = p;
		} else if( h_int < 2 ) {
			rgb[0] = q;
			rgb[1] = v;
			rgb[2] = p;
		} else if( h_int < 3 ) {
			rgb[0] = p;
			rgb[1] = v;
			rgb[2] = t;
		} else if( h_int < 4 ) {
			rgb[0] = p;
			rgb[1] = q;
			rgb[2] = v;
		} else if( h_int < 5 ) {
			rgb[0] = t;
			rgb[1] = p;
			rgb[2] = v;
		} else {
			rgb[0] = v;
			rgb[1] = p;
			rgb[2] = q;
		}
	}

	/**
	 * Convert RGB color into HSV color
	 *
	 * @param r red
	 * @param g green
	 * @param b blue
	 * @param hsv (Output) HSV value.
	 */
	public static void rgbToHsv( int r , int g , int b , double []hsv ) {

		// inline to xyz

		double x = (double) r / 255.0;
		double y = (double) g / 255.0;
		double z = (double) b / 255.0;

		xyzToHsv(x, y, z, hsv);
	}

	public static void xyzToHsv( double r , double b , double g , double []hsv ) {
		// Maximum value
		double max = r > g ? ( r > b ? r : b) : ( g > b ? g : b );
		// Minimum value
		double min = r < g ? ( r < b ? r : b) : ( g < b ? g : b );
		double delta = max - min;

		hsv[2] = max;

		if( max != 0 )
			hsv[1] = delta / max;
		else {
			hsv[0] = Double.NaN;
			hsv[1] = 0;
			return;
		}

		double h;
		if( r == max )
			h = ( g - b ) / delta;
		else if( g == max )
			h = 2 + ( b - r ) / delta;
		else
			h = 4 + ( r - g ) / delta;

		h *= d60_F64;
		if( h < 0 )
			h += PI2_F64;

		hsv[0] = h;
	}

	/**
	 * Convert RGB color into HSV color
	 *
	 * @param r red
	 * @param g green
	 * @param b blue
	 * @param hsv (Output) HSV value.
	 */
	public static void xyzToHsv( float r , float g , float b , float []hsv ) {
		// Maximum value
		float max = r > g ? ( r > b ? r : b) : ( g > b ? g : b );
		// Minimum value
		float min = r < g ? ( r < b ? r : b) : ( g < b ? g : b );
		float delta = max - min;

		hsv[2] = max;

		if( max != 0 )
			hsv[1] = delta / max;
		else {
			hsv[0] = Float.NaN;
			hsv[1] = 0;
			return;
		}

		float h;
		if( r == max )
			h = ( g - b ) / delta;
		else if( g == max )
			h = 2 + ( b - r ) / delta;
		else
			h = 4 + ( r - g ) / delta;

		h *= d60_F32;
		if( h < 0 )
			h += PI2_F32;

		hsv[0] = h;
	}



	/**
	 * Conversion from normalized RGB into LAB.  Normalized RGB values have a range of 0:1
	 */
	public static final double epsilon = 0.008856;	//actual CIE standard
	public static final double kappa   = 7.787;	

	public static void rgbToLab( int r , int g , int b , double lab[] ) {
		rgbToXyz(r,g,b,lab);

		double X = lab[0];
		double Y = lab[1];
		double Z = lab[2];

		// divide by reference white constants

		double xr = X / 95.047;
		double yr = Y / 100.0;
		double zr = Z / 108.883;

		double fx, fy, fz;
		if (xr > epsilon)	fx = Math.pow(xr, 1.0 / 3.0);
		else			fx = kappa*xr + 16.0/116.0;
		if (yr > epsilon)	fy = Math.pow(yr, 1.0 / 3.0);
		else			fy = kappa*yr + 16.0/116.0;
		if (zr > epsilon)	fz = Math.pow(zr, 1.0 / 3.0);
		else			fz = kappa*zr + 16.0/116.0;

		lab[0] = 116.0 * fy - 16.0;
		lab[1] = 500.0 * (fx-fy);
		lab[2] = 200.0 * (fy-fz);
	}


    /*
    **  labToXyz()
    */
	public static void labToXyz(double l, double a, double b, double[] xyz) {
		double y = ( l + 16.0 ) / 116.0;
		double x = a / 500.0 + y;
		double z = y - b / 200.0;

		double t = y * y * y;
		if (t > epsilon) y = t;
		else y = ( y - 16.0 / 116.0 ) / kappa;

		t = x * x * x;
		if (t > epsilon) x = t;
		else x = ( x - 16.0 / 116.0 ) / kappa;

		t = z * z * z;
		if (t > epsilon) z = t;
		else z = ( z - 16.0 / 116.0 ) / kappa;

		xyz[0] = x * 95.047;
		xyz[1] = y * 100.0;
		xyz[2] = z * 108.883;
	}

    /*
    **  Color distance Delta E 1994  http://www.easyrgb.com/
    */

    public static double cdE94(String id1, int l1, int a1, int b1,
                               String id2, int l2, int a2, int b2) {

        double dL = l1 - l2;
        double dA = a1 - a2;
        double dB = b1 - b2;

        double xC1 = Math.sqrt( a1 * a1 + b1 * b1 );
        double xC2 = Math.sqrt( a2 * a2 + b2 * b2 );
        double xDL = l2 - l1;
        double xDC = xC2 - xC1;
        double xDE = Math.sqrt( xDL * xDL + dA * dA + dB * dB );
        double xDH;
        if (Math.sqrt(xDE) > Math.sqrt(Math.abs(xDL)) +
                             Math.sqrt(Math.abs(xDC))) {
            xDH = Math.sqrt( xDE * xDE - xDL * xDL - xDC * xDC );
        } else {
            xDH = 0.0;
        }
        double xSC = 1.0 + 0.045 * xC1;
        double xSH = 1.0 + 0.015 * xC1;
        xDC /= xSC;
        xDH /= xSH;

        double inner =  xDL * xDL +
                        xDC * xDC +
                        xDH * xDH;

        double res = Math.sqrt( inner);

        if (Double.isNaN(res)) {
            if (inner < 0.0) {
                err("ColorUtil.cdE94: NaN from sqrt(-1): " + inner);
            }
            err("ColorUtil.cdE94: got NaN");
        }

        return res;
    }

    /*
    **  Color distance Delta E 2000 http://www.easyrgb.com/
    */
    private final static double CONST = Math.pow( 25.0, 7.0);

    private final static double WHT_L = 1.0;
    private final static double WHT_C = 1.0;
    private final static double WHT_H = 1.0;

    // StringBuffer for sync
    private final static StringBuffer flips = new StringBuffer();
    private final static Set<String> flipIds = Collections.synchronizedSet(new HashSet<>());

    public static String flipSum(int nIds) {

        if (flips.length() == 0) {
            return null;
        }

        String lines = flips.toString();

        int count = 0;
        for (int i=0; i<lines.length(); i++) {
            if (lines.charAt(i) == '\n') {
                count++;
            }
        }
        return "ColorUtil.cdE2k flips: " + count +
            " of " + e2kcalls +
            " (" + ((100 * count)/e2kcalls) + "%)";
    }

    public static void printFlips(int nIds) {

        String lines = flips.toString();

        int count = 0;
        for (int i=0; i<lines.length(); i++) {
            if (lines.charAt(i) == '\n') {
                count++;
            }
        }

        pout("ColorUtil.cdE2k flips: " + count +
            "\n\tid1\tl1\ta1\tb1\tid2\tl2\ta2\tb2\tsum\tadd1\tadd2");

        pout(flips.toString());

        pout("ColorUtil.cdE2k flips: " + count +
            " of " + e2kcalls +
            " (" + ((100 * count)/e2kcalls) + "%) - resetting counts");
        flips.setLength(0);
        e2kcalls = 0;

        HashCount hc = new HashCount();
        Iterator<String> setIterator = flipIds.iterator();
        while(setIterator.hasNext()){
            String[] ss = setIterator.next().split("/");
            hc.add(ss[0]);
        }
        // v's 2023_07, at arch37: 
        //      flips: 1463986 of 606828101 (0%) 
        //              ids in flips: 10660 (26%)
        //      ids seem well-distributed across archives/cameras
        pout("ColorUtil.cdE2k unique ids in flips: " + flipIds.size() +
                    " (" + ((100 * flipIds.size()) / nIds) + "%) archive counts:\n" +
                    hc.toString(false)); // + "\n" + Arrays.toString(flipIds.toArray()));
        flipIds.clear();
    }

    private static int e2kcalls = 0;

    public static double cdE2k(int l1, int a1, int b1,
                               int l2, int a2, int b2) {
        return cdE2k("id1", l1, a1, b1, "id2", l2, a2, b2);
    }

    public static double cdE2k(String id1, int l1, int a1, int b1,
                               String id2, int l2, int a2, int b2) {

        e2kcalls++;

        double xC1 = Math.sqrt( a1 * a1 + b1 * b1 );
        double xC2 = Math.sqrt( a2 * a2 + b2 * b2 );
        double xCX = ( xC1 + xC2 ) / 2.0;
        double xCX7 = Math.pow( xCX, 7.0);

        double xGX = 0.5 * ( 1.0 - Math.sqrt( xCX / ( xCX7 + CONST) ) );

        double xNN = ( 1.0 + xGX ) * a1;
        xC1 = Math.sqrt( xNN * xNN + b1 * b1 );
        double xH1 = cieLab2Hue( xNN, b1 );

        xNN = ( 1.0 + xGX ) * a2;
        xC2 = Math.sqrt( xNN * xNN + b2 * b2 );
        double xH2 = cieLab2Hue( xNN, b2 );

        double xDL = l2 - l1;
        double xDC = xC2 - xC1;
        double xDH = 0.0;

        boolean gtZero = xC1 * xC2 > 0.001; // ARBITRARY

        if ( gtZero ) {
            xNN = Math.round( xH2 - xH1 );  // ORIG ROUNDS to 12 -?
        }
        if ( gtZero ) {
            if ( Math.abs( xNN ) <= 180.0 ) {
                xDH = xH2 - xH1;
            } else {
                if ( xNN > 180.0 ) xDH = xH2 - xH1 - 360.0;
                else               xDH = xH2 - xH1 + 360.0;
            }
        }
        xDH = 2.0 * Math.sqrt( xC1 * xC2 )
                  * Math.sin( Math.toRadians( xDH / 2.0 ));

        double xLX = ( l1 + l2 ) / 2.0;
        double xCY = ( xC1 + xC2 ) / 2.0;

        double xHX = xH1 + xH2;
        if ( gtZero ) {
            xNN = Math.abs( xNN );
            if ( xNN > 180.0 ) {
                if ( xH2 + xH1 < 360.0 ) xHX = xH1 + xH2 + 360.0;
                else                     xHX = xH1 + xH2 - 360.0;
            } else {
                xHX = xH1 + xH2;
            }
            xHX /= 2.0;
        }

        double xTX = 1.0 - 0.17 * Math.cos( Math.toRadians( xHX - 30.0 ) ) +
                           0.24 * Math.cos( Math.toRadians( 2.0 * xHX ) ) +
                           0.32 * Math.cos( Math.toRadians( 3.0 * xHX + 6.0 ) ) -
                           0.20 * Math.cos( Math.toRadians( 4.0 *xHX - 63.0 ) );

        double t = ( xHX - 275.0 ) / 25.0;
        t *= t * -1.0;
        double xPH = 30.0 * Math.exp( t );

        double xCY7 = Math.pow( xCY, 7.0 );
        double xRC = 2.0 * Math.sqrt( xCY7 / ( xCY7 + CONST ) );

        t = xLX - 50.0;
        t *= t;
        double xSL = 1.0 + ( 0.015 * t )
                           / Math.sqrt( 20.0 + t );

        double xSC = 1.0 + 0.045 * xCY;
        double xSH = 1.0 + 0.015 * xCY * xTX;
        double xRT = -1.0 * Math.sin( Math.toRadians( 2.0 * xPH ) ) * xRC;

        xDL /= ( WHT_L * xSL );
        xDC /= ( WHT_C * xSC );
        xDH /= ( WHT_H * xSH );

        double inner = xDL * xDL + xDH * xDH + xRT * xDC * xDH;

        if (inner < 0.0) {

            // TODO - check conversion to Lab, maybe the averaging,
            //          which could land in a non-Lab space?

            flipIds.add(id1);
            flipIds.add(id2);

            StringBuilder sb = new StringBuilder(); // in-thread use
            sb.append("\t").append(id1)
              .append("\t").append(l1)
              .append("\t").append(a1)
              .append("\t").append(b1)
              .append("\t").append(id2)
              .append("\t").append(l2)
              .append("\t").append(a2)
              .append("\t").append(b2)
              .append("\t").append(inner)
              .append("\t").append(xDL * xDL + xDH * xDH)
              .append("\t").append(xRT * xDC * xDH)
              .append("\n");

            flips.append(sb); // multithreaded/synced

            inner *= -1.0;
        }

        double res = Math.sqrt( inner );

        if (Double.isNaN(res)) {
            err("ColorUtil.cdE2k: NaN result from inputs: " +
                "\n\t" + id1 + " " + l1 + " " + a1 + " " + b1 +
                "\n\t" + id2 + " " + l2 + " " + a2 + " " + b2);
        }

        return res;

        //return Math.sqrt( xDL * xDL + xDH * xDH + xRT * xDC * xDH );
    }

    private static double cieLab2Hue( double a, double b ) {

        if (Math.abs(b) < 0.001) {
            if ( a >= 0.0 ) return 0.0;
            return 180.0;
        }
        if (Math.abs(a) < 0.001) {
            if ( b > 0.0 ) return 90.0;
            return 270.0;
        }

        double bias = 0.0;
        if (a < 0.0) {
            bias = 180.0;
        } else if ( a > 0.0  &&  b < 0.0 ) {
            bias = 360.0;
        }
        return Math.toDegrees( Math.atan ( b / a )) + bias;
     }
}
