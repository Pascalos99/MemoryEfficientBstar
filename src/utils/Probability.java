package utils;

import java.util.Arrays;

public class Probability {
	
	public static double greaterOrEqual(double[] survival, int lowerbound, int x) {
		int index = x - lowerbound;
		if (index < 0) return 1.;
		if (index >= survival.length) return 0.;
		return survival[index];
	}
	
	public static double greaterOrEqualFromCDF(double[] CDF, int lowerbound, int x) {
		int index = x - lowerbound;
		if (index <= 0) return 1.;
		if (index >= CDF.length + 1) return 0.;
		return 1. - CDF[index-1];
	}
	
	public static double lesserOrEqual(double[] CDF, int lowerbound, int x) {
		int index = x - lowerbound;
		if (index < 0) return 0.;
		if (index >= CDF.length) return 1.;
		return CDF[index];
	}
	
	public static double lesserOrEqualFromSurvival(double[] survival, int lowerbound, int x) {
		int index = x - lowerbound;
		if (index < -1) return 0.;
		if (index >= survival.length - 1) return 1.;
		return 1. - survival[index+1];
	}
	
	public static double equalTo(double[] pmf, int lowerbound, int x) {
		int index = x - lowerbound;
		if (index < 0 || index  >= pmf.length) return 0.0;
		return pmf[index];
	}
	
	public static double equalToFromCDF(double[] CDF, int lowerbound, int x) {
		int index = x - lowerbound;
		if (index < 0 || index >= CDF.length) return 0.0;
		if (index == 0) return CDF[0];
		return CDF[index] - CDF[index - 1];
	}
	
	public static double equalToFromSurvival(double[] survival, int lowerbound, int x) {
		int index = x - lowerbound;
		if (index < 0 || index >= survival.length) return 0.0;
		if (index == survival.length - 1) return survival[index];
		return survival[index] - survival[index + 1];
	}
	
	/**
	 * Converts a CDF P(X ≤ t) to the respective distribution's Survival Function P(X ≥ t).
	 * <p>
	 * Assumes that the given array spans the entire domain of the distribution.
	 * @param cdf the Cumulative Distribution Function (CDF)
	 * @return the Survival Function
	 */
	public static double[] toSurvival(double[] cdf) {
		double[] survival = new double[cdf.length];
		survival[0] = 1.;
		for (int i = 1; i < cdf.length; i++) {
			survival[i] = 1. - cdf[i-1];
		}
		return survival;
	}
	
	/**
	 * Converts a Survival function P(X ≥ t) to the respective distribution's CDF P(X ≤ t).
	 * <p>
	 * Assumes that the given array spans the entire domain of the distribution.
	 * @param survival the Survival Function
	 * @return the Cumulative Distribution Function (CDF)
	 */
	public static double[] toCDF(double[] survival) {
		double[] cdf = new double[survival.length];
		cdf[survival.length - 1] = 1.;
		for (int i=0; i < cdf.length - 1; i++)
			cdf[i] = 1. - survival[i+1];
		return cdf;
	}
	
	/**
	 * Converts a CDF P(X ≤ t) to the respective distribution's PMF P(X = t).
	 * <p>
	 * Assumes that the given array spans the entire domain of the distribution.
	 * @param CDF the Cumulative Distribution Function (CDF)
	 * @return the Probability Mass Function (PMF)
	 */
	public static double[] cdfToPMF(double[] CDF) {
		double[] PMF = new double[CDF.length];
		PMF[0] = CDF[0]; // P(X = min) == P(X ≤ min)
		for (int i=1; i < PMF.length; i++)
			PMF[i] = CDF[i] - CDF[i - 1];
		return PMF;
	}
	
	/**
	 * Converts a Survival function P(X ≥ t) to the respective distribution's PMF P(X = t).
	 * <p>
	 * Assumes that the given array spans the entire domain of the distribution.
	 * @param survival the Survival Function
	 * @return the Probability Mass Function (PMF)
	 */
	public static double[] survivalToPMF(double[] survival) {
		double[] PMF = new double[survival.length];
		for (int i=0; i < PMF.length-1; i++)
			PMF[i] = survival[i+1] - survival[i];
		PMF[PMF.length-1] = survival[PMF.length-1]; // P(X = max) == P(X ≥ max)
		return PMF;
	}
	
	/**
	 * Converts a CDF/Survival Function to PMF based on the {@code cumulative} flag.
	 * @param distribution The distribution (CDF if {@code cumulative = true}, Survival Function if {@code cumulative = false})
	 * @param cumulative Whether the input is cumulative ({@code true}: CDF) or subtractive ({@code false}: Survival Function).
	 * @return the Probability Mass Function (PMF)
	 */
	public static double[] toPMF(double[] distribution, boolean cumulative) {
		return cumulative ? cdfToPMF(distribution) : survivalToPMF(distribution);
	}
	
	/**
	 * @param lower inclusive lower bound of the interval
	 * @param upper inclusive upper bound of the interval
	 * @param cumulative whether to return the CDF of the interval ({@code cumulative = true}) or the Survival Function of the interval ({@code cumulative = false}).
	 * @return the discrete CDF or Survival Function of a uniform distribution spanning the given lower and upper bounds
	 */
	public static double[] discreteUniform(int lower, int upper, boolean cumulative) {
		int range = upper - lower + 1;
		double[] res = new double[range];
		for (int i=0; i < range; i++) {
			if (cumulative)
				res[i] = (i+1.) / range;
			else
				res[i] = ((double)range-i) / range;
		}
		return res;
	}
	
	/**
	 * Computes the distribution resulting from taking the maximum or minimum of multiple random variables.
	 * 
	 * @param CDFs The CDFs or Survival Functions of the distributions to be combined
	 * @param lowerbounds lower bounds of each of the distributions provided. The upper bound is encoded in the lengths of the arrays.
	 * @param lower lower bound of the resulting function (maximum lowerbound of the lowerbounds of the CDFs)
	 * @param upper upper bound of the resulting function (maximum upperbound of the upperbounds of the CDFs)
	 * @param cumulative {@code true} if the given functions are cumulative (CDF) and {@code false} if the 
	 *           given functions are subtractive (Survival Function).
	 * @return The combined CDF or Survival Function of the given distributions, representing the distribution of the 
	 * maximum ({@code cumulative = true}) or minimum ({@code cumulative = false}) of the random variables given by 
	 * {@code CDFs} and {@code lowerbounds}
	 */
	public static double[] minOrMaxDistribution(double[][] CDFs, int[] lowerbounds, int lower, int upper, boolean cumulative) {
		double[] CDF = new double[upper - lower + 1];
		Arrays.fill(CDF, 1.);
		
		for (int i=0; i < CDFs.length; i++) {
			for (int j=0; j < CDF.length; j++) {
				int x = lower + j;
				int CDF_index = x - lowerbounds[i];
				double CDFval;
	            if (CDF_index < 0) {
	            	// P(X_i ≤ m) = 0 (max) or P(X_i ≥ m) = 1 (min)
	                CDFval = cumulative ? 0.0 : 1.0; 
	            } else if (CDF_index >= CDFs[i].length) {
	            	// P(X_i ≤ m) = 1 (max) or P(X_i ≥ m) = 0 (min)
	                CDFval = cumulative ? 1.0 : 0.0; 
	            } else {
	                CDFval = CDFs[i][CDF_index];
	            }
	            CDF[j] *= CDFval;
			}
		}
		return CDF;
	}
	
}
