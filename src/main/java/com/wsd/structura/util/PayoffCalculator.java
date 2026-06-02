package com.wsd.structura.util;

import com.wsd.structura.domain.ProductType;
import com.wsd.structura.domain.SimulationParameters;

public final class PayoffCalculator {

	private PayoffCalculator() {}

	/**
	 * Computes the absolute payoff (in currency units, on the invested principal)
	 * for the given product type at maturity, using the supplied price path.
	 * The path is expected to be daily (length = horizonYears * stepsPerYear + 1) with
	 * pricePath[0] = spot. For a single-point payoff curve, callers pass a 2-element
	 * path [spot, finalSpot].
	 */
	public static double computePayoff(ProductType type,
	                                   SimulationParameters p,
	                                   double[] pricePath) {
		final double spot = p.getSpotPrice();
		final double finalS = pricePath[pricePath.length - 1];
		final double invested = p.getInvestmentAmount();

		return switch (type) {
			case AUTOCALLABLE -> autocallPayoff(p, pricePath, spot, finalS, invested);
			case REVERSE_CONVERTIBLE -> reverseConvertiblePayoff(p, spot, finalS, invested);
			case CAPITAL_PROTECTED_NOTE -> capitalProtectedPayoff(p, spot, finalS, invested);
			case BARRIER_REVERSE_CONVERTIBLE -> barrierReverseConvertiblePayoff(p, pricePath, spot, finalS, invested);
		};
	}

	/** Returns true if at least one annual observation triggered an autocall. */
	public static boolean isAutocalled(SimulationParameters p, double[] pricePath) {
		final double trigger = p.getAutocallLevel() * p.getSpotPrice();
		final int obsStride = Math.max(1, p.getStepsPerYear());
		for (int i = obsStride; i < pricePath.length; i += obsStride) {
			if (pricePath[i] >= trigger) {
				return true;
			}
		}
		return false;
	}

	private static double autocallPayoff(SimulationParameters p, double[] pricePath,
	                                     double spot, double finalS, double invested) {
		final double trigger = p.getAutocallLevel() * spot;
		final int obsStride = Math.max(1, p.getStepsPerYear());
		// Check each annual observation for autocall
		for (int i = obsStride, year = 1; i < pricePath.length; i += obsStride, year++) {
			if (pricePath[i] >= trigger) {
				return invested * (1.0 + p.getCouponRate() * year);
			}
		}
		// No autocall: protected above barrier, principal-at-risk below
		if (finalS >= p.getBarrierLevel() * spot) {
			return invested;
		}
		return invested * (finalS / spot);
	}

	private static double reverseConvertiblePayoff(SimulationParameters p,
	                                               double spot, double finalS, double invested) {
		final double totalCoupon = invested * p.getCouponRate() * p.getHorizonYears();
		if (finalS >= p.getBarrierLevel() * spot) {
			return invested + totalCoupon;
		}
		return invested * (finalS / spot) + totalCoupon;
	}

	private static double capitalProtectedPayoff(SimulationParameters p,
	                                             double spot, double finalS, double invested) {
		final double upside = Math.max(0.0, (finalS / spot) - 1.0);
		return invested * (1.0 + p.getParticipationRate() * upside);
	}

	private static double barrierReverseConvertiblePayoff(SimulationParameters p, double[] pricePath,
	                                                      double spot, double finalS, double invested) {
		final double totalCoupon = invested * p.getCouponRate() * p.getHorizonYears();
		final double barrier = p.getBarrierLevel() * spot;
		boolean barrierTouched = false;
		for (double price : pricePath) {
			if (price < barrier) {
				barrierTouched = true;
				break;
			}
		}
		if (!barrierTouched || finalS >= spot) {
			return invested + totalCoupon;
		}
		return invested * (finalS / spot) + totalCoupon;
	}
}
