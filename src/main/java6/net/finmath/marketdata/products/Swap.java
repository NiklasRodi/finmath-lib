/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 26.11.2012
 */
package net.finmath.marketdata.products;

import net.finmath.marketdata.model.AnalyticModel;
import net.finmath.marketdata.model.AnalyticModelInterface;
import net.finmath.marketdata.model.curves.CurveInterface;
import net.finmath.marketdata.model.curves.DiscountCurveFromForwardCurve;
import net.finmath.marketdata.model.curves.DiscountCurveInterface;
import net.finmath.marketdata.model.curves.ForwardCurveInterface;
import net.finmath.time.RegularSchedule;
import net.finmath.time.ScheduleInterface;
import net.finmath.time.TimeDiscretizationInterface;

/**
 * Implements the valuation of a swap using curves (discount curve, forward curve).
 * The swap valuation supports distinct discounting and forward curve.
 * Support for day counting is limited to the capabilities of
 * <code>TimeDiscretizationInterface</code>.
 * 
 * The swap is just the composition of two <code>SwapLeg</code>s, namely the
 * receiver leg and the payer leg. The value of the swap is the value of the receiver leg minus the value of the payer leg.
 * 
 * @author Christian Fries
 */
public class Swap extends AbstractAnalyticProduct implements AnalyticProductInterface {

	private final AnalyticProductInterface legReceiver;
	private final AnalyticProductInterface legPayer;

	/**
	 * Create a swap which values as <code>legReceiver - legPayer</code>.
	 * 
	 * @param legReceiver The receiver leg.
	 * @param legPayer The payler leg.
	 */
	public Swap(AnalyticProductInterface legReceiver, AnalyticProductInterface legPayer) {
		super();
		this.legReceiver = legReceiver;
		this.legPayer = legPayer;
	}

	/**
	 * Creates a swap with notional exchange. The swap has a unit notional of 1.
	 * 
	 * @param scheduleReceiveLeg Schedule of the receiver leg.
	 * @param forwardCurveReceiveName Name of the forward curve, leave empty if this is a fix leg.
	 * @param spreadReceive Fixed spread on the forward or fix rate.
	 * @param discountCurveReceiveName Name of the discount curve for the receiver leg.
	 * @param schedulePayLeg Schedule of the payer leg.
	 * @param forwardCurvePayName Name of the forward curve, leave empty if this is a fix leg.
	 * @param spreadPay Fixed spread on the forward or fix rate.
	 * @param discountCurvePayName Name of the discount curve for the payer leg.
	 * @param isNotionalExchanged If true, both leg will pay notional at the beginning of each swap period and receive notional at the end of the swap period. Note that the cash flow date for the notional is periodStart and periodEnd (not fixingDate and paymentDate).
	 */
	public Swap(ScheduleInterface scheduleReceiveLeg,
			String forwardCurveReceiveName, double spreadReceive,
			String discountCurveReceiveName,
			ScheduleInterface schedulePayLeg,
			String forwardCurvePayName, double spreadPay,
			String discountCurvePayName,
			boolean isNotionalExchanged
			) {
		super();
		legReceiver		= new SwapLeg(scheduleReceiveLeg, forwardCurveReceiveName, spreadReceive, discountCurveReceiveName, isNotionalExchanged /* Notional Exchange */);
		legPayer		= new SwapLeg(schedulePayLeg, forwardCurvePayName, spreadPay, discountCurvePayName, isNotionalExchanged /* Notional Exchange */);
	}

	/**
	 * Creates a swap with notional exchange. The swap has a unit notional of 1.
	 * 
	 * @param scheduleReceiveLeg Schedule of the receiver leg.
	 * @param forwardCurveReceiveName Name of the forward curve, leave empty if this is a fix leg.
	 * @param spreadReceive Fixed spread on the forward or fix rate.
	 * @param discountCurveReceiveName Name of the discount curve for the receiver leg.
	 * @param schedulePayLeg Schedule of the payer leg.
	 * @param forwardCurvePayName Name of the forward curve, leave empty if this is a fix leg.
	 * @param spreadPay Fixed spread on the forward or fix rate.
	 * @param discountCurvePayName Name of the discount curve for the payer leg.
	 */
	public Swap(ScheduleInterface scheduleReceiveLeg,
			String forwardCurveReceiveName, double spreadReceive,
			String discountCurveReceiveName,
			ScheduleInterface schedulePayLeg,
			String forwardCurvePayName, double spreadPay,
			String discountCurvePayName) {
		this(scheduleReceiveLeg, forwardCurveReceiveName, spreadReceive, discountCurveReceiveName, schedulePayLeg, forwardCurvePayName, spreadPay, discountCurvePayName, true);
	}

	/**
	 * Creates a swap with notional exchange. The swap has a unit notional of 1.
	 * 
	 * @param scheduleReceiveLeg Schedule of the receiver leg.
	 * @param spreadReceive Fixed spread on the forward or fix rate.
	 * @param discountCurveReceiveName Name of the discount curve for the receiver leg.
	 * @param schedulePayLeg Schedule of the payer leg.
	 * @param forwardCurvePayName Name of the forward curve, leave empty if this is a fix leg.
	 * @param discountCurvePayName Name of the discount curve for the payer leg.
	 */
	public Swap(ScheduleInterface scheduleReceiveLeg,
			double spreadReceive,
			String discountCurveReceiveName,
			ScheduleInterface schedulePayLeg,
			String forwardCurvePayName,
			String discountCurvePayName) {
		this(scheduleReceiveLeg, null, spreadReceive, discountCurveReceiveName, schedulePayLeg, forwardCurvePayName, 0.0, discountCurvePayName, true);
	}


	@Override
	public double getValue(double evaluationTime, AnalyticModelInterface model) {	

		double valueReceiverLeg	= legReceiver.getValue(evaluationTime, model);
		double valuePayerLeg	= legPayer.getValue(evaluationTime, model);

		return valueReceiverLeg - valuePayerLeg;
	}

	static public double getForwardSwapRate(TimeDiscretizationInterface fixTenor, TimeDiscretizationInterface floatTenor, ForwardCurveInterface forwardCurve) {
		return getForwardSwapRate(new RegularSchedule(fixTenor), new RegularSchedule(floatTenor), forwardCurve);
	}

	static public double getForwardSwapRate(ScheduleInterface fixSchedule, ScheduleInterface floatSchedule, ForwardCurveInterface forwardCurve) {
		if(forwardCurve==null)
			throw new IllegalArgumentException("forwardCurve==null.");
		// create discount curve wrapper around forward curve
		DiscountCurveInterface discountCurve = new DiscountCurveFromForwardCurve(forwardCurve.getName());
		AnalyticModelInterface model = new AnalyticModel(new CurveInterface[] {forwardCurve,discountCurve});
		return getForwardSwapRate(fixSchedule, floatSchedule, forwardCurve.getName(), discountCurve.getName(), model);
	}
	
	static public double getForwardSwapRate(TimeDiscretizationInterface fixTenor, TimeDiscretizationInterface floatTenor, ForwardCurveInterface forwardCurve, DiscountCurveInterface discountCurve) {
		if(forwardCurve==null)
			throw new IllegalArgumentException("forwardCurve==null");
		if(discountCurve==null)
			throw new IllegalArgumentException("discountCurve==null");
		AnalyticModel model = new AnalyticModel(new CurveInterface[] {forwardCurve,discountCurve});
		return getForwardSwapRate(new RegularSchedule(fixTenor), new RegularSchedule(floatTenor), forwardCurve.getName(), discountCurve.getName(), model);
	}

	static public double getForwardSwapRate(ScheduleInterface fixSchedule, ScheduleInterface floatSchedule, String forwardCurveName, String discountCurveName, AnalyticModelInterface model) {
		// note that this cannot be simplified to getForwardSwapRate(fixSchedule,floatSchedule,forwardCurve,discountCurve) as I potentially need the model to call forwardCurve.getForward()
		if(model==null)
			throw new IllegalArgumentException("model==null");
		
		ForwardCurveInterface forwardCurve = model.getForwardCurve(forwardCurveName);
		if(forwardCurve==null)
			throw new IllegalArgumentException("Forward curve " + forwardCurveName + " not found in the model.");
		
		DiscountCurveInterface discountCurve = model.getDiscountCurve(discountCurveName);
		if(discountCurve == null)
			throw new IllegalArgumentException("Discount curve " + discountCurveName + " not found in the model.");

		double evaluationTime = fixSchedule.getFixing(0);	// Consider all values
		double swapAnnuity	= SwapAnnuity.getSwapAnnuity(evaluationTime, fixSchedule, discountCurveName, model);

		double floatLeg = 0;
		for(int periodIndex=0; periodIndex<floatSchedule.getNumberOfPeriods(); periodIndex++) {
			double fixingDate		= floatSchedule.getFixing(periodIndex);
			double periodStartDate	= floatSchedule.getPeriodStart(periodIndex);
			double periodEndDate	= floatSchedule.getPeriodEnd(periodIndex);
			double payment			= floatSchedule.getPayment(periodIndex);
			double discountFactor	= discountCurve.getDiscountFactor(model, payment);
			
			/**
			 * the way the forward is calculated here should go hand in hand with the way it is done in SwapLeg.getValue()
			 * if forwardCurve is a true forwardCurve then forwardCurve.getForward(model,liborFixingDate) is equal to forwardCurve.getForward(model,liborFixingDate,swapPeriodEndDate-swapPeriodStartDate)
			 * however, if forwardCurve=forwardCurveFromDiscountCurve then there may be a difference if the swap and the libor period do not coincide. Note that getForward(model,fixingDate) should be the prefered solution
			 */ 
			//double forward			= forwardCurve.getForward(model, fixingDate);
			double forward			= forwardCurve.getForward(model, fixingDate, periodEndDate-periodStartDate);
			
			double periodLength		= floatSchedule.getPeriodLength(periodIndex);
			floatLeg += forward * periodLength * discountFactor;
		}

		double valueFloatLeg = floatLeg / discountCurve.getDiscountFactor(model, evaluationTime);

		return valueFloatLeg / swapAnnuity;
	}
	
	/**
	 * Return the receiver leg of the swap, i.e. the leg who's value is added to the swap value.
	 * 
	 * @return The receiver leg of the swap.
	 */
	public AnalyticProductInterface getLegReceiver() {
		return legReceiver;
	}

	/**
	 * Return the payer leg of the swap, i.e. the leg who's value is subtracted from the swap value.
	 * 
	 * @return The payer leg of the swap.
	 */
	public AnalyticProductInterface getLegPayer() {
		return legPayer;
	}

	@Override
	public String toString() {
		return "Swap [legReceiver=" + legReceiver + ", legPayer=" + legPayer
				+ "]";
	}
}
