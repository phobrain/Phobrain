package org.phobrain.math;

/**
 **  SPDX-FileCopyrightText: 2009 - 2016 Helmut Ahammer, Philipp Kainz
 **
 **  SPDX-License-Identifier: GPL-3.0
 */

/*
 * #%L
 * Project: Phobrain
 * Original Project: IQM - API
 * File: LinearRegression.java
 * 
 * This file was part of IQM, hereinafter referred to as "this program".
 * %%
 * Copyright (C) 2009 - 2016 Helmut Ahammer, Philipp Kainz
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */


/**
 * The regression range is calculated using a class
 * by Michael Thomas Flanagan's Java Scientific Library
 * http://www.ee.ucl.ac.uk/~mflanaga/java/index.html
 * @author Helmut Ahammer
 *
 */
public class LinearRegression {
	/**
	 * This class calculates the regression parameters
	 * @param dataX
	 * @param dataY
	 * @param regStart  first value for regression 
	 * @param regEnd    last value for regression
	 * @return parameters including errors
	 */
	@SuppressWarnings("unused")
	public double[] calculateParameters(double[] dataX, double[] dataY, int regStart, int regEnd){
		double[] parameters = new double[6]; //p0, p1, StDev1 StDev2, r2     
		//number of data points for regression 
		int numRegPoints = (regEnd-regStart+1);

		//Flanagan library
		double[] regDataX = new double[numRegPoints];
		double[] regDataY = new double[numRegPoints];
		for (int i = 0; i < numRegPoints; i++){
			//System.out.println("LinearRegression: i:" + i + "  regStart:" + regStart + "    regEnd:"+ regEnd + "   numRegPoints:" + numRegPoints);
			regDataX[i] = dataX[i+regStart-1];
			regDataY[i] = dataY[i+regStart-1];
			//System.out.println("LinearRegression: i: "+i+ " regDataX[i]: "+regDataX[i]+ " regDataY[i]: "+regDataY[i]);
		}	
		flanagan.analysis.Regression reg = new flanagan.analysis.Regression(regDataX, regDataY);
		reg.linear();

		//reg.linearPlot();
		double[] coef    = reg.getCoeff();
		double[] coefSd  = reg.getCoeffSd();
		double[] coefVar = reg.getCoeffVar();
		double adjR   = reg.getAdjustedR();
		double adjR2  = reg.getAdjustedR2();
		double sampR  = reg.getSampleR();
		double sampR2 = reg.getSampleR2();
		//double chi2   = reg.getChiSquare(); 
		//		double[] bestEst = reg.getBestEstimates();
		//		double[] bestEstErr = reg.getBestEstimatesErrors();
		//		double[] bestEstSd  = reg.getBestEstimatesStandardDeviations();

		//for (int i =0; i < coef.length; i++)    System.out.print("Regression Coef: " + coef[i]+"  "); System.out.println("");
		//for (int i =0; i < coefSd.length; i++)  System.out.print("coefSd: " + coefSd[i]+"  "); System.out.println("");
		//for (int i =0; i < coefVar.length; i++) System.out.print("coefVar: " + coefVar[i]+"  ");	System.out.println("");
		//System.out.println("adjR: " + adjR + "  adjR2: " + adjR2+ "  sampR: " + sampR + "  sampR2: " + sampR2 + "  chi2: " + chi2);

		//		for (int i =0; i < bestEst.length; i++) System.out.println("bestEst: " + bestEst.toString());
		//		for (int i =0; i < bestEstErr.length; i++) System.out.println("bestEstErr: " + bestEstErr.toString());
		//		for (int i =0; i < bestEstSd.length; i++) System.out.println("bestEstSd: " + bestEstSd.toString());
		parameters[0] = coef[0];    //y = coef[0] + coef[1] . x
		parameters[1] = coef[1];
		parameters[2] = coefSd[0];
		parameters[3] = coefSd[1];
		parameters[4] = sampR2;    //Bestimmheitsmaß
		parameters[5] = adjR2;     //adjustiertes, bereinigtes Bestimmheitsmaß für große Zahlen

		return parameters;
	}

}
