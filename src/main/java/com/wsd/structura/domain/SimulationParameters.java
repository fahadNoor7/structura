package com.wsd.structura.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationParameters {

	private double spotPrice;
	private double volatility;
	private double drift;
	private double barrierLevel;
	private double couponRate;
	private double autocallLevel;
	private double participationRate;
	private int horizonYears;
	private int numPaths;
	private int stepsPerYear;
	private double investmentAmount;
}
