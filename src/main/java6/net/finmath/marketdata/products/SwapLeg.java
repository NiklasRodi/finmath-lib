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
 * Implements the valuation of a swap leg using curves (discount curve, forward curve).
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
	private final String						discountCurveForNotionalResetName;	// Name of the discount curve used for notional reset. Set this to discountCurveName if there is no notional reset
	private boolean								isNotionalExchanged = false;		// If true, the leg will pay notional at the beginning of each swap period and receive notional at the end of the swap period. Note that the cash flow date for the notional is periodStart and periodEnd (not fixingDate and paymentDate).

	/**
	 * Creates a swap leg. The swap leg has a unit notional of 1.
	 * 
	 * @param legSchedule Schedule of the leg.
	 * @param forwardCurveName Name of the forward curve, leave empty if this is a fix leg.
	 * @param spread Fixed spread on the forward or fix rate.
	 * @param discountCurveName Name of the discount curve for the leg.
	 * @param isNotionalExchanged If true, the leg will pay notional at the beginning of each swap period and receive notional at the end of the swap period. Note that the cash flow date for the notional is periodStart and periodEnd (not fixingDate and paymentDate).
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
	 * Creates a swap leg (without reset). The swap leg has a unit notional of 1.
	 */
	public SwapLeg(ScheduleInterface legSchedule, String forwardCurveName, double spread, String discountCurveName, boolean isNotionalExchanged) {
		this(legSchedule, forwardCurveName, spread, discountCurveName, discountCurveName, isNotionalExchanged);
	}
	
	/**
	 * Creates a swap leg (without reset or notional exchange). The swap leg has a unit notional of 1.
	 * 
	 * @param legSchedule Schedule of the leg.
	 * @param forwardCurveName Name of the forward curve, leave empty if this is a fix leg.
	 * @param spread Fixed spread on the forward or fix rate.
	 * @param discountCurveName Name of the discount curve for the leg.
	 */
	public SwapLeg(ScheduleInterface legSchedule, String forwardCurveName, double spread, String discountCurveName) {
		this(legSchedule, forwardCurveName, spread, discountCurveName, false);
	}


	@Override
	public double getValue(double evaluationTime, AnalyticModelInterface model) {	
		DiscountCurveInterface	discountCurve = model.getDiscountCurve(discountCurveName);
		DiscountCurveInterface	discountCurveForNotionalReset = model.getDiscountCurve(discountCurveForNotionalResetName);
		// Check for discount curve
		if(discountCurve == null)
			throw new IllegalArgumentException("No discount curve with name '" + discountCurveName + "' was found in the model.");
		if(discountCurveForNotionalReset == null)
			throw new IllegalArgumentException("No discount curve with name '" + discountCurveForNotionalResetName + "' (used to calculate resettable notional) was found in the model.");
		
		ForwardCurveInterface	forwardCurve	= model.getForwardCurve(forwardCurveName);
		DiscountCurveInterface	discountCurveForForward = null;
		if(forwardCurve == null && forwardCurveName != null && forwardCurveName.length() > 0) {
			// User might like to get forward from discount curve.
			discountCurveForForward	= model.getDiscountCurve(forwardCurveName);

			if(discountCurveForForward == null) {
				// User specified a name for the forward curve, but no curve was found.
				throw new IllegalArgumentException("No curve of the name " + forwardCurveName + " was found in the model.");
			}
		}

		double value = 0.0;
		for(int periodIndex=0; periodIndex<legSchedule.getNumberOfPeriods(); periodIndex++) {
			double fixingDate		= legSchedule.getFixing(periodIndex);
			double periodStartDate	= legSchedule.getPeriodStart(periodIndex);
			double periodEndDate	= legSchedule.getPeriodEnd(periodIndex);
			double paymentDate		= legSchedule.getPayment(periodIndex);
			double periodLength		= legSchedule.getPeriodLength(periodIndex);

			/*
			 * We do not count empty periods.
			 * Since empty periods are an indication for a ill-specified
			 * product, it might be reasonable to throw an
			 * illegal argument exception instead.
			 */
			if(periodLength == 0) continue;

			double forward		= spread;
			if(forwardCurve != null) {
				forward += forwardCurve.getForward(model, fixingDate);
				//double periodStartDate	= legSchedule.getPeriodStart(periodIndex);
				//double periodEndDate	= legSchedule.getPeriodEnd(periodIndex);
				//forward += forwardCurve.getForward(model, fixingDate, periodEndDate-periodStartDate); // if forwardCurve=forwardCurveFromDiscountCurve then this takes the swapPeriod as the liborPeriod and there may be small differences. Note that getForward(model, fixingDate) should be the prefered solution 
			}
			else if(discountCurveForForward != null) {
				/*
				 * Classical single curve case: using a discount curve as a forward curve.
				 * This is only implemented for demonstration purposes (an exception would also be appropriate :-)
				 */
				if(fixingDate != paymentDate)
					forward			+= (discountCurveForForward.getDiscountFactor(fixingDate) / discountCurveForForward.getDiscountFactor(paymentDate) - 1.0) / (paymentDate-fixingDate);
			}

			double firstPeriodStartDate	= legSchedule.getPeriodStart(0);
			// note that notional = 1 if discountCurveForNotionalReset = discountCurve
			double notional = (discountCurveForNotionalReset.getDiscountFactor(model,periodStartDate)/discountCurveForNotionalReset.getDiscountFactor(model,firstPeriodStartDate)) / (discountCurve.getDiscountFactor(model,periodStartDate)/discountCurve.getDiscountFactor(model,firstPeriodStartDate));
			double discountFactorPaymentDate = paymentDate > evaluationTime ? discountCurve.getDiscountFactor(model, paymentDate) : 0.0;
			value += notional * forward * periodLength * discountFactorPaymentDate;

			if(isNotionalExchanged) {
				value -= periodStartDate > evaluationTime ? discountCurve.getDiscountFactor(model, periodStartDate) : 0.0;
				value += periodEndDate > evaluationTime ? discountCurve.getDiscountFactor(model, periodEndDate) : 0.0;		
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
		return "SwapLeg [legSchedule=" + legSchedule + ", forwardCurveName="
				+ forwardCurveName + ", spread=" + spread
				+ ", discountCurveName=" + discountCurveName
				+ ", isNotionalExchanged=" + isNotionalExchanged + "]";
	}
}
