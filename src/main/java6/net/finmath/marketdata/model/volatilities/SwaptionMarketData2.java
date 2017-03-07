/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 20.05.2005
 */
package net.finmath.marketdata.model.volatilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;

import net.finmath.functions.AnalyticFormulas;
import net.finmath.marketdata.model.AnalyticModelInterface;
import net.finmath.marketdata.model.curves.DiscountCurveInterface;
//import net.finmath.marketdata.model.curves.DiscountCurveInterface;
//import net.finmath.marketdata.model.curves.ForwardCurveInterface;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationInterface;
import net.finmath.marketdata.products.Swaption;

/**
 * @author Niklas Rodi
 */
public class SwaptionMarketData2 implements AbstractSwaptionMarketData2 {

	//private final LocalDate 						referenceDate;
	protected final String 							swaptionSurfaceName;
	
	protected final Swaption[][]					atmSwaptionMatrix;
	
	protected final TimeDiscretizationInterface		optionMaturities;
	protected final TimeDiscretizationInterface		swapLengths;
	protected final TimeDiscretizationInterface		strikeOffsets; // actual value, not in bps
	
	//private final double							quotingShift;
	//private final QuotingConvention				quotingConvention;
	protected final InterpolationMethod 			interpolationMethod;
	
	//private final String							forwardCurveName;
	//private final String							discountCurveName;
	//private final String							optionDiscountCurveName;
	
	protected final double[][][]					impliedNormalVolatilityCube;
	protected final double[][]						forwardSwapRates;
	protected final double[]						optionDiscountFactors;
	
	
	public SwaptionMarketData2(
			//LocalDate referenceDate, 
			String swaptionSurfaceName,
			Swaption[] atmSwaptionVector,
			double[][] quotingShiftMatrix,
			QuotingConvention quotingConvention, 
			InterpolationMethod interpolationMethod, 
			String optionDiscountCurveName,
			AnalyticModelInterface model,
			double[] inputStrikeOffsets, 
			double[][]... impliedVolatilityMatricesAndOffsets) 
	{
		//this.referenceDate = referenceDate;
		this.swaptionSurfaceName = swaptionSurfaceName;
		this.strikeOffsets = new TimeDiscretization(inputStrikeOffsets, 1E-16); // no time rounding
		this.interpolationMethod = interpolationMethod;
				
		// data sanity checks
		if(atmSwaptionVector.length==0)
			throw new IllegalArgumentException("atmSwaptionVector is empty");
		for(int iSwaption=0; iSwaption<atmSwaptionVector.length; iSwaption++)
			if(atmSwaptionVector[iSwaption].getStrikeOffset()!=0.0)
				throw new IllegalArgumentException(iSwaption + "th swaption's strikeOffset (" + atmSwaptionVector[iSwaption].getStrikeOffset() + ") is not 0");
		
		//### Roll out underlying swaps, get optionMaturities, swapLengths and forwardSwapRates ###
		Map<String, Swaption> underlyingSwaptionsMap = new HashMap<String, Swaption>();
		ArrayList<Double> optionMaturitiesTemp = new ArrayList<Double>(); 
		ArrayList<Double> swapLengthsTemp = new ArrayList<Double>(); 
		for(int iSwaption=0; iSwaption<atmSwaptionVector.length; iSwaption++) {
			double optionMaturity = atmSwaptionVector[iSwaption].getOptionMaturity();
			double swapLength = atmSwaptionVector[iSwaption].getUnderlyingSwap().getSwapMaturity() - atmSwaptionVector[iSwaption].getUnderlyingSwap().getSwapStart();
			// @ToDo
			swapLength = Math.round(swapLength);
			if(!optionMaturitiesTemp.contains(optionMaturity))
				optionMaturitiesTemp.add(optionMaturity);
			if(!swapLengthsTemp.contains(swapLength))
				swapLengthsTemp.add(swapLength);
			String tmp = optionMaturity + "x" + swapLength;
			underlyingSwaptionsMap.put(tmp, atmSwaptionVector[iSwaption]);
		}
		this.optionMaturities = new TimeDiscretization(optionMaturitiesTemp, 1E-16); // no time rounding
		this.swapLengths = new TimeDiscretization(swapLengthsTemp, 1E-16); // no time rounding
		
		double[][] forwardSwapRatesTmp = new double[optionMaturities.getNumberOfTimes()][swapLengths.getNumberOfTimes()];
		double[] optionDiscountFactorsTmp = new double[optionMaturities.getNumberOfTimes()];
		Swaption[][] atmSwaptionMatrixTmp = new Swaption[optionMaturities.getNumberOfTimes()][swapLengths.getNumberOfTimes()];
		DiscountCurveInterface optionDiscountCurve = model.getDiscountCurve(optionDiscountCurveName);
		// @ToDo
		for(int iMaturity=0; iMaturity<optionMaturities.getNumberOfTimes(); iMaturity++) {
			optionDiscountFactorsTmp[iMaturity] = optionDiscountCurve.getDiscountFactor(model, optionMaturities.getTime(iMaturity));
			for(int iTenor=0; iTenor<swapLengths.getNumberOfTimes(); iTenor++) {
				Swaption swaption = underlyingSwaptionsMap.get(optionMaturities.getTime(iMaturity) + "x" + swapLengths.getTime(iTenor));
				atmSwaptionMatrixTmp[iMaturity][iTenor] = swaption;
				forwardSwapRatesTmp[iMaturity][iTenor] = swaption.getUnderlyingSwap().getForwardSwapRate(model);
			}
		}
		this.atmSwaptionMatrix = atmSwaptionMatrixTmp;
		this.forwardSwapRates = forwardSwapRatesTmp;
		this.optionDiscountFactors = optionDiscountFactorsTmp;
		
		// ### build impliedVolatilityCube with sorted strike dimension
		// data sanity checks
		if(strikeOffsets.getNumberOfTimes()!=impliedVolatilityMatricesAndOffsets.length)
			throw new IllegalArgumentException("strikeOffsets.length (" + strikeOffsets.getNumberOfTimes() + ") != impliedVolatilityMatricesAndOffsets.length (" + impliedVolatilityMatricesAndOffsets.length + ")");
		for(int iOffset=0 ; iOffset<impliedVolatilityMatricesAndOffsets.length ; iOffset++)
			if(impliedVolatilityMatricesAndOffsets.length==0 || impliedVolatilityMatricesAndOffsets[0].length==0 || impliedVolatilityMatricesAndOffsets[iOffset].length!=optionMaturities.getNumberOfTimes() || impliedVolatilityMatricesAndOffsets[iOffset][0].length!=swapLengths.getNumberOfTimes())
				throw new IllegalArgumentException("Dimension mismatch of impliedVolatilityMatricesAndOffsets for strikeOffset " + inputStrikeOffsets[iOffset] + " (#rows=" + impliedVolatilityMatricesAndOffsets[iOffset].length + " instead of " + optionMaturities.getNumberOfTimes() + ", #cols=" + impliedVolatilityMatricesAndOffsets[iOffset][0].length + " instead of " + swapLengths.getNumberOfTimes() + ")");
		if(quotingShiftMatrix.length==0 || quotingShiftMatrix.length!=optionMaturities.getNumberOfTimes() || quotingShiftMatrix[0].length!=swapLengths.getNumberOfTimes())
			throw new IllegalArgumentException("Dimension mismatch of shiftMatrix (#rows=" + quotingShiftMatrix.length + " instead of " + optionMaturities.getNumberOfTimes() + ", #cols=" + quotingShiftMatrix[0].length + " instead of " + swapLengths.getNumberOfTimes() + ")");
		
		// build cube
		int atmIndex = java.util.Arrays.asList(ArrayUtils.toObject(inputStrikeOffsets)).indexOf(0.0); // note that double[] strikeOffsets need to be converted to Double[]
		double[] strikeOffsetsSorted = strikeOffsets.getAsDoubleArray().clone();
		java.util.Arrays.sort(strikeOffsetsSorted);
		double[][][] impliedNormalVolatilityCubeTemp = new double[strikeOffsets.getNumberOfTimes()][optionMaturities.getNumberOfTimes()][swapLengths.getNumberOfTimes()];
		for(int iMaturity=0; iMaturity<optionMaturities.getNumberOfTimes(); iMaturity++) {
			for(int iTenor=0; iTenor<swapLengths.getNumberOfTimes(); iTenor++) {
				double atmVol = impliedVolatilityMatricesAndOffsets[atmIndex][iMaturity][iTenor];
				double forwardSwapRate = forwardSwapRates[iMaturity][iTenor];
				for(int iOffset=0; iOffset<strikeOffsets.getNumberOfTimes(); iOffset++) {
					// nonAtmVol = atmVol + offset
					int offsetIndex = java.util.Arrays.asList(ArrayUtils.toObject(inputStrikeOffsets)).indexOf(strikeOffsetsSorted[iOffset]);
					double offsetVol = impliedVolatilityMatricesAndOffsets[offsetIndex][iMaturity][iTenor] + (offsetIndex==atmIndex?0.0:atmVol);
					double normalVol = AbstractSwaptionMarketData2.convertVolatilityQuote(offsetVol, quotingShiftMatrix[iMaturity][iTenor], quotingConvention, 0.0, QuotingConvention.VOLATILITYNORMAL, forwardSwapRate, forwardSwapRate+strikeOffsetsSorted[iOffset], optionMaturities.getTime(iMaturity), optionDiscountFactors[iMaturity]);
					//double offsetVolCheck = AbstractSwaptionMarketData2.convertVolatilityQuote(normalVol, 0.0, QuotingConvention.VOLATILITYNORMAL, quotingShiftMatrix[iMaturity][iTenor], quotingConvention, forwardSwapRate, forwardSwapRate+strikeOffsetsSorted[iOffset], optionMaturities.getTime(iMaturity), optionDiscountFactors[iMaturity]);
					impliedNormalVolatilityCubeTemp[iOffset][iMaturity][iTenor] = normalVol;
				}
			}
		}
		this.impliedNormalVolatilityCube = impliedNormalVolatilityCubeTemp;
	}

	@Override
    public String getName() {
		return swaptionSurfaceName;
	}
	
	@Override
    public double[] getOptionMaturities() {
		return optionMaturities.getAsDoubleArray();
	}

	@Override
    public double[] getSwapLengths() {
		return swapLengths.getAsDoubleArray();
	}
	
	@Override
    public double[] getStrikeOffsets() {
		return strikeOffsets.getAsDoubleArray();
	}
	
	public double[] getOptionDiscountFactors() {
		return optionDiscountFactors;
	}
	
	public double[] getSmile(double optionMaturity, double swapLength, double outputQuoteShift, QuotingConvention outputQuoteConvention) {
		int iMaturity = optionMaturities.getTimeIndex(optionMaturity);
		int iTenor = swapLengths.getTimeIndex(swapLength);
		if(iMaturity<0 || iTenor<0)		
			throw new IllegalArgumentException("Requested (optionMaturity,swapLength) tuplet (" + optionMaturity + "," + swapLength + ") not part of data. Interpolation currently not allowed for this method");
		double[] smile = new double[strikeOffsets.getNumberOfTimes()];
		for(int iOffset=0; iOffset<strikeOffsets.getNumberOfTimes(); iOffset++) {
			double strikeOffset = strikeOffsets.getTime(iOffset);
			double volatility = getVolatility(optionMaturity, swapLength, strikeOffset, outputQuoteShift, outputQuoteConvention, forwardSwapRates[iMaturity][iTenor], optionDiscountFactors[iMaturity]);
			smile[iOffset] = volatility;
		}
		return smile;
	}

	@Override
	public double getVolatilityQuote(double optionMaturity, double swapLength, double strikeOffset, double outputQuoteShift, QuotingConvention outputQuoteConvention) {
		int iOffset = strikeOffsets.getTimeIndex(strikeOffset);
		int iMaturity = optionMaturities.getTimeIndex(optionMaturity);
		int iTenor = swapLengths.getTimeIndex(swapLength);
		if(iOffset<0 || iMaturity<0 || iTenor<0)		
			throw new IllegalArgumentException("Requested (strikeOffset,optionMaturity,swapLength) triplet (" + strikeOffset + "," + optionMaturity + "," + swapLength + ") not part of data");
		double normalVolatility = impliedNormalVolatilityCube[iOffset][iMaturity][iTenor];
		double outputQuote = AbstractSwaptionMarketData2.convertVolatilityQuote(normalVolatility, 0.0, QuotingConvention.VOLATILITYNORMAL, outputQuoteShift, outputQuoteConvention, forwardSwapRates[iMaturity][iTenor], forwardSwapRates[iMaturity][iTenor]+strikeOffset, optionMaturity, optionDiscountFactors[iMaturity]);
		return outputQuote;
	}
	
	@Override
	public double getVolatility(double optionMaturity, double swapLength, double strikeOffset, double outputQuoteShift, QuotingConvention outputQuoteConvention) {
		int iMaturity = optionMaturities.getTimeIndex(optionMaturity);
		int iTenor = swapLengths.getTimeIndex(swapLength);
		if(iMaturity<0 || iTenor<0)		
			throw new IllegalArgumentException("Requested (optionMaturity,swapLength) tuplet (" + optionMaturity + "," + swapLength + ") not part of data");
		return getVolatility(optionMaturity, swapLength, strikeOffset, outputQuoteShift, outputQuoteConvention, forwardSwapRates[iMaturity][iTenor], optionDiscountFactors[iMaturity]);
	}
	
	@Override
	public double getVolatility(double optionMaturity, double swapLength, double strikeOffset, double outputQuoteShift, QuotingConvention outputQuoteConvention, double forwardSwapRate, double optionDiscountFactor) {
		double normalVolatility = getNormalVolatility(optionMaturity, swapLength, strikeOffset);
		double outputQuote = AbstractSwaptionMarketData2.convertVolatilityQuote(normalVolatility, 0.0, QuotingConvention.VOLATILITYNORMAL, outputQuoteShift, outputQuoteConvention, forwardSwapRate, forwardSwapRate+strikeOffset, optionMaturity, optionDiscountFactor);
		return outputQuote;
	}
	
	@Override
    public double getNormalVolatility(double optionMaturity, double swapLength, double strikeOffset) {
		
		if(interpolationMethod==InterpolationMethod.NONE) {
			int iOffset = strikeOffsets.getTimeIndex(strikeOffset);
			int iMaturity = optionMaturities.getTimeIndex(optionMaturity);
			int iTenor = swapLengths.getTimeIndex(swapLength);
			if(iOffset<0 || iMaturity<0 || iTenor<0)		
				throw new IllegalArgumentException("Requested (strikeOffset,optionMaturity,swapLength) triplet (" + strikeOffset + "," + optionMaturity + "," + swapLength + ") not part of data and interpolationMethod=" + interpolationMethod);
			return impliedNormalVolatilityCube[iOffset][iMaturity][iTenor];
		} else if(interpolationMethod==InterpolationMethod.TRILINEAR){
			return AnalyticFormulas.getTrilinearInterpolation(strikeOffset, optionMaturity, swapLength, strikeOffsets, optionMaturities, swapLengths, impliedNormalVolatilityCube);
		} else if(interpolationMethod==InterpolationMethod.SABR){
			throw new IllegalArgumentException("SABR only implemented in subClass");
		} else
			throw new IllegalArgumentException("Unknown interpolationMethod");
	}
	
	public double getForwardSwapRate(double optionMaturity, double swapLength) {
		int iMaturity = optionMaturities.getTimeIndex(optionMaturity);
		int iTenor = swapLengths.getTimeIndex(swapLength);
		if(iMaturity<0 || iTenor<0)		
			throw new IllegalArgumentException("Requested (optionMaturity,swapLength) tuplet (" + optionMaturity + "," + swapLength + ") not part of data.");
		return forwardSwapRates[iMaturity][iTenor];
	}
	
	public double getOptionDiscountFactor(double optionMaturity) {
		int iMaturity = optionMaturities.getTimeIndex(optionMaturity);
		if(iMaturity<0)		
			throw new IllegalArgumentException("Requested optionMaturity (" + optionMaturity + ") not part of data.");
		return optionDiscountFactors[iMaturity];
	}
}
