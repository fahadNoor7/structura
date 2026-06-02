package com.wsd.structura.util;

import com.wsd.structura.domain.ClientProfile;
import com.wsd.structura.domain.MarketView;
import com.wsd.structura.domain.ProductType;
import com.wsd.structura.domain.RiskLevel;
import com.wsd.structura.domain.SimulationParameters;
import org.springframework.stereotype.Component;

@Component
public class SimulationParameterFactory {

	private static final double SPOT_PRICE = 100.0;
	private static final int NUM_PATHS = 10_000;
	private static final int STEPS_PER_YEAR = 252;

	public SimulationParameters build(ClientProfile profile, ProductType type) {
		return SimulationParameters.builder()
				.spotPrice(SPOT_PRICE)
				.volatility(volatilityFor(profile.getRiskLevel()))
				.drift(driftFor(profile.getMarketView()))
				.barrierLevel(barrierFor(type))
				.couponRate(couponFor(type, profile.getRiskLevel()))
				.autocallLevel(1.05)
				.participationRate(participationFor(type))
				.horizonYears(profile.getHorizonYears())
				.numPaths(NUM_PATHS)
				.stepsPerYear(STEPS_PER_YEAR)
				.investmentAmount(profile.getInvestmentAmount())
				.build();
	}

	private static double volatilityFor(RiskLevel risk) {
		return switch (risk) {
			case LOW -> 0.15;
			case MEDIUM -> 0.25;
			case HIGH -> 0.40;
		};
	}

	private static double driftFor(MarketView view) {
		return switch (view) {
			case BULLISH -> 0.12;
			case NEUTRAL -> 0.05;
			case BEARISH -> -0.05;
		};
	}

	private static double barrierFor(ProductType type) {
		return switch (type) {
			case AUTOCALLABLE -> 0.70;
			case REVERSE_CONVERTIBLE -> 0.80;
			case CAPITAL_PROTECTED_NOTE -> 1.00;
			case BARRIER_REVERSE_CONVERTIBLE -> 0.70;
		};
	}

	private static double couponFor(ProductType type, RiskLevel risk) {
		double base = switch (type) {
			case AUTOCALLABLE, BARRIER_REVERSE_CONVERTIBLE -> 0.08;
			case REVERSE_CONVERTIBLE -> 0.06;
			case CAPITAL_PROTECTED_NOTE -> 0.0;
		};
		return base * switch (risk) {
			case LOW -> 0.8;
			case MEDIUM -> 1.0;
			case HIGH -> 1.3;
		};
	}

	private static double participationFor(ProductType type) {
		return type == ProductType.CAPITAL_PROTECTED_NOTE ? 0.80 : 1.00;
	}
}
