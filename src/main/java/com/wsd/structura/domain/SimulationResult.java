package com.wsd.structura.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationResult {

	private double expectedReturn;
	private double successProbability;
	private double maxLoss;
	private double var95;
	private double medianPayoff;
	private double autocallProbability;
	private double deltaApproximation;

	private double[] payoffCurveX;
	private double[] payoffCurveY;
	private double[] returnDistribution;
}
