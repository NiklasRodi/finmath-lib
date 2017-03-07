/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 20.05.2005
 */
package net.finmath.marketdata.model.volatilities;

import net.finmath.functions.AnalyticFormulas;

/**
 * @author Niklas Rodi
 */
public interface AbstractSwaptionMarketData2 {

	double[]	getOptionMaturities();
	double[]	getSwapLengths();
	double[] 	getStrikeOffsets();

	public enum QuotingConvention {
		VOLATILITYLOGNORMAL,
		VOLATILITYNORMAL,
		PRICE
	}
	
	public enum InterpolationMethod {
		NONE,
		TRILINEAR,	// trilinear interpolation (in normal vol as this is the internal representation) with constant extrapolation
		SABR
	}
	
	String getName();
	double getVolatilityQuote(double optionMaturity, double swapLength, double strikeOffset, double outputQuoteShift, QuotingConvention outputQuoteConvention);
	double getVolatility(double optionMaturity, double swapLength, double strikeOffset, double outputQuoteShift, QuotingConvention outputQuoteConvention, double forwardSwapRate, double optionDiscountFactor);
	double getVolatility(double optionMaturity, double swapLength, double strikeOffset, double outputQuoteShift, QuotingConvention outputQuoteConvention);
    double getNormalVolatility(double optionMaturity, double swapLength, double strikeOffset);
    
    public static double convertVolatilityQuote(double inputQuote, double inputQuoteShift, QuotingConvention inputQuoteConvention, double outputQuoteShift, QuotingConvention outputQuoteConvention, double forward, double optionStrike, double optionMaturity, double optionDiscountFactor) {
    	if(inputQuoteConvention==QuotingConvention.PRICE && inputQuoteShift!=0.0)
    		throw new IllegalArgumentException("quotingConvention " + inputQuoteConvention + " incompatibl with inputQuoteShift " + inputQuoteShift);
    	
    	if(inputQuoteShift==outputQuoteShift && inputQuoteConvention==outputQuoteConvention)
    		return inputQuote;
    	
    	// @TODO: Use exact ATM transformation
 
    	// calculate option price
    	double optionValue;
    	if(inputQuoteConvention.equals(QuotingConvention.PRICE))
    		optionValue = inputQuote;
    	else if(inputQuoteConvention.equals(QuotingConvention.VOLATILITYLOGNORMAL))
    		optionValue = AnalyticFormulas.blackScholesGeneralizedOptionValue(forward+inputQuoteShift, inputQuote, optionMaturity, optionStrike+inputQuoteShift, optionDiscountFactor);
    	else if(inputQuoteConvention.equals(QuotingConvention.VOLATILITYNORMAL))
    		optionValue = AnalyticFormulas.bachelierOptionValue(forward+inputQuoteShift, inputQuote, optionMaturity, optionStrike+inputQuoteShift, optionDiscountFactor);
    	else
    		throw new IllegalArgumentException("Unknown inputQuoteConvention " + inputQuoteConvention);
    	
    	double outputQuote;
    	if(outputQuoteConvention.equals(QuotingConvention.PRICE))
    		outputQuote = optionValue;
    	else if(outputQuoteConvention.equals(QuotingConvention.VOLATILITYLOGNORMAL))
    		outputQuote = AnalyticFormulas.blackScholesOptionImpliedVolatility(forward+outputQuoteShift, optionMaturity, optionStrike+outputQuoteShift, optionDiscountFactor, optionValue);
    	else if(outputQuoteConvention.equals(QuotingConvention.VOLATILITYNORMAL))
    		outputQuote = AnalyticFormulas.bachelierOptionImpliedVolatility(forward+outputQuoteShift, optionMaturity, optionStrike+outputQuoteShift, optionDiscountFactor, optionValue);
    	else
    		throw new IllegalArgumentException("Unknown inputQuoteConvention " + inputQuoteConvention);
    	
    	return outputQuote;	
    }
    
    
}
