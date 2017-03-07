/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 26.11.2012
 */
package net.finmath.marketdata.products;

import net.finmath.marketdata.model.AnalyticModelInterface;
import net.finmath.time.ScheduleInterface;

/**
 * @author Niklas Rodi
 */
public class RateSwap extends Swap {

	private final SwapLeg fixLeg;	// Fix leg of the swap (assumed to be payer)
	private final SwapLeg floatLeg;	// Float leg of the swap (assumed to be receiver)

	public RateSwap(SwapLeg fixLeg, SwapLeg floatLeg) {
		// assume payerSwap (i.e. fixLeg = legPayer, floatLeg = legReceiver)
		super(floatLeg, fixLeg);
		this.fixLeg = fixLeg;
		this.floatLeg = floatLeg;
		
		// data sanity checks
		if(fixLeg.getForwardCurveName()!= null && fixLeg.getForwardCurveName()!="")
			throw new IllegalArgumentException("fixLeg has non-empty forwardCurveName (" + fixLeg.getForwardCurveName() + ")"); 
		if(floatLeg.getForwardCurveName()=="")
			throw new IllegalArgumentException("floatLeg has empty forwardCurveName"); 
		if(fixLeg.getSchedule().getNumberOfPeriods()==0 || floatLeg.getSchedule().getNumberOfPeriods()==0)
			throw new IllegalArgumentException("Either fixLeg (" + fixLeg.getSchedule().getNumberOfPeriods() + ") or floatLeg (" + floatLeg.getSchedule().getNumberOfPeriods() + ") have 0 periods");
		if(fixLeg.getSchedule().getPeriodStart(0) != floatLeg.getSchedule().getPeriodStart(0))
			throw new IllegalArgumentException("fixLeg's first period start (" + fixLeg.getSchedule().getPeriodStart(0) + ") != floatLeg's first period start (" + floatLeg.getSchedule().getPeriodStart(0) + ")");
		if(fixLeg.getSchedule().getPeriodEnd(fixLeg.getSchedule().getNumberOfPeriods()-1) != floatLeg.getSchedule().getPeriodEnd(floatLeg.getSchedule().getNumberOfPeriods()-1))
			throw new IllegalArgumentException("fixLeg's last period end (" + fixLeg.getSchedule().getPeriodEnd(fixLeg.getSchedule().getNumberOfPeriods()-1) + ") != floatLeg's last period end (" + floatLeg.getSchedule().getPeriodEnd(floatLeg.getSchedule().getNumberOfPeriods()-1) + ")");
	}

	public RateSwap(
			ScheduleInterface scheduleReceiveLeg, String forwardCurveReceiveName, double spreadReceive, String discountCurveReceiveName,
			ScheduleInterface schedulePayLeg, String forwardCurvePayName, double spreadPay, String discountCurvePayName,
			boolean isNotionalExchanged
			) {
		this(new SwapLeg(scheduleReceiveLeg, forwardCurveReceiveName, spreadReceive, discountCurveReceiveName, isNotionalExchanged), 
			 new SwapLeg(schedulePayLeg, forwardCurvePayName, spreadPay, discountCurvePayName, isNotionalExchanged));
	}
	
	/**
	 * Get swap's forwardSwapRate (i.e. spread of fixLeg).
	 */
	public double getForwardSwapRate(){
		if(fixLeg==null)
			throw new IllegalArgumentException("empty fix leg");
		return fixLeg.getSpread();
	}
	
	/**
	 * Calculate swap's forwardSwapRate for given model.
	 */
	public double getForwardSwapRate(AnalyticModelInterface model){
		if(fixLeg==null || floatLeg==null)
			throw new IllegalArgumentException("Either fix or float leg is empty");
		
		double floatLegValue = floatLeg.getValue(0.0, model);
		double annuity = fixLeg.getAnnuity(model);
		if(annuity==0)
			throw new IllegalArgumentException("annuity==0");
		
		double forwardSwapRate = floatLegValue/annuity;
		return forwardSwapRate;
	}

	public double getSwapStart(){
		return fixLeg.getSchedule().getPeriodStart(0);
	}
	
	public double getSwapMaturity(){
		return fixLeg.getSchedule().getPeriodEnd(fixLeg.getSchedule().getNumberOfPeriods()-1);
	}
	
	@Override
	public String toString() {
		return "Swap [fixLeg=" + fixLeg + ", floatLeg=" + floatLeg + "]";
	}
}
