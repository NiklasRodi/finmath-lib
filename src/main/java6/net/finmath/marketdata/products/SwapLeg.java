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
		// temporary hack to match Summit behaviour ()
		if(isNotionalExchanged)
			evaluationTime = Math.max(evaluationTime, legSchedule.getPeriodStart(0));
			
		DiscountCurveInterface	discountCurve = model.getDiscountCurve(discountCurveName);
		DiscountCurveInterface	discountCurveForNotionalReset = model.getDiscountCurve(discountCurveForNotionalResetName);
		// Check for discount curve
		if(discountCurve == null)
			throw new IllegalArgumentException("No discount curve with name '" + discountCurveName + "' was found in the model.");
		if(discountCurveForNotionalReset == null)
			throw new IllegalArgumentException("No discount curve with name '" + discountCurveForNotionalResetName + "' (used to calculate resettable notional) was found in the model:\n" + model.toString());
		
		ForwardCurveInterface forwardCurve = model.getForwardCurve(forwardCurveName);
		double firstPeriodStartDate	= legSchedule.getPeriodStart(0);
		double value = 0.0;
		// implement classical single curve formula
		if(forwardCurveName != null && (discountCurveName.equals(forwardCurveName) || (forwardCurve!=null && discountCurveName.equals(forwardCurve.getBaseDiscountCurveName())))) {
			if(spread!=0 || isNotionalExchanged)
				throw new IllegalArgumentException((spread==0?"":("spread = " + spread + "!=0 ")) + (isNotionalExchanged?", isNotionalExchanged":"") + " -> not implemented for singleCurve formulas");
			double lastPeriodEndDate	= legSchedule.getPeriodEnd(legSchedule.getNumberOfPeriods()-1);
			double lastPaymentDate		= legSchedule.getPayment(legSchedule.getNumberOfPeriods()-1);
			// use 0vol-approximation in case lastPaymentDate != lastPeriodEndDate
			value = discountCurve.getDiscountFactor(model, firstPeriodStartDate)*discountCurve.getDiscountFactor(model, lastPaymentDate)/discountCurve.getDiscountFactor(model, lastPeriodEndDate)-discountCurve.getDiscountFactor(model, lastPaymentDate);
		} else {
			if(forwardCurve == null && forwardCurveName != null && forwardCurveName.length() > 0)
				throw new IllegalArgumentException("No forward curve with name '" + forwardCurveName + "' was found in the model:\n" + model.toString());
			
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
					/**
					 * the way the forward is calculated here should go hand in hand with the way it is done in Swap.getForwardSwapRate()
					 * if forwardCurve is a true forwardCurve then forwardCurve.getForward(model,liborFixingDate) is equal to forwardCurve.getForward(model,liborFixingDate,swapPeriodEndDate-swapPeriodStartDate)
					 * however, if forwardCurve=forwardCurveFromDiscountCurve then there may be a difference if the swap and the libor period do not coincide. Note that getForward(model,fixingDate) should be the prefered solution
					 */ 
					//forward += forwardCurve.getForward(model, liborFixingDate);
					forward += forwardCurve.getForward(model, liborFixingDate, swapPeriodEndDate-swapPeriodStartDate); 
				}
				
				// note that notional = 1 if discountCurveForNotionalReset = discountCurve
				double notional = (discountCurveForNotionalReset.getDiscountFactor(model,legSchedule.getPeriodStart(iPeriod))/discountCurveForNotionalReset.getDiscountFactor(model,firstPeriodStartDate)) / (discountCurve.getDiscountFactor(model,legSchedule.getPeriodStart(iPeriod))/discountCurve.getDiscountFactor(model,firstPeriodStartDate));
				double discountFactorPaymentDate = paymentDate < evaluationTime ? 0.0 : discountCurve.getDiscountFactor(model, paymentDate);
				value += notional * (forward+spread) * swapDayCountFraction * discountFactorPaymentDate;
				
				if(isNotionalExchanged) {
					value -= swapPeriodStartDate < evaluationTime ? 0.0 : notional*discountCurve.getDiscountFactor(model, swapPeriodStartDate);
					value += swapPeriodEndDate < evaluationTime ? 0.0 : notional*discountCurve.getDiscountFactor(model, swapPeriodEndDate);		
				}
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

	public double getAnnuity(AnalyticModelInterface model) {
		if(model==null)
			throw new IllegalArgumentException("model==null");
		
		double annuity = SwapAnnuity.getSwapAnnuity(0.0, this.getSchedule(), discountCurveName, model);
		return annuity;
	}
	
	@Override
	public String toString() {
		return "SwapLeg [forwardCurveName=" + forwardCurveName + ", spread=" + spread + ", discountCurveName=" + discountCurveName + ", discountCurveForNotionalResetName=" + discountCurveForNotionalResetName + ", isNotionalExchanged=" + isNotionalExchanged + ", " + legSchedule.toString() + "]\n";
	}
}
