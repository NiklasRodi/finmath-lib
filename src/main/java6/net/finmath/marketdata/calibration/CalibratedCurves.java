/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 30.11.2012
 */
package net.finmath.marketdata.calibration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.finmath.marketdata.model.AnalyticModel;
import net.finmath.marketdata.model.AnalyticModelInterface;
import net.finmath.marketdata.model.curves.CurveInterface;
import net.finmath.marketdata.model.curves.DiscountCurveFromForwardCurve;
import net.finmath.marketdata.model.curves.DiscountCurveInterface;
import net.finmath.marketdata.model.curves.ForwardCurveFromDiscountCurve;
import net.finmath.marketdata.model.curves.ForwardCurveInterface;
import net.finmath.marketdata.products.AnalyticProductInterface;
import net.finmath.marketdata.products.Deposit;
import net.finmath.marketdata.products.ForwardRateAgreement;
import net.finmath.marketdata.products.Swap;
import net.finmath.marketdata.products.SwapLeg;
import net.finmath.marketdata.products.SwapLegWithResetting;
import net.finmath.optimizer.SolverException;
import net.finmath.time.RegularSchedule;
import net.finmath.time.ScheduleInterface;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.businessdaycalendar.BusinessdayCalendarExcludingTARGETHolidays;
import net.finmath.time.businessdaycalendar.BusinessdayCalendarInterface;

/**
 * Generate a collection of calibrated curves (discount curves, forward curves)
 * from a vector of calibration products.
 * 
 * An object of this class provides a calibration of curves (using multi-curves, forward curve, discount curve).
 * Sometimes this is referred as curve bootstrapping, however the algorithm used here is not a bootstrap.
 * 
 * The calibration products have to be provided via a vector of <code>CalibrationSpec</code>s.
 * 
 * The products provides are
 * <table summary="List of calibration products types">
 * 	<tr>
 * 		<td>Value of Type String</td>
 * 		<td>Classes</td>
 * 		<td>Note</td>
 * 	</tr>
 * 	<tr>
 * 		<td>swap</td>
 * 		<td>{@link net.finmath.marketdata.products.Swap}</td>
 * 		<td></td>
 * 	</tr>
 * 	<tr>
 * 		<td>swapleg</td>
 * 		<td>{@link net.finmath.marketdata.products.SwapLeg}</td>
 * 		<td>Only the receiver part of <code>CalibrationSpec</code> is used.</td>
 * 	</tr>
 * 	<tr>
 * 		<td>swapwithresetonreceiver</td>
 * 		<td>{@link net.finmath.marketdata.products.SwapLeg}, {@link net.finmath.marketdata.products.SwapLegWithResetting}</td>
 * 		<td></td>
 * 	</tr>
 * 	<tr>
 * 		<td>swapwithresetonpayer</td>
 * 		<td>{@link net.finmath.marketdata.products.SwapLeg}, {@link net.finmath.marketdata.products.SwapLegWithResetting}</td>
 * 		<td></td>
 * 	</tr>
 * 	<tr>
 * 		<td>deposit</td>
 * 		<td>{@link net.finmath.marketdata.products.Deposit}</td>
 * 		<td>Only the receiver part of <code>CalibrationSpec</code> is used.</td>
 * 	</tr>
 * 	<tr>
 * 		<td>fra</td>
 * 		<td>{@link net.finmath.marketdata.products.ForwardRateAgreement}</td>
 * 		<td>Only the receiver part of <code>CalibrationSpec</code> is used.</td>
 * 	</tr>
 * </table>
 * 
 * For a demo spreadsheet using this class see <a href="http://finmath.net/topics/curvecalibration/">finmath.net/topics/curvecalibration/</a>.
 * 
 * @author Christian Fries
 */
public class CalibratedCurves {

	private static final boolean doWrapCurves;		// indicates whether discountCurves/forwardCurves are automatically wrapped into ForwardCurvesFromDiscountCurves/DiscountCurvesFromForwardCurves if necessary 
	//private static final boolean isUseForwardCurve;
	static {
		doWrapCurves = Boolean.parseBoolean(System.getProperty("net.finmath.marketdata.calibration.CalibratedCurves.doWrapCurves","true"));
		//isUseForwardCurve = Boolean.parseBoolean(System.getProperty("net.finmath.marketdata.calibration.CalibratedCurves.isUseForwardCurve","true"));
	}

	/**
	 * Specification of calibration product.
	 * 
	 * @author Christian Fries
	 */
	public static class CalibrationSpec {

		private String				symbol;

		private String				type;

		private	ScheduleInterface	swapTenorDefinitionReceiver;
		private String				forwardCurveReceiverName;
		private double				spreadReceiver;
		private String				discountCurveReceiverName;

		private ScheduleInterface	swapTenorDefinitionPayer;
		private String				forwardCurvePayerName;
		private double				spreadPayer;
		private String				discountCurvePayerName;

		private String				calibrationCurveName;
		private double				calibrationTime;

		/**
		 * Calibration specification.
		 * 
		 * @param symbol A string identifying the calibration product. This string can be used in sensitivity calculation, allowing to bump the spread in a finite difference approximation. See <code>getCloneShifted</code> method.
		 * @param type The type of the calibration product.
		 * @param swapTenorDefinitionReceiver The schedule of periods of the receiver leg.
		 * @param forwardCurveReceiverName The forward curve of the receiver leg (may be null).
		 * @param spreadReceiver The spread or fixed coupon of the receiver leg.
		 * @param discountCurveReceiverName The discount curve of the receiver leg.
		 * @param swapTenorDefinitionPayer The schedule of periods of the payer leg.
		 * @param forwardCurvePayerName The forward curve of the payer leg (may be null).
		 * @param spreadPayer The spread or fixed coupon of the payer leg.
		 * @param discountCurvePayerName The discount curve of the payer leg.
		 * @param calibrationCurveName The curve to calibrate, by this product.
		 * @param calibrationTime The time point in calibrationCurveName used to calibrate, by this product.
		 */
		public CalibrationSpec(
				String symbol,
				String type,
				ScheduleInterface swapTenorDefinitionReceiver,
				String forwardCurveReceiverName, double spreadReceiver,
				String discountCurveReceiverName,
				ScheduleInterface swapTenorDefinitionPayer,
				String forwardCurvePayerName, double spreadPayer,
				String discountCurvePayerName,
				String calibrationCurveName,
				double calibrationTime) {
			super();
			this.symbol = symbol;
			this.type = type;
			this.swapTenorDefinitionReceiver = swapTenorDefinitionReceiver;
			this.forwardCurveReceiverName = forwardCurveReceiverName;
			this.spreadReceiver = spreadReceiver;
			this.discountCurveReceiverName = discountCurveReceiverName;
			this.swapTenorDefinitionPayer = swapTenorDefinitionPayer;
			this.forwardCurvePayerName = forwardCurvePayerName;
			this.spreadPayer = spreadPayer;
			this.discountCurvePayerName = discountCurvePayerName;
			this.calibrationCurveName = calibrationCurveName;
			this.calibrationTime = calibrationTime;
		}

		/**
		 * Calibration specification.
		 * 
		 * @param type The type of the calibration product.
		 * @param swapTenorDefinitionReceiver The schedule of periods of the receiver leg.
		 * @param forwardCurveReceiverName The forward curve of the receiver leg (may be null).
		 * @param spreadReceiver The spread or fixed coupon of the receiver leg.
		 * @param discountCurveReceiverName The discount curve of the receiver leg.
		 * @param swapTenorDefinitionPayer The schedule of periods of the payer leg.
		 * @param forwardCurvePayerName The forward curve of the payer leg (may be null).
		 * @param spreadPayer The spread or fixed coupon of the payer leg.
		 * @param discountCurvePayerName The discount curve of the payer leg.
		 * @param calibrationCurveName The curve to calibrate, by this product.
		 * @param calibrationTime The time point in calibrationCurveName used to calibrate, by this product.
		 */
		public CalibrationSpec(
				String type,
				ScheduleInterface swapTenorDefinitionReceiver,
				String forwardCurveReceiverName, double spreadReceiver,
				String discountCurveReceiverName,
				ScheduleInterface swapTenorDefinitionPayer,
				String forwardCurvePayerName, double spreadPayer,
				String discountCurvePayerName,
				String calibrationCurveName,
				double calibrationTime) {
			this(null, type, swapTenorDefinitionReceiver, forwardCurveReceiverName, spreadReceiver, discountCurveReceiverName, swapTenorDefinitionPayer, forwardCurvePayerName, spreadPayer, discountCurvePayerName, calibrationCurveName, calibrationTime);
		}

		/**
		 * Calibration specification.
		 * 
		 * @param type The type of the calibration product.
		 * @param swapTenorDefinitionReceiver The schedule of periods of the receiver leg.
		 * @param forwardCurveReceiverName The forward curve of the receiver leg (may be null).
		 * @param spreadReceiver The spread or fixed coupon of the receiver leg.
		 * @param discountCurveReceiverName The discount curve of the receiver leg.
		 * @param swapTenorDefinitionPayer The schedule of periods of the payer leg.
		 * @param forwardCurvePayerName The forward curve of the payer leg (may be null).
		 * @param spreadPayer The spread or fixed coupon of the payer leg.
		 * @param discountCurvePayerName The discount curve of the payer leg.
		 * @param calibrationCurveName The curve to calibrate, by this product.
		 * @param calibrationTime The time point in calibrationCurveName used to calibrate, by this product.
		 */
		public CalibrationSpec(
				String type,
				double[] swapTenorDefinitionReceiver,
				String forwardCurveReceiverName, double spreadReceiver,
				String discountCurveReceiverName,
				double[] swapTenorDefinitionPayer,
				String forwardCurvePayerName, double spreadPayer,
				String discountCurvePayerName,
				String calibrationCurveName,
				double calibrationTime) {
			super();
			this.type = type;
			this.swapTenorDefinitionReceiver = new RegularSchedule(new TimeDiscretization(swapTenorDefinitionReceiver[0] /* initial */, swapTenorDefinitionReceiver[1] /* numberOfTimeSteps */, swapTenorDefinitionReceiver[2] /* deltaT */, TimeDiscretization.ShortPeriodLocation.SHORT_PERIOD_AT_START));
			this.forwardCurveReceiverName = forwardCurveReceiverName;
			this.spreadReceiver = spreadReceiver;
			this.discountCurveReceiverName = discountCurveReceiverName;
			this.swapTenorDefinitionPayer = new RegularSchedule(new TimeDiscretization(swapTenorDefinitionPayer[0] /* initial */, swapTenorDefinitionPayer[1] /* numberOfTimeSteps */, swapTenorDefinitionPayer[2] /* deltaT */, TimeDiscretization.ShortPeriodLocation.SHORT_PERIOD_AT_START));
			this.forwardCurvePayerName = forwardCurvePayerName;
			this.spreadPayer = spreadPayer;
			this.discountCurvePayerName = discountCurvePayerName;
			this.calibrationCurveName = calibrationCurveName;
			this.calibrationTime = calibrationTime;
		}

		/**
		 * Calibration specification.
		 * 
		 * @param type The type of the calibration product.
		 * @param swapTenorDefinitionReceiver The schedule of periods of the receiver leg.
		 * @param forwardCurveReceiverName The forward curve of the receiver leg (may be null).
		 * @param spreadReceiver The spread or fixed coupon of the receiver leg.
		 * @param discountCurveReceiverName The discount curve of the receiver leg.
		 * @param calibrationCurveName The curve to calibrate, by this product.
		 * @param calibrationTime The time point in calibrationCurveName used to calibrate, by this product.
		 */
		public CalibrationSpec(
				String type,
				double[] swapTenorDefinitionReceiver,
				String forwardCurveReceiverName, double spreadReceiver,
				String discountCurveReceiverName,
				String calibrationCurveName,
				double calibrationTime) {
			super();
			this.type = type;
			this.swapTenorDefinitionReceiver = new RegularSchedule(new TimeDiscretization(swapTenorDefinitionReceiver[0] /* initial */, swapTenorDefinitionReceiver[1] /* numberOfTimeSteps */, swapTenorDefinitionReceiver[2] /* deltaT */, TimeDiscretization.ShortPeriodLocation.SHORT_PERIOD_AT_START));
			this.forwardCurveReceiverName = forwardCurveReceiverName;
			this.spreadReceiver = spreadReceiver;
			this.discountCurveReceiverName = discountCurveReceiverName;
			this.calibrationCurveName = calibrationCurveName;
			this.calibrationTime = calibrationTime;
		}

		public CalibrationSpec getCloneShifted(double shift) {
			if(discountCurvePayerName == null || type.toLowerCase().equals("swapleg")  || type.toLowerCase().equals("deposit")  || type.toLowerCase().equals("fra")) {
				return new CalibrationSpec(symbol, type, swapTenorDefinitionReceiver, forwardCurveReceiverName, spreadReceiver+shift, discountCurveReceiverName, swapTenorDefinitionPayer, forwardCurvePayerName, spreadPayer, discountCurvePayerName, calibrationCurveName, calibrationTime);
			}
			else {
				return new CalibrationSpec(symbol, type, swapTenorDefinitionReceiver, forwardCurveReceiverName, spreadReceiver, discountCurveReceiverName, swapTenorDefinitionPayer, forwardCurvePayerName, spreadPayer+shift, discountCurvePayerName, calibrationCurveName, calibrationTime);
			}
		}

		@Override
		public String toString() {
			return "CalibrationSpec [symbol=" + symbol + ", type=" + type + ", swapTenorDefinitionReceiver="
					+ swapTenorDefinitionReceiver + ", forwardCurveReceiverName=" + forwardCurveReceiverName
					+ ", spreadReceiver=" + spreadReceiver + ", discountCurveReceiverName=" + discountCurveReceiverName
					+ ", swapTenorDefinitionPayer=" + swapTenorDefinitionPayer + ", forwardCurvePayerName="
					+ forwardCurvePayerName + ", spreadPayer=" + spreadPayer + ", discountCurvePayerName="
					+ discountCurvePayerName + ", calibrationCurveName=" + calibrationCurveName + ", calibrationTime="
					+ calibrationTime + "]";
		}
	}

	private AnalyticModelInterface				model				= new AnalyticModel();
	private Set<ParameterObjectInterface>		objectsToCalibrate	= new LinkedHashSet<ParameterObjectInterface>();
	private Vector<AnalyticProductInterface>	calibrationProducts			= new Vector<AnalyticProductInterface>();
	private Vector<String>						calibrationProductsSymbols	= new Vector<String>();

	private List<CalibrationSpec>				calibrationSpecs	= new ArrayList<CalibrationSpec>();

	private final double evaluationTime;
	private final double calibrationAccuracy;

	private int lastNumberOfInterations;
	private double lastAccuracy;

	/**
	 * Generate a collection of calibrated curves (discount curves, forward curves)
	 * from a vector of calibration products and a given model.
	 * 
	 * If the model already contains a curve referenced as calibration curve that
	 * curve is replaced by a clone, retaining the given curve information and
	 * adding a new calibration point.
	 * 
	 * If the model does not contain the curve referenced as calibration curve, the
	 * curve will be added to the model. 
	 * 
	 * Use case: You already have a discount curve as part of the model and like
	 * to calibrate an additional curve to an additional set of instruments.
	 * 
	 * @param calibrationSpecs Array of calibration specs.
	 * @param calibrationModel A given model used to value the calibration products.
	 * @param evaluationTime Evaluation time applied to the calibration products.
	 * @param calibrationAccuracy Error tolerance of the solver. Set to 0 if you need machine precision.
	 * @throws net.finmath.optimizer.SolverException May be thrown if the solver does not cannot find a solution of the calibration problem. 
	 * @throws CloneNotSupportedException Thrown, when a curve could not be cloned.
	 */
	public CalibratedCurves(List<CalibrationSpec> calibrationSpecs, AnalyticModelInterface calibrationModel, double evaluationTime, double calibrationAccuracy) throws SolverException, CloneNotSupportedException {
		if(calibrationModel != null)	model	= calibrationModel.getCloneForParameter(null);
		this.evaluationTime = evaluationTime;
		this.calibrationAccuracy = calibrationAccuracy;

		for(CalibrationSpec calibrationSpec : calibrationSpecs) {
			add(calibrationSpec);
		}

		lastNumberOfInterations = calibrate(calibrationAccuracy);
	}

	/**
	 * Generate a collection of calibrated curves (discount curves, forward curves)
	 * from a vector of calibration products and a given model.
	 * 
	 * If the model already contains a curve referenced as calibration curve that
	 * curve is replaced by a clone, retaining the given curve information and
	 * adding a new calibration point.
	 * 
	 * If the model does not contain the curve referenced as calibration curve, the
	 * curve will be added to the model. 
	 * 
	 * Use case: You already have a discount curve as part of the model and like
	 * to calibrate an additional curve to an additional set of instruments.
	 * 
	 * @param calibrationSpecs Array of calibration specs.
	 * @param calibrationModel A given model used to value the calibration products.
	 * @param evaluationTime Evaluation time applied to the calibration products.
	 * @param calibrationAccuracy Error tolerance of the solver. Set to 0 if you need machine precision.
	 * @throws net.finmath.optimizer.SolverException May be thrown if the solver does not cannot find a solution of the calibration problem. 
	 * @throws CloneNotSupportedException Thrown, when a curve could not be cloned.
	 */
	public CalibratedCurves(CalibrationSpec[] calibrationSpecs, AnalyticModel calibrationModel, double evaluationTime, double calibrationAccuracy) throws SolverException, CloneNotSupportedException {
		if(calibrationModel != null)	model	= calibrationModel.getCloneForParameter(null);
		this.evaluationTime = evaluationTime;
		this.calibrationAccuracy = calibrationAccuracy;

		for(CalibrationSpec calibrationSpec : calibrationSpecs) {
			add(calibrationSpec);
		}

		lastNumberOfInterations = calibrate(calibrationAccuracy);
	}

	/**
	 * Generate a collection of calibrated curves (discount curves, forward curves)
	 * from a vector of calibration products and a given model.
	 * 
	 * If the model already contains a curve referenced as calibration curve that
	 * curve is replaced by a clone, retaining the given curve information and
	 * adding a new calibration point.
	 * 
	 * If the model does not contain the curve referenced as calibration curve, the
	 * curve will be added to the model. 
	 * 
	 * Use case: You already have a discount curve as part of the model and like
	 * to calibrate an additional curve to an additional set of instruments.
	 * 
	 * @param calibrationSpecs Array of calibration specs.
	 * @param calibrationModel A given model used to value the calibration products.
	 * @param calibrationAccuracy Error tolerance of the solver. Set to 0 if you need machine precision.
	 * @throws net.finmath.optimizer.SolverException May be thrown if the solver does not cannot find a solution of the calibration problem. 
	 * @throws CloneNotSupportedException Thrown, when a curve could not be cloned.
	 */
	public CalibratedCurves(CalibrationSpec[] calibrationSpecs, AnalyticModel calibrationModel, double calibrationAccuracy) throws SolverException, CloneNotSupportedException {
		this(calibrationSpecs, calibrationModel, 0.0, calibrationAccuracy);
	}

	/**
	 * Generate a collection of calibrated curves (discount curves, forward curves)
	 * from a vector of calibration products and a given model.
	 * 
	 * If the model already contains a curve referenced as calibration curve that
	 * curve is replaced by a clone, retaining the given curve information and
	 * adding a new calibration point.
	 * 
	 * If the model does not contain the curve referenced as calibration curve, the
	 * curve will be added to the model. 
	 * 
	 * Use case: You already have a discount curve as part of the model and like
	 * to calibrate an additional curve to an additional set of instruments.
	 * 
	 * @param calibrationSpecs Array of calibration specs.
	 * @param calibrationModel A given model used to value the calibration products.
	 * @throws net.finmath.optimizer.SolverException May be thrown if the solver does not cannot find a solution of the calibration problem. 
	 * @throws CloneNotSupportedException Thrown, when a curve could not be cloned.
	 */
	public CalibratedCurves(CalibrationSpec[] calibrationSpecs, AnalyticModel calibrationModel) throws SolverException, CloneNotSupportedException {
		this(calibrationSpecs, calibrationModel, 0.0);
	}

	/**
	 * Generate a collection of calibrated curves (discount curves, forward curves)
	 * from a vector of calibration products.
	 * 
	 * @param calibrationSpecs Array of calibration specs.
	 * @throws net.finmath.optimizer.SolverException May be thrown if the solver does not cannot find a solution of the calibration problem. 
	 * @throws CloneNotSupportedException Thrown, when a curve could not be cloned.
	 */
	public CalibratedCurves(Collection<CalibrationSpec> calibrationSpecs) throws SolverException, CloneNotSupportedException {
		this(calibrationSpecs.toArray(new CalibrationSpec[calibrationSpecs.size()]), null);
	}

	/**
	 * Generate a collection of calibrated curves (discount curves, forward curves)
	 * from a vector of calibration products.
	 * 
	 * @param calibrationSpecs Array of calibration specs.
	 * @throws net.finmath.optimizer.SolverException May be thrown if the solver does not cannot find a solution of the calibration problem. 
	 * @throws CloneNotSupportedException Thrown, when a curve could not be cloned.
	 */
	public CalibratedCurves(CalibrationSpec[] calibrationSpecs) throws SolverException, CloneNotSupportedException {
		this(calibrationSpecs, null, 0.0);
	}

	public AnalyticProductInterface getCalibrationProductForSpec(CalibrationSpec calibrationSpec) {
		// get name of receiver discounting and forward curve - due to wrapping they may be different than what the user has entered
		String discountCurveReceiverName = calibrationSpec.discountCurveReceiverName;
		discountCurveReceiverName = createDiscountCurve(discountCurveReceiverName);
		String forwardCurveReceiverName = calibrationSpec.forwardCurveReceiverName;
		forwardCurveReceiverName = createForwardCurve(calibrationSpec.swapTenorDefinitionReceiver, forwardCurveReceiverName);
		
		AnalyticProductInterface product = null;
		if(calibrationSpec.type.toLowerCase().equals("deposit")){
			product = new Deposit(calibrationSpec.swapTenorDefinitionReceiver, calibrationSpec.spreadReceiver, calibrationSpec.discountCurveReceiverName);
		} else if(calibrationSpec.type.toLowerCase().equals("fra")){
			product = new ForwardRateAgreement(calibrationSpec.swapTenorDefinitionReceiver, calibrationSpec.spreadReceiver, forwardCurveReceiverName, discountCurveReceiverName);
		} else if(calibrationSpec.type.toLowerCase().equals("future")){
			// like a fra but future price needs to be translated into rate
			product = new ForwardRateAgreement(calibrationSpec.swapTenorDefinitionReceiver, 1.0-calibrationSpec.spreadReceiver/100.0, forwardCurveReceiverName, discountCurveReceiverName);
		} else if(calibrationSpec.type.toLowerCase().equals("swapleg")) {
			product = new SwapLeg(calibrationSpec.swapTenorDefinitionReceiver, forwardCurveReceiverName, calibrationSpec.spreadReceiver, discountCurveReceiverName, true);
		} else {
			// note that the products so far did not require a payer discounting or forward curve
			// get name of payer discounting and forward curve - due to wrapping they may be different than what the user has entered
			String discountCurvePayerName = calibrationSpec.discountCurvePayerName;
			discountCurvePayerName = createDiscountCurve(discountCurvePayerName);
			String forwardCurvePayerName = calibrationSpec.forwardCurvePayerName;
			forwardCurvePayerName = createForwardCurve(calibrationSpec.swapTenorDefinitionPayer, forwardCurvePayerName);
						
		
			if(calibrationSpec.type.toLowerCase().equals("swap")) {
				product = new Swap(calibrationSpec.swapTenorDefinitionReceiver, forwardCurveReceiverName, calibrationSpec.spreadReceiver, calibrationSpec.discountCurveReceiverName, calibrationSpec.swapTenorDefinitionPayer, forwardCurvePayerName, calibrationSpec.spreadPayer, calibrationSpec.discountCurvePayerName);
			} else if(calibrationSpec.type.toLowerCase().equals("swapwithresetonreceiver")) {
				String discountCurveForNotionalResetName = discountCurvePayerName;
				SwapLeg	legReceiver	= new SwapLeg(calibrationSpec.swapTenorDefinitionReceiver, forwardCurveReceiverName, calibrationSpec.spreadReceiver, discountCurveReceiverName, discountCurveForNotionalResetName, true);
				SwapLeg	legPayer	= new SwapLeg(calibrationSpec.swapTenorDefinitionPayer, forwardCurvePayerName, calibrationSpec.spreadPayer, discountCurvePayerName, true);
				product = new Swap(legReceiver, legPayer);
			} else if(calibrationSpec.type.toLowerCase().equals("swapwithresetonpayer")) {
				String discountCurveForNotionalResetName = calibrationSpec.discountCurveReceiverName;
				SwapLeg					legReceiver	= new SwapLeg(calibrationSpec.swapTenorDefinitionReceiver, forwardCurveReceiverName, calibrationSpec.spreadReceiver, calibrationSpec.discountCurveReceiverName, true);
				SwapLegWithResetting	legPayer	= new SwapLegWithResetting(calibrationSpec.swapTenorDefinitionPayer, forwardCurvePayerName, calibrationSpec.spreadPayer, calibrationSpec.discountCurvePayerName, discountCurveForNotionalResetName, true);
				product = new Swap(legReceiver, legPayer);
			} else {
				throw new RuntimeException("Product of type " + calibrationSpec.type + " unknown.");
			}
		}

		return product;
	}

	/**
	 * Return the calibrated model, i.e., the model maintaining a collection of curves calibrated to the
	 * given calibration specifications.
	 * 
	 * @return The calibrated model.
	 */
	public AnalyticModelInterface getModel() {
		return model;
	}

	/**
	 * Get a curve for a given name.
	 * 
	 * @param name Name of the curve
	 * @return The curve model.
	 */
	public CurveInterface getCurve(String name) {
		return model.getCurve(name);
	}

	/**
	 * Return the number of iterations needed to calibrate the model.
	 * 
	 * @return The number of iterations needed to calibrate the model.
	 */
	public int getLastNumberOfInterations() {
		return lastNumberOfInterations;
	}

	/**
	 * Returns the set curves calibrated to "shifted" market data, that is,
	 * the market date of <code>this</code> object, modified by the shifts
	 * provided to this methods.
	 * 
	 * @param symbol The symbol to shift. All other symbols remain unshifted.
	 * @param shift The shift to apply to the symbol.
	 * @return A new set of calibrated curves, calibrated to shifted market data.
	 * @throws SolverException The likely cause of this exception is a failure of the solver used in the calibration.
	 * @throws CloneNotSupportedException The likely cause of this exception is the inability to clone or modify a curve.
	 */
	public CalibratedCurves getCloneShifted(String symbol, double shift) throws SolverException, CloneNotSupportedException {
		// Clone calibration specs, shifting the desired symbol
		List<CalibrationSpec> calibrationSpecsShifted = new ArrayList<CalibrationSpec>();
		for(CalibrationSpec calibrationSpec : calibrationSpecs) {
			if(calibrationSpec.symbol.equals(symbol)) {
				calibrationSpecsShifted.add(calibrationSpec.getCloneShifted(shift));
			}
			else {
				calibrationSpecsShifted.add(calibrationSpec);				
			}
		}

		return new CalibratedCurves(calibrationSpecsShifted, model, evaluationTime, calibrationAccuracy);
	}

	/**
	 * Returns the set curves calibrated to "shifted" market data, that is,
	 * the market date of <code>this</code> object, modified by the shifts
	 * provided to this methods.
	 * 
	 * @param shifts A map of shifts associating each symbol with a shifts. If symbols are not part of this map, they remain unshifted.
	 * @return A new set of calibrated curves, calibrated to shifted market data.
	 * @throws SolverException The likely cause of this exception is a failure of the solver used in the calibration.
	 * @throws CloneNotSupportedException The likely cause of this exception is the inability to clone or modify a curve.
	 */
	public CalibratedCurves getCloneShifted(Map<String,Double> shifts) throws SolverException, CloneNotSupportedException {
		// Clone calibration specs, shifting the desired symbol
		List<CalibrationSpec> calibrationSpecsShifted = new ArrayList<CalibrationSpec>();
		for(CalibrationSpec calibrationSpec : calibrationSpecs) {
			if(shifts.containsKey(calibrationSpec)) {
				calibrationSpecsShifted.add(calibrationSpec.getCloneShifted(shifts.get(calibrationSpec)));
			}
			else {
				calibrationSpecsShifted.add(calibrationSpec);				
			}
		}

		return new CalibratedCurves(calibrationSpecsShifted, model, evaluationTime, calibrationAccuracy);
	}

	/**
	 * Returns the set curves calibrated to "shifted" market data, that is,
	 * the market date of <code>this</code> object, modified by the shifts
	 * provided to this methods.
	 * 
	 * This method will shift all symbols matching a given regular expression <code>Pattern</code>.
	 * 
	 * @see java.util.regex.Pattern
	 * 
	 * @param symbolRegExp A pattern, identifying the symbols to shift.
	 * @param shift The shift to apply to the symbol(s).
	 * @return A new set of calibrated curves, calibrated to shifted market data.
	 * @throws SolverException The likely cause of this exception is a failure of the solver used in the calibration.
	 * @throws CloneNotSupportedException The likely cause of this exception is the inability to clone or modify a curve.
	 */
	public CalibratedCurves getCloneShifted(Pattern symbolRegExp, double shift) throws SolverException, CloneNotSupportedException {
		// Clone calibration specs, shifting the desired symbol
		List<CalibrationSpec> calibrationSpecsShifted = new ArrayList<CalibrationSpec>();

		for(CalibrationSpec calibrationSpec : calibrationSpecs) {
			Matcher matcher = symbolRegExp.matcher(calibrationSpec.symbol);
			if(matcher.matches()) {
				calibrationSpecsShifted.add(calibrationSpec.getCloneShifted(shift));
			}
			else {
				calibrationSpecsShifted.add(calibrationSpec);				
			}
		}

		return new CalibratedCurves(calibrationSpecsShifted, model, evaluationTime, calibrationAccuracy);
	}

	/**
	 * Returns the set curves calibrated to "shifted" market data, that is,
	 * the market date of <code>this</code> object, modified by the shifts
	 * provided to this methods.
	 * 
	 * This method will shift all symbols matching a given regular expression.
	 * 
	 * @see java.util.regex.Pattern
	 * 
	 * @param symbolRegExp A string representing a regular expression, identifying the symbols to shift.
	 * @param shift The shift to apply to the symbol(s).
	 * @return A new set of calibrated curves, calibrated to shifted market data.
	 * @throws SolverException The likely cause of this exception is a failure of the solver used in the calibration.
	 * @throws CloneNotSupportedException The likely cause of this exception is the inability to clone or modify a curve.
	 */
	public CalibratedCurves getCloneShiftedForRegExp(String symbolRegExp, double shift) throws SolverException, CloneNotSupportedException {
		return getCloneShifted(Pattern.compile(symbolRegExp), shift);
	}

	/**
	 * Return the accuracy achieved in the last calibration.
	 * 
	 * @return The accuracy achieved in the last calibration.
	 */
	public double getLastAccuracy() {
		return lastAccuracy;
	}
	
	/**
	 * Returns the first product found in the vector of calibration products
	 * which matches the given symbol, where symbol is the String set in
	 * the calibrationSpecs.
	 * 
	 * @param symbol A given symbol string.
	 * @return The product associated with that symbol.
	 */
	public AnalyticProductInterface getCalibrationProductForSymbol(String symbol) {
		
		/*
		 * The internal data structure is not optimal here (a map would make more sense here),
		 * if the user does not require access to the products, we would allow non-unique symbols.
		 * Hence we store both in two side by side vectors.
		 */
		for(int i=0; i<calibrationProductsSymbols.size(); i++) {
			String calibrationProductSymbol = calibrationProductsSymbols.get(i);
			if(calibrationProductSymbol.equals(symbol)) return calibrationProducts.get(i);
		}
		
		return null;
	}

	private int calibrate(double accuracy) throws SolverException {
		Solver solver = new Solver(model, calibrationProducts, evaluationTime, accuracy);
		model = solver.getCalibratedModel(objectsToCalibrate);

		lastAccuracy = solver.getAccuracy();

		return solver.getIterations();
	}

	/**
	 * Add a calibration product to the set of calibration instruments.
	 * 
	 * @param calibrationSpec The spec of the calibration product.
	 * @throws CloneNotSupportedException Thrown if a curve could not be cloned / created.
	 */
	private String add(CalibrationSpec calibrationSpec) throws CloneNotSupportedException
	{
		calibrationSpecs.add(calibrationSpec);

		/* 
		 * Add one point to the calibration curve and one new objective function
		 */

		// Create calibration product (will also create the curve if necessary)
		calibrationProducts.add(getCalibrationProductForSpec(calibrationSpec));
		calibrationProductsSymbols.add(calibrationSpec.symbol);

		// Create parameter to calibrate

		// Fetch old curve
		CurveInterface calibrationCurveOld = model.getCurve(calibrationSpec.calibrationCurveName);
		if(calibrationCurveOld == null) throw new IllegalArgumentException("Calibration curve " + calibrationSpec.calibrationCurveName + " does not exist. This should not happen. Possible reason: The given calibration product does not depend on the given calibration curve.");

		// Remove old curve
		objectsToCalibrate.remove(calibrationCurveOld);

		// Create and add new curve
		CurveInterface calibrationCurve = null;
		if(DiscountCurveInterface.class.isInstance(calibrationCurveOld)) {
			@SuppressWarnings("unused")
			double paymentTime	= calibrationSpec.swapTenorDefinitionReceiver.getPayment(calibrationSpec.swapTenorDefinitionReceiver.getNumberOfPeriods()-1);

			// Build new curve with one additional point
			calibrationCurve = calibrationCurveOld
					.getCloneBuilder()
					.addPoint(calibrationSpec.calibrationTime, 1.0, true)
					.build();
		}
		else if(ForwardCurveInterface.class.isInstance(calibrationCurveOld)) {
			// Build new curve with one additional point
			calibrationCurve = calibrationCurveOld
					.getCloneBuilder()
					.addPoint(calibrationSpec.calibrationTime, 0.1, true)
					.build();
		}
		else {
			// Build new curve with one additional point
			calibrationCurve = calibrationCurveOld
					.getCloneBuilder()
					.addPoint(calibrationSpec.calibrationTime, 1.0, true)
					.build();
		}
		model = model.addCurves(calibrationCurve);
		objectsToCalibrate.add(calibrationCurve);

		return calibrationSpec.type;
	}

	/**
	 * Get discountCurve from the model (by name) or throw exception of no such discountCurve exists in the model. 
	 * Note that if given curve corresponds to a forwardCurve and doWrapCurves=true then a DiscountCurveFromForwardCurve is created from the forwardCurve.
	 * 
	 * @param discountCurveName The name of the discount curve to create.
	 * @return The name of the discount factor curve associated with the given name.
	 */
	private String createDiscountCurve(String discountCurveName) {
		CurveInterface curve = model.getCurve(discountCurveName); // note that this may be a forward curve
		if(curve == null)
			throw new IllegalArgumentException("Cannot create discountCurve " + discountCurveName + " as no such curve was found in the model (not even as a forward curve):\n" + model.toString());
			
		CurveInterface	discountCurve = null;
		if(DiscountCurveInterface.class.isInstance(curve)) {
			// they way it should be: given curve is discount curve
			discountCurve = curve;
		} else if(ForwardCurveInterface.class.isInstance(curve)) {
			if(doWrapCurves) {
				// check whether given curve is in fact a forwardCurve for which I have a discountCurveFromForwardCurve in the model
				String discountCurveFromForwardCurveName = discountCurveName+net.finmath.marketdata.model.curves.DiscountCurveFromForwardCurve.nameSuffix;
				discountCurve = model.getDiscountCurve(discountCurveFromForwardCurveName);
				if(discountCurve==null) {
					// otherwise create discountCurveFromForwardCurve
					discountCurve = new DiscountCurveFromForwardCurve(curve.getName());
					model = model.addCurve(discountCurve.getName(),discountCurve);
				}
			} else {
				throw new IllegalArgumentException("Cannot create discountCurve " + discountCurveName + " as this is a forward curve (and isAutomaticdoWrapCurvesallyWrapCurves=" + doWrapCurves + ")");
			}
		} else {
			throw new IllegalArgumentException("Unhandled type. Curve " + curve.getName() + " is neither discount nor forward curve");
		}
		
		return discountCurve.getName();
	}

	/**
	 * Get a forwardCurve from the model (by name) or throw exception of no such forwardCurve exists in the model. 
	 * Note that if given curve corresponds to a discountCurve and doWrapCurves=true then a ForwardCurveFromDiscountCurve is created from the discountCurve.
	 * 
	 * @param swapTenorDefinition The swap tenor associated with the forward curve.
	 * @param forwardCurveName The name of the forward curve to create.
	 * @return The forward curve associated with the given name.
	 */
	private String createForwardCurve(ScheduleInterface swapTenorDefinition, String forwardCurveName) {
		if(forwardCurveName == null || forwardCurveName.isEmpty()) 
			return null;
		
		CurveInterface curve = model.getCurve(forwardCurveName); // note that this may be a discount curve
		if(curve == null) 
			throw new IllegalArgumentException("Cannot create forwardCurve " + forwardCurveName + " as no such curve was found in the model (not even as a discount curve):\n" + model.toString());
		
		CurveInterface	forwardCurve = null;
		if(ForwardCurveInterface.class.isInstance(curve)) {
			// they way it should be: given curve is forward curve
			forwardCurve = curve;
		} else if(DiscountCurveInterface.class.isInstance(curve)) {
			if(doWrapCurves) {
				// check whether given curve is in fact a forwardCurve for which I have a discountCurveFromForwardCurve in the model
				String forwardCurveFromDiscountCurveName = forwardCurveName+net.finmath.marketdata.model.curves.ForwardCurveFromDiscountCurve.nameSuffix;
				forwardCurve = model.getForwardCurve(forwardCurveFromDiscountCurveName);
				if(forwardCurve==null) {
					// If the specified forward curve exits as a discount curve, we generate a forward curve by wrapping the discount curve, i.e. calculate the forwards from discount factors using the formula (df(T)/df(T+Delta T)-1) / dcf
					// Temporary "hack" - we try to infer index maturity codes from curve name
					// Note that this does not work for OIS curves
					String indexMaturityCode = null;
					if(forwardCurveName.contains("12M"))	indexMaturityCode = "12M";
					if(forwardCurveName.contains("9M"))		indexMaturityCode = "1M";
					if(forwardCurveName.contains("6M"))		indexMaturityCode = "6M";
					if(forwardCurveName.contains("3M"))		indexMaturityCode = "3M";
					
					// cannot impose this restriction because of OIS curves
					//if(indexMaturityCode!=null)
					//	throw new IllegalArgumentException("Cannot create forward curve form discount curve " + curve.getName() + " without knowing its indexMaturityCode.");
					
					// forwards from pseudo discount factors are always assumed to be ACT/360 and modified following based on the target calendar
					double daycountScaling = 365.0/360.0;
					BusinessdayCalendarInterface busDayCalendar = new BusinessdayCalendarExcludingTARGETHolidays();
					BusinessdayCalendarInterface.DateRollConvention dateRollConvention = BusinessdayCalendarInterface.DateRollConvention.MODIFIED_FOLLOWING;
					forwardCurve = new ForwardCurveFromDiscountCurve(curve.getName(), curve.getName(), swapTenorDefinition.getReferenceDate(), indexMaturityCode, 
							busDayCalendar, dateRollConvention, daycountScaling, 0.0);
					model = model.addCurve(forwardCurve.getName(),forwardCurve);
				} else {
					throw new IllegalArgumentException("Cannot create forwardCurve " + forwardCurveName + " as this is a discount curve (and doWrapCurves=" + doWrapCurves + ")");
				}
			}
		} else {
			throw new IllegalArgumentException("Unhandled type. Curve " + curve.getName() + " is neither discount nor forward curve");
		}
		
		return forwardCurve.getName();
	}
}
