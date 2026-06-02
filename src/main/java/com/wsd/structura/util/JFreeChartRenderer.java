package com.wsd.structura.util;

import com.wsd.structura.domain.SimulationResult;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class JFreeChartRenderer {

	private static final int WIDTH = 800;
	private static final int HEIGHT = 450;
	private static final Color NAVY = new Color(11, 37, 69);
	private static final Color ACCENT = new Color(212, 175, 55);

	public byte[] payoffCurveImage(SimulationResult result) {
		XYSeries series = new XYSeries("Payoff");
		double[] xs = result.getPayoffCurveX();
		double[] ys = result.getPayoffCurveY();
		for (int i = 0; i < xs.length; i++) {
			series.add(xs[i], ys[i]);
		}
		XYSeriesCollection dataset = new XYSeriesCollection(series);

		JFreeChart chart = ChartFactory.createXYLineChart(
				"Payoff at Maturity vs Final Underlying Price",
				"Underlying change (% of initial spot)",
				"Payoff (currency units)",
				dataset,
				PlotOrientation.VERTICAL,
				false, false, false);

		styleXYPlot(chart, NAVY);
		return writePng(chart);
	}

	public byte[] histogramImage(SimulationResult result) {
		HistogramDataset dataset = new HistogramDataset();
		double[] returns = result.getReturnDistribution();
		if (returns != null && returns.length > 0) {
			dataset.addSeries("Final Return", returns, 50);
		}

		JFreeChart chart = ChartFactory.createHistogram(
				"Distribution of Simulated Final Returns",
				"Return",
				"Frequency",
				dataset,
				PlotOrientation.VERTICAL,
				false, false, false);

		styleXYPlot(chart, ACCENT);
		XYPlot plot = chart.getXYPlot();
		if (plot.getRenderer() instanceof XYBarRenderer bar) {
			bar.setBarPainter(new org.jfree.chart.renderer.xy.StandardXYBarPainter());
			bar.setShadowVisible(false);
		}
		return writePng(chart);
	}

	private void styleXYPlot(JFreeChart chart, Color seriesColor) {
		chart.setBackgroundPaint(Color.WHITE);
		XYPlot plot = chart.getXYPlot();
		plot.setBackgroundPaint(Color.WHITE);
		plot.setRangeGridlinePaint(new Color(220, 220, 220));
		plot.setDomainGridlinePaint(new Color(220, 220, 220));
		plot.getRenderer().setSeriesPaint(0, seriesColor);
	}

	private byte[] writePng(JFreeChart chart) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ChartUtils.writeChartAsPNG(out, chart, WIDTH, HEIGHT);
			return out.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to render chart PNG", e);
		}
	}
}
