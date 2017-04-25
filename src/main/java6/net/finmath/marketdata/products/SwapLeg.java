/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 26.11.2012
 */
package net.finmath.marketdata.products;

import net.finmath.marketdata.model.AnalyticModelInterface;
import net.finmath.marketdata.model.curves.DiscountCurveInterface;
import net.finmath.marketdata.model.curves.ForwardCurveInterface;
import net.finmath.time.ScheduleInterface;

/**
 * Implements the valuation of a swap leg using curves (discount curve, forward curve). The swap leg has a unit notional of 1.
 * 
 * The swap leg valuation supports distinct discounting and forward curves.
 * 
 * Support for day counting is provided via the class implementing
 * <code>ScheduleInterface</code>.
 * 
 * @author Christian Fries
 */
public class SwapLeg extends AbstractAnalyticProduct implements AnalyticProductInterface {

	private final ScheduleInterface				legSchedule;						// Schedule of the leg
	private final String						forwardCurveName;					// Name of the forward curve, leave empty if this is a fix leg
	private final double						spread;								// Fixed spread on the forward or fix rate
	private final String						discountCurveName;					// Name of the discount curve for the leg
	private final String						discountCurveForNotionalResetName;	// Name of the discount curve used for notional reset. If this is equal to discountCurveName then there is no notional reset
	private boolean								isNotionalExchanged;				// If true, the leg will pay notional at the beginning of each swap period and receive notional at the end of the swap period. Note that the cash flow date for the notional is periodStart and periodEnd (not fixingDate and paymentDate)

	/**
	 * Creates a swap leg. 
	 * 
	 * @param legSchedule Schedule of the leg
	 * @param forwardCurveName Name of the forward curve, leave empty if this is a fix leg
	 * @param spread Fixed spread on the forward or fix rate
	 * @param discountCurveName Name of the discount curve for the leg
	 * @param discountCurveForNotionalResetName Name of the discount curve used for notional reset. If this is equal to discountCurveName then there is no notional reset
	 * @param isNotionalExchanged If true, the leg will pay notional at the beginning of each swap period and receive notional at the end of the swap period. Note that the cash flow date for the notional is periodStart and periodEnd (not fixingDate and paymentDate)
	 */
	public SwapLeg(ScheduleInterface legSchedule, String forwardCurveName, double spread, String discountCurveName, String discountCurveForNotionalResetName, boolean isNotionalExchanged) {
		super();
		this.legSchedule = legSchedule;
		this.forwardCurveName = forwardCurveName;
		this.spread = spread;
		this.discountCurveName = discountCurveName;
		this.discountCurveForNotionalResetName = discountCurveForNotionalResetName;
		this.isNotionalExchanged = isNotionalExchanged;
	}

	/**
	 * Creates a swap leg (without notional reset). 
	 * See main constructor for parameter description.
	 */
	public SwapLeg(ScheduleInterface legSchedule, String forwardCurveName, double spread, String discountCurveName, boolean isNotionalExchanged) {
		this(legSchedule, forwardCurveName, spread, discountCurveName, discountCurveName, isNotionalExchanged);
	}
	
	/**
	 * Creates a swap leg (without notional reset and without notional exchange). 
	 * See main constructor for parameter description.
	 */
	public SwapLeg(ScheduleInterface legSchedule, String forwardCurveName, double spread, String discountCurveName) {
		this(legSchedule, forwardCurveName, spread, discountCurveName, false);
	}


	@Override
	public double getValue(double evaluationTime, AnalyticModelInterface model) {
		
		DiscountCurveInterface discountCurve = model.getDiscountCurve(discountCurveName);
		DiscountCurveInterface discountCurveForNotionalReset = model.getDiscountCurve(discountCurveForNotionalResetName);
		// Check for discount curve
		if(discountCurve == null)
			throw new IllegalArgumentException("No discount curve with name '" + discountCurveName + "' was found in the model:\n" + model.toString());
		if(discountCurveForNotionalReset == null)
			throw new IllegalArgumentException("No discount curve with name '" + discountCurveForNotionalResetName + "' (used to calculate resettable notional) was found in the model:\n" + model.toString());
		
		ForwardCurveInterface forwardCurve = model.getForwardCurve(forwardCurveName);
		if(forwardCurve == null && forwardCurveName != null && forwardCurveName.length() > 0)
			throw new IllegalArgumentException("No forward curve with name '" + forwardCurveName + "' was found in the model:\n" + model.toString());

		double value = 0.0;
		double firstPeriodStartDate	= legSchedule.getPeriodStart(0);
		for(int iPeriod=0; iPeriod<legSchedule.getNumberOfPeriods(); iPeriod++) {
			double liborFixingDate 		= legSchedule.getFixing(iPeriod);
			double swapPeriodStartDate	= legSchedule.getPeriodStart(iPeriod);
			double swapPeriodEndDate	= legSchedule.getPeriodEnd(iPeriod);
			double paymentDate 			= legSchedule.getPayment(iPeriod);
			double swapDayCountFraction	= legSchedule.getPeriodLength(iPeriod);
			// empty period is interpreted as misspecification
			if(swapDayCountFraction == 0)
				throw new IllegalArgumentException(iPeriod + "th period of swapLeg is empty.");

			double forward = 0.0;
			if(forwardCurve != null) {
				// the way the forward is calculated here should go hand in hand with the way it is done in Swap.getForwardSwapRate()
				// if forwardCurve is a true forwardCurve then forwardCurve.getForward(model,liborFixingDate) is equal to forwardCurve.getForward(model,liborFixingDate,swapPeriodEndDate-swapPeriodStartDate)
				// however, if forwardCurve=forwardCurveFromDiscountCurve then there may be a difference if the swap and the Libor period do not coincide. Note that getForward(model,fixingDate) should be the prefered solution 
				// forward += forwardCurve.getForward(model, liborFixingDate); note that this does not work with OIS swaps in the current implementation
				forward += forwardCurve.getForward(model, liborFixingDate, swapPeriodEndDate-swapPeriodStartDate); 
			}

			// note that notional = 1 if discountCurveForNotionalReset = discountCurve
			double notional = (discountCurveForNotionalReset.getDiscountFactor(model,legSchedule.getPeriodStart(iPeriod))/discountCurveForNotionalReset.getDiscountFactor(model,firstPeriodStartDate)) / (discountCurve.getDiscountFactor(model,legSchedule.getPeriodStart(iPeriod))/discountCurve.getDiscountFactor(model,firstPeriodStartDate));
			double discountFactorPaymentDate = paymentDate > evaluationTime ? discountCurve.getDiscountFactor(model, paymentDate) : 0.0;
			value += notional * (forward+spread) * swapDayCountFraction * discountFactorPaymentDate;
			
			if(isNotionalExchanged) {
				value -= swapPeriodStartDate > evaluationTime ? notional*discountCurve.getDiscountFactor(model, swapPeriodStartDate) : 0.0;
				value += swapPeriodEndDate > evaluationTime ? notional*discountCurve.getDiscountFactor(model, swapPeriodEndDate) : 0.0;
			}
		}

		return value / discountCurve.getDiscountFactor(model, evaluationTime);
	}

	public ScheduleInterface getSchedule() {
		return legSchedule;
	}

	public String getForwardCurveName() {
		return forwardCurveName;
	}

	public double getSpread() {
		return spread;
	}

	public String getDiscountCurveName() {
		return discountCurveName;
	}

	public boolean isNotionalExchanged() {
		return isNotionalExchanged;
	}

	@Override
	public String toString() {
		return "SwapLeg [forwardCurveName=" + forwardCurveName + ", spread=" + spread + ", discountCurveName=" + discountCurveName + ", discountCurveForNotionalResetName=" + discountCurveForNotionalResetName + ", isNotionalExchanged=" + isNotionalExchanged + ", " + legSchedule.toString() + "]\n";
	}
}
