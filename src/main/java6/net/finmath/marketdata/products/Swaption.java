/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 26.11.2012
 */
package net.finmath.marketdata.products;

import net.finmath.functions.AnalyticFormulas;
import net.finmath.marketdata.model.AnalyticModelInterface;
import net.finmath.marketdata.model.curves.DiscountCurveInterface;
import net.finmath.marketdata.model.volatilities.AbstractSwaptionMarketData2;

/**
 * Swaptions used to calibrate analytic volatility smile models (e.g. SABR)
 * 
 * @author Niklas Rodi
 */
public class Swaption extends AbstractAnalyticProduct implements AnalyticProductInterface {

	private final String	swaptionVolatilitySurfaceName;
	private final RateSwap 	underlyingSwap;
	private final double	optionMaturity;
	private final double	swapLength;
	private final double 	strikeOffset; // additive strike offset (i.e. 0 = ATM)
	private final String	discountCurveName;
	//private final double	normalVolatility;

	//public Swaption(String swaptionVolatilitySurfaceName, RateSwap underlyingSwap, double optionMaturity, double strikeOffset, String discountCurveName, double quote, double quotingShift, AbstractSwaptionMarketData2.QuotingConvention quotingConvention) {
	public Swaption(String swaptionVolatilitySurfaceName, RateSwap underlyingSwap, double optionMaturity, double strikeOffset, String discountCurveName) {
		super();
		this.swaptionVolatilitySurfaceName 	= swaptionVolatilitySurfaceName;
		this.underlyingSwap 				= underlyingSwap;
		this.optionMaturity 				= optionMaturity;
		this.swapLength 					= underlyingSwap.getSwapMaturity() - underlyingSwap.getSwapStart();
		this.strikeOffset 					= strikeOffset;
		this.discountCurveName 				= discountCurveName;
	}

	@Override
	public double getValue(double evaluationTime, AnalyticModelInterface model) {	
		if(evaluationTime>=optionMaturity)
			throw new IllegalArgumentException("evaluationTime (" + evaluationTime + ") >= optionMaturity (" + optionMaturity + ") not allowed");
		
		AbstractSwaptionMarketData2 swaptionVolatilitySurface = (AbstractSwaptionMarketData2)model.getVolatilitySurface(swaptionVolatilitySurfaceName);
		if(swaptionVolatilitySurface==null)
			throw new IllegalArgumentException("No swaptionVolatilitySurface with name " + swaptionVolatilitySurfaceName + " found in the model:\n" + model.toString());
		
		DiscountCurveInterface discountCurve = model.getDiscountCurve(discountCurveName);
		if(discountCurve==null)
			throw new IllegalArgumentException("No discountCurve with name " + discountCurveName + " found in the model");
		
		double discountFactor = discountCurve.getDiscountFactor(model, optionMaturity);
		double optionForward = underlyingSwap.getForwardSwapRate(model);
		double optionStrike = optionForward + strikeOffset;
		
		double normalVolatility = swaptionVolatilitySurface.getNormalVolatility(optionMaturity, swapLength, optionStrike);
		return AnalyticFormulas.bachelierOptionValue(optionForward, normalVolatility, optionMaturity, optionStrike, discountFactor);
	}
	
	public double getOptionMaturity() {
		return optionMaturity;
	}

	public RateSwap getUnderlyingSwap() {
		return underlyingSwap;
	}
	
	public double getStrikeOffset() {
		return strikeOffset;
	}
	
	public Swaption cloneShiftedStrikeOffset(double newStrikeOffset)
	{
		return new Swaption(swaptionVolatilitySurfaceName, underlyingSwap, optionMaturity, newStrikeOffset, discountCurveName);
	}

	@Override
	public String toString() {
		return "Swaption [swaptionVolatilitySurfaceName = " + swaptionVolatilitySurfaceName + ", optionMaturity=" + optionMaturity + ", strikeOffset=" + strikeOffset + ", discountCurveName=" + discountCurveName + ", underlyingSwap=" + underlyingSwap.toString() + "]";
	}
}
