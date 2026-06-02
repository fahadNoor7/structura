package com.wsd.structura.simulation;

import com.wsd.structura.domain.ClientProfile;
import com.wsd.structura.domain.ProductType;
import com.wsd.structura.domain.SimulationParameters;
import com.wsd.structura.domain.SimulationResult;
import com.wsd.structura.domain.StructuredProduct;
import com.wsd.structura.util.PayoffCalculator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MonteCarloEngine {

	private static final int DELTA_BATCH_PATHS = 1_000;
	private static final int CURVE_POINTS = 51;
	private static final double CURVE_MIN_PCT = -0.5;
	private static final double CURVE_MAX_PCT = 1.0;

	public SimulationResult run(ClientProfile profile,
	                            StructuredProduct product,
	                            SimulationParameters params) {
		final ProductType type = product.getType();
		final int numPaths = params.getNumPaths();

		double[] payoffs = simulatePayoffs(type, params, numPaths);
		double[] payoffsBumped = simulatePayoffs(type, withBumpedSpot(params, 1.01), DELTA_BATCH_PATHS);

		double invested = params.getInvestmentAmount();
		Aggregates agg = aggregate(payoffs, invested);
		double delta = (mean(payoffsBumped) - mean(takeFirst(payoffs, DELTA_BATCH_PATHS)))
				/ (0.01 * params.getSpotPrice());

		double[] curveX = new double[CURVE_POINTS];
		double[] curveY = new double[CURVE_POINTS];
		buildPayoffCurve(type, params, curveX, curveY);

		double autocallProb = (type == ProductType.AUTOCALLABLE)
				? estimateAutocallProbability(params, numPaths)
				: 0.0;

		return SimulationResult.builder()
				.expectedReturn(agg.expectedReturn)
				.successProbability(agg.successProbability)
				.maxLoss(agg.maxLoss)
				.var95(agg.var95)
				.medianPayoff(agg.medianPayoff)
				.autocallProbability(autocallProb)
				.deltaApproximation(delta)
				.payoffCurveX(curveX)
				.payoffCurveY(curveY)
				.returnDistribution(agg.returns)
				.build();
	}

	private double[] simulatePayoffs(ProductType type, SimulationParameters params, int n) {
		final int steps = params.getHorizonYears() * params.getStepsPerYear();
		final double dt = 1.0 / params.getStepsPerYear();
		final double[] payoffs = new double[n];

		List<Callable<Void>> tasks = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			final int idx = i;
			tasks.add(() -> {
				double[] path = generatePath(params.getSpotPrice(),
						params.getDrift(), params.getVolatility(), steps, dt);
				payoffs[idx] = PayoffCalculator.computePayoff(type, params, path);
				return null;
			});
		}

		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<Void>> futures = executor.invokeAll(tasks);
			for (Future<Void> f : futures) {
				f.get();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Simulation interrupted", e);
		} catch (ExecutionException e) {
			throw new IllegalStateException("Simulation worker failed", e.getCause());
		}
		return payoffs;
	}

	private double estimateAutocallProbability(SimulationParameters params, int n) {
		final int steps = params.getHorizonYears() * params.getStepsPerYear();
		final double dt = 1.0 / params.getStepsPerYear();
		final boolean[] called = new boolean[n];

		List<Callable<Void>> tasks = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			final int idx = i;
			tasks.add(() -> {
				double[] path = generatePath(params.getSpotPrice(),
						params.getDrift(), params.getVolatility(), steps, dt);
				called[idx] = PayoffCalculator.isAutocalled(params, path);
				return null;
			});
		}
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			executor.invokeAll(tasks).forEach(f -> {
				try { f.get(); } catch (Exception ignored) { /* propagated elsewhere */ }
			});
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		int count = 0;
		for (boolean b : called) {
			if (b) count++;
		}
		return (double) count / n;
	}

	private static double[] generatePath(double spot, double drift, double vol, int steps, double dt) {
		double[] path = new double[steps + 1];
		path[0] = spot;
		double driftAdj = (drift - 0.5 * vol * vol) * dt;
		double volSqrt = vol * Math.sqrt(dt);
		ThreadLocalRandom rng = ThreadLocalRandom.current();
		for (int i = 1; i <= steps; i++) {
			path[i] = path[i - 1] * Math.exp(driftAdj + volSqrt * rng.nextGaussian());
		}
		return path;
	}

	private void buildPayoffCurve(ProductType type, SimulationParameters params,
	                              double[] xs, double[] ys) {
		double spot = params.getSpotPrice();
		for (int i = 0; i < CURVE_POINTS; i++) {
			double pct = CURVE_MIN_PCT + (CURVE_MAX_PCT - CURVE_MIN_PCT) * i / (CURVE_POINTS - 1);
			double finalSpot = spot * (1.0 + pct);
			xs[i] = pct * 100.0;
			ys[i] = PayoffCalculator.computePayoff(type, params, new double[]{spot, finalSpot});
		}
	}

	private static SimulationParameters withBumpedSpot(SimulationParameters base, double factor) {
		return SimulationParameters.builder()
				.spotPrice(base.getSpotPrice() * factor)
				.volatility(base.getVolatility())
				.drift(base.getDrift())
				.barrierLevel(base.getBarrierLevel())
				.couponRate(base.getCouponRate())
				.autocallLevel(base.getAutocallLevel())
				.participationRate(base.getParticipationRate())
				.horizonYears(base.getHorizonYears())
				.numPaths(base.getNumPaths())
				.stepsPerYear(base.getStepsPerYear())
				.investmentAmount(base.getInvestmentAmount())
				.build();
	}

	private static Aggregates aggregate(double[] payoffs, double invested) {
		int n = payoffs.length;
		double[] returns = new double[n];
		double sum = 0.0;
		double min = Double.POSITIVE_INFINITY;
		int successes = 0;
		for (int i = 0; i < n; i++) {
			returns[i] = (payoffs[i] - invested) / invested;
			sum += payoffs[i];
			if (payoffs[i] < min) min = payoffs[i];
			if (payoffs[i] > invested) successes++;
		}
		double mean = sum / n;
		double[] sorted = payoffs.clone();
		Arrays.sort(sorted);
		double medianPayoff = (n % 2 == 0)
				? (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
				: sorted[n / 2];
		// VaR 95: 5th percentile of returns
		double[] sortedReturns = returns.clone();
		Arrays.sort(sortedReturns);
		double var95 = sortedReturns[Math.max(0, (int) Math.floor(0.05 * n))];

		Aggregates a = new Aggregates();
		a.expectedReturn = (mean - invested) / invested;
		a.successProbability = (double) successes / n;
		a.maxLoss = (min - invested) / invested;
		a.var95 = var95;
		a.medianPayoff = medianPayoff;
		a.returns = returns;
		return a;
	}

	private static double mean(double[] xs) {
		double s = 0;
		for (double x : xs) s += x;
		return s / xs.length;
	}

	private static double[] takeFirst(double[] xs, int n) {
		return Arrays.copyOf(xs, Math.min(n, xs.length));
	}

	private static final class Aggregates {
		double expectedReturn;
		double successProbability;
		double maxLoss;
		double var95;
		double medianPayoff;
		double[] returns;
	}
}
