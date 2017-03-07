		package net.finmath.marketdata.model.volatilities;
		
		import net.finmath.functions.AnalyticFormulas;
		import net.finmath.marketdata.model.AnalyticModelInterface;
		import net.finmath.marketdata.products.Swaption;
		import net.finmath.optimizer.LevenbergMarquardt;
		import net.finmath.optimizer.SolverException;
		
		public class SwaptionMarketDataWithSabrInterpolation extends SwaptionMarketData2 {
		
			private final double[][] sabrShiftMatrix;
			private double[][] alphaMatrix;
			private double[][] betaMatrix;
			private double[][] rhoMatrix;
			private double[][] nuMatrix;
			private boolean isCalibrated = false;
		
			public SwaptionMarketDataWithSabrInterpolation(
					String swaptionSurfaceName,
					Swaption[] atmSwaptionVector,
					double[][] quotingShiftMatrix,
					QuotingConvention quotingConvention, 
					InterpolationMethod interpolationMethod, 
					String optionDiscountCurveName,
					AnalyticModelInterface model,
					double[][] impliedAtmVolatilityMatrix) 
			{
				super(swaptionSurfaceName, atmSwaptionVector, quotingShiftMatrix, quotingConvention, interpolationMethod, optionDiscountCurveName, model, new double[] {0.0}, impliedAtmVolatilityMatrix);
				this.sabrShiftMatrix = quotingShiftMatrix;
			}
			
			public SwaptionMarketDataWithSabrInterpolation(
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
				super(swaptionSurfaceName, atmSwaptionVector, quotingShiftMatrix, quotingConvention, interpolationMethod, optionDiscountCurveName, model, inputStrikeOffsets, impliedVolatilityMatricesAndOffsets);
				this.sabrShiftMatrix = quotingShiftMatrix;
				try {
					calibrate(model);
				} catch(SolverException exception) {
					throw new IllegalArgumentException("Calibration produces following exception: " + exception.getMessage());
				}
			}
			
			private void calibrate(AnalyticModelInterface model) throws SolverException
			{
				alphaMatrix = new double[getOptionMaturities().length][getSwapLengths().length];
				betaMatrix = new double[getOptionMaturities().length][getSwapLengths().length];
				nuMatrix = new double[getOptionMaturities().length][getSwapLengths().length];
				rhoMatrix = new double[getOptionMaturities().length][getSwapLengths().length];
				
				for(int iMaturity=0; iMaturity<getOptionMaturities().length; iMaturity++) 
				{
					double optionMaturity = this.getOptionMaturities()[iMaturity];
					for(int iTenor=0; iTenor<getSwapLengths().length; iTenor++)
					{
						System.out.println(optionMaturities.getTime(iMaturity) + "x" + swapLengths.getTime(iTenor) + ": ");
						double[] targetNormalVolatilities = new double[strikeOffsets.getNumberOfTimes()];
						for(int iOffset=0; iOffset<strikeOffsets.getNumberOfTimes(); iOffset++)
							targetNormalVolatilities[iOffset] = impliedNormalVolatilityCube[iOffset][iMaturity][iTenor];
						//System.out.println("\ntargetNormalVolatilities=" + Arrays.toString(targetNormalVolatilities));
						
						double forwardSwapRate = forwardSwapRates[iMaturity][iTenor];
						double sabrShift = sabrShiftMatrix[iMaturity][iTenor];
						int maxIteration = 500;
						int numberOfThreads = 8;
						double[] initialParameters = {0, 0, -100}; // beta, rho, nu
						//double beta = 0.2;
						LevenbergMarquardt optimizer = new LevenbergMarquardt(initialParameters, targetNormalVolatilities, maxIteration, numberOfThreads) 
						{			
							@Override
							public void setValues(double[] parameters, double[] values) throws SolverException 
							{
								double[] transformedParameters = new double[parameters.length];
								//transformedParameters[0] = Math.exp(parameters[0]); // alpha is positive
								transformedParameters[0] = 1/(1+Math.exp(-parameters[0])); // beta in [0,1]
								transformedParameters[1] = 2/(1+Math.exp(-parameters[1]))-1; // rho in in [-1,1]
								transformedParameters[2] = Math.exp(parameters[2]); // nu is positive
								double alpha = AnalyticFormulas.sabrAlphaApproximation(targetNormalVolatilities[strikeOffsets.getTimeIndex(0)], transformedParameters[0], transformedParameters[1], transformedParameters[2], sabrShift, forwardSwapRate, optionMaturity);
								//System.out.println("atmVol=" + targetNormalVolatilities[strikeOffsets.getTimeIndex(0)] + ", alpha=" + alpha + ", beta=" + transformedParameters[0] + ", rho=" + transformedParameters[1] + ", nu=" + transformedParameters[2]);
								for(int iOffset=0; iOffset<strikeOffsets.getNumberOfTimes(); iOffset++)
									values[iOffset] = AnalyticFormulas.sabrBerestyckiNormalVolatilityApproximation(alpha, transformedParameters[0], transformedParameters[1], transformedParameters[2], sabrShift, forwardSwapRate, forwardSwapRate+strikeOffsets.getTime(iOffset), optionMaturity);
								//System.out.println("values=" + Arrays.toString(values));
							}
						};
						optimizer.setErrorTolerance(1E-16);
						
						optimizer.run();
						
						//double error = optimizer.getRootMeanSquaredError();
						double[] bestParameters = optimizer.getBestFitParameters();
						double beta = 1/(1+Math.exp(-bestParameters[0])); // beta in [0,1]
						double rho = 2/(1+Math.exp(-bestParameters[1]))-1; // rho in in [-1,1]
						double nu = Math.exp(bestParameters[2]); // nu is positive
						betaMatrix[iMaturity][iTenor] = beta;
						rhoMatrix[iMaturity][iTenor] = rho;
						nuMatrix[iMaturity][iTenor] = nu;
						double alpha = AnalyticFormulas.sabrAlphaApproximation(targetNormalVolatilities[strikeOffsets.getTimeIndex(0)], beta, rho, nu, sabrShift, forwardSwapRate, optionMaturity);
						alphaMatrix[iMaturity][iTenor] = alpha;
						double[] solvedNormalVols = new double[strikeOffsets.getNumberOfTimes()];
						for(int iOffset=0; iOffset<strikeOffsets.getNumberOfTimes(); iOffset++)
							solvedNormalVols[iOffset] = AnalyticFormulas.sabrBerestyckiNormalVolatilityApproximation(alpha, beta, rho, nu, sabrShift, forwardSwapRate, forwardSwapRate+strikeOffsets.getTime(iOffset), optionMaturity);
					}
				}
				isCalibrated = true;
			}
			
			@Override
		    public double getNormalVolatility(double optionMaturity, double swapLength, double strikeOffset) 
			{
				if(!isCalibrated)
					throw new IllegalArgumentException("SABR container not calibrated");
					
				if(interpolationMethod!=InterpolationMethod.SABR)
					throw new IllegalArgumentException("interpolationMethod " + interpolationMethod + " (!= SABR) not allowed for this class");
				
				int iMaturity = optionMaturities.getTimeIndex(optionMaturity);
				int iTenor = swapLengths.getTimeIndex(swapLength);
				if(iMaturity<0 || iTenor<0)		
					throw new IllegalArgumentException("Requested (optionMaturity,swapLength) tuplet (" + optionMaturity + "," + swapLength + ") not part of data");
				double forwardSwapRate = forwardSwapRates[iMaturity][iTenor]; //AnalyticFormulas.getBilinearInterpolation(optionMaturity, swapLength, optionMaturities, swapLengths, forwardSwapRates);
				double alpha = alphaMatrix[iMaturity][iTenor]; //AnalyticFormulas.getBilinearInterpolation(optionMaturity, swapLength, optionMaturities, swapLengths, alphaMatrix);
				double beta = betaMatrix[iMaturity][iTenor]; //AnalyticFormulas.getBilinearInterpolation(optionMaturity, swapLength, optionMaturities, swapLengths, betaMatrix);
				double rho = rhoMatrix[iMaturity][iTenor]; //AnalyticFormulas.getBilinearInterpolation(optionMaturity, swapLength, optionMaturities, swapLengths, rhoMatrix);
				double nu = nuMatrix[iMaturity][iTenor]; //AnalyticFormulas.getBilinearInterpolation(optionMaturity, swapLength, optionMaturities, swapLengths, nuMatrix);
				
				double normalVolatility = AnalyticFormulas.sabrBerestyckiNormalVolatilityApproximation(alpha, beta, rho, nu, sabrShiftMatrix[iMaturity][iTenor], forwardSwapRate, forwardSwapRate+strikeOffset, optionMaturity);
				return normalVolatility;
			}
			
			public double getSabrParameter(String type, double optionMaturity, double swapLength) 
			{
				int iMaturity = optionMaturities.getTimeIndex(optionMaturity);
				int iTenor = swapLengths.getTimeIndex(swapLength);
				if(iMaturity<0 || iTenor<0)		
					throw new IllegalArgumentException("Requested (optionMaturity,swapLength) tuplet (" + optionMaturity + "," + swapLength + ") not part of data");
				
				if(type.toLowerCase().equalsIgnoreCase("alpha"))
					return alphaMatrix[iMaturity][iTenor];
				else if(type.toLowerCase().equalsIgnoreCase("beta"))
					return betaMatrix[iMaturity][iTenor];
				else if(type.toLowerCase().equalsIgnoreCase("rho"))
					return rhoMatrix[iMaturity][iTenor];
				else if(type.toLowerCase().equalsIgnoreCase("nu"))
					return nuMatrix[iMaturity][iTenor];
				else
					throw new IllegalArgumentException("type " + type + " not allowed (must be alpha, beta, rho or nu)");
			}
		}
