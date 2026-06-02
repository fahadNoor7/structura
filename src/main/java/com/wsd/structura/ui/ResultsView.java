package com.wsd.structura.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.wsd.structura.ai.AIService;
import com.wsd.structura.domain.ClientProfile;
import com.wsd.structura.domain.ProductType;
import com.wsd.structura.domain.SimulationParameters;
import com.wsd.structura.domain.SimulationResult;
import com.wsd.structura.domain.StructuredProduct;
import com.wsd.structura.pdf.PdfReportService;
import com.wsd.structura.simulation.MonteCarloEngine;
import com.wsd.structura.util.SimulationParameterFactory;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Route(value = "results", layout = MainLayout.class)
@PageTitle("Structura — Results")
public class ResultsView extends VerticalLayout implements BeforeEnterObserver {

	private static final int HIST_BINS = 40;

	private final WizardState state;
	private final MonteCarloEngine engine;
	private final AIService aiService;
	private final SimulationParameterFactory paramFactory;
	private final PdfReportService pdfService;
	private final Executor virtualThreadExecutor;
	private final ObjectMapper mapper;

	private final TabSheet tabs = new TabSheet();
	private final Tab suggestionsTab = new Tab("AI Suggestions");
	private final Tab simulatorTab = new Tab("Simulator");
	private final Tab chartsTab = new Tab("Charts");
	private final Tab reportTab = new Tab("Report");

	private final VerticalLayout suggestionsPanel = new VerticalLayout();
	private final VerticalLayout simulatorPanel = new VerticalLayout();
	private final VerticalLayout chartsPanel = new VerticalLayout();
	private final VerticalLayout reportPanel = new VerticalLayout();

	public ResultsView(WizardState state,
	                   MonteCarloEngine engine,
	                   AIService aiService,
	                   SimulationParameterFactory paramFactory,
	                   PdfReportService pdfService,
	                   ObjectMapper mapper,
	                   @Qualifier("virtualThreadExecutor") Executor virtualThreadExecutor) {
		this.state = state;
		this.engine = engine;
		this.aiService = aiService;
		this.paramFactory = paramFactory;
		this.pdfService = pdfService;
		this.virtualThreadExecutor = virtualThreadExecutor;
		this.mapper = mapper;

		setSizeFull();
		setPadding(true);
		setSpacing(true);

		H1 title = new H1("Results");
		add(title);

		tabs.add(suggestionsTab, suggestionsPanel);
		tabs.add(simulatorTab, simulatorPanel);
		tabs.add(chartsTab, chartsPanel);
		tabs.add(reportTab, reportPanel);
		tabs.setWidthFull();
		add(tabs);
		add(buildDisclaimer());
	}

	@Override
	public void beforeEnter(BeforeEnterEvent event) {
		if (!state.hasSuggestions()) {
			Notification.show("Generate suggestions first", 3000, Notification.Position.MIDDLE);
			event.forwardTo(ClientInputView.class);
			return;
		}
		renderSuggestions();
		renderSimulator();
		renderCharts();
		renderReport();
	}

	// ---------- Tab 1: Suggestions ----------

	private void renderSuggestions() {
		suggestionsPanel.removeAll();
		suggestionsPanel.setPadding(false);

		Paragraph intro = new Paragraph(
				"AI-generated product ideas tailored to the client profile. "
						+ "Click \"Select & Simulate\" to run a Monte Carlo analysis on a product.");
		suggestionsPanel.add(intro);

		HorizontalLayout cards = new HorizontalLayout();
		cards.setWidthFull();
		cards.getStyle().set("flex-wrap", "wrap");

		for (StructuredProduct product : state.getSuggestions()) {
			cards.add(buildProductCard(product));
		}
		suggestionsPanel.add(cards);
	}

	private VerticalLayout buildProductCard(StructuredProduct product) {
		VerticalLayout card = new VerticalLayout();
		card.addClassName("product-card");
		card.setWidth("330px");
		card.setSpacing(false);
		card.setPadding(false);

		Span name = new Span(product.getName());
		name.addClassName("product-name");

		Span type = new Span(product.getType().name());
		type.getElement().getThemeList().add("badge");

		Paragraph desc = new Paragraph(product.getDescription());
		desc.getStyle().set("font-size", "var(--lumo-font-size-s)");

		Paragraph payoff = new Paragraph("Payoff: " + product.getPayoffLogic());
		payoff.getStyle().set("font-size", "var(--lumo-font-size-xs)");
		payoff.getStyle().set("color", "var(--lumo-secondary-text-color)");

		Div pros = chipRow("Pros", product.getPros(),
				"var(--lumo-success-color-10pct)", "var(--lumo-success-text-color)");
		Div cons = chipRow("Cons", product.getCons(),
				"var(--lumo-error-color-10pct)", "var(--lumo-error-text-color)");

		Button select = new Button("Select & Simulate", e -> selectProduct(product));
		select.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		select.setWidthFull();

		card.add(name, type, desc, payoff, pros, cons, select);
		return card;
	}

	private Div chipRow(String label, List<String> items, String bg, String color) {
		Div wrapper = new Div();
		wrapper.getStyle().set("margin-top", "var(--lumo-space-s)");
		Span heading = new Span(label + ": ");
		heading.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-xs)");
		wrapper.add(heading);
		if (items != null) {
			for (String item : items) {
				Span chip = new Span(item);
				chip.getStyle()
						.set("display", "inline-block")
						.set("padding", "2px 8px")
						.set("margin", "2px")
						.set("border-radius", "12px")
						.set("font-size", "var(--lumo-font-size-xs)")
						.set("background-color", bg)
						.set("color", color);
				wrapper.add(chip);
			}
		}
		return wrapper;
	}

	private void selectProduct(StructuredProduct product) {
		state.setSelectedProduct(product);
		state.setSimulationParameters(paramFactory.build(state.getProfile(), product.getType()));
		state.setSimulationResult(null);
		state.setAiExplanation(null);
		renderSimulator();
		renderCharts();
		renderReport();
		tabs.setSelectedTab(simulatorTab);
		Notification.show("Selected: " + product.getName(), 2000, Notification.Position.BOTTOM_START);
	}

	// ---------- Tab 2: Simulator ----------

	private void renderSimulator() {
		simulatorPanel.removeAll();
		simulatorPanel.setPadding(false);

		if (!state.hasSelectedProduct()) {
			simulatorPanel.add(new Paragraph("Pick a product from the Suggestions tab to run a simulation."));
			return;
		}

		StructuredProduct product = state.getSelectedProduct();
		SimulationParameters params = state.getSimulationParameters();

		simulatorPanel.add(new H3(product.getName() + " — " + product.getType()));

		FormLayout summary = new FormLayout();
		summary.setResponsiveSteps(
				new FormLayout.ResponsiveStep("0", 1),
				new FormLayout.ResponsiveStep("600px", 3));
		summary.addFormItem(textTile(String.format("$%,.0f", params.getInvestmentAmount())), "Investment");
		summary.addFormItem(textTile(String.format("%.0f%%", params.getVolatility() * 100)), "Volatility (σ)");
		summary.addFormItem(textTile(String.format("%.0f%%", params.getDrift() * 100)), "Drift (μ)");
		summary.addFormItem(textTile(String.format("%.0f%%", params.getBarrierLevel() * 100)), "Barrier");
		summary.addFormItem(textTile(String.format("%.0f%%", params.getCouponRate() * 100)), "Coupon");
		summary.addFormItem(textTile(params.getHorizonYears() + " yr"), "Horizon");
		simulatorPanel.add(summary);

		ProgressBar progress = new ProgressBar();
		progress.setIndeterminate(true);
		progress.setVisible(false);

		Button run = new Button("Run Simulation");
		run.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

		Div metricsBox = new Div();
		metricsBox.setWidthFull();

		run.addClickListener(e -> {
			UI ui = UI.getCurrent();
			progress.setVisible(true);
			run.setEnabled(false);
			metricsBox.removeAll();

			ClientProfile profile = state.getProfile();
			CompletableFuture
					.supplyAsync(() -> engine.run(profile, product, params), virtualThreadExecutor)
					.whenComplete((result, throwable) -> ui.access(() -> {
						progress.setVisible(false);
						run.setEnabled(true);
						if (throwable != null) {
							Notification.show("Simulation failed: " + throwable.getMessage(),
											5000, Notification.Position.MIDDLE)
									.addThemeVariants(NotificationVariant.LUMO_ERROR);
							return;
						}
						state.setSimulationResult(result);
						metricsBox.add(buildMetricsLayout(result, product.getType()));
						renderCharts();
						renderReport();
					}));
		});

		simulatorPanel.add(run, progress, metricsBox);

		if (state.hasSimulationResult()) {
			metricsBox.add(buildMetricsLayout(state.getSimulationResult(), product.getType()));
		}
	}

	private Span textTile(String text) {
		Span s = new Span(text);
		s.getStyle().set("font-weight", "600").set("color", "var(--lumo-primary-text-color)");
		return s;
	}

	private HorizontalLayout buildMetricsLayout(SimulationResult r, ProductType type) {
		HorizontalLayout row = new HorizontalLayout();
		row.setWidthFull();
		row.getStyle().set("flex-wrap", "wrap");
		row.setSpacing(true);
		row.add(metricTile("Expected Return", percent(r.getExpectedReturn())));
		row.add(metricTile("Success Probability", percent(r.getSuccessProbability())));
		row.add(metricTile("Max Loss", percent(r.getMaxLoss())));
		row.add(metricTile("VaR 95%", percent(r.getVar95())));
		row.add(metricTile("Median Payoff", String.format("$%,.0f", r.getMedianPayoff())));
		if (type == ProductType.AUTOCALLABLE) {
			row.add(metricTile("Autocall Prob.", percent(r.getAutocallProbability())));
		}
		row.add(metricTile("Delta (Δ ≈)", String.format("%.4f", r.getDeltaApproximation())));
		return row;
	}

	private Div metricTile(String label, String value) {
		Div tile = new Div();
		tile.addClassName("metric-tile");
		tile.setWidth("180px");
		Span l = new Span(label);
		l.addClassName("label");
		Span v = new Span(value);
		v.addClassName("value");
		Div labelDiv = new Div(l);
		Div valueDiv = new Div(v);
		tile.add(labelDiv, valueDiv);
		return tile;
	}

	// ---------- Tab 3: Charts ----------

	private void renderCharts() {
		chartsPanel.removeAll();
		chartsPanel.setPadding(false);

		if (!state.hasSimulationResult()) {
			chartsPanel.add(new Paragraph("Run a simulation to see charts."));
			return;
		}

		SimulationResult result = state.getSimulationResult();

		Div payoffDiv = new Div();
		payoffDiv.setId("payoff-chart");
		payoffDiv.setWidthFull();
		payoffDiv.setHeight("420px");

		Div histDiv = new Div();
		histDiv.setId("hist-chart");
		histDiv.setWidthFull();
		histDiv.setHeight("420px");

		chartsPanel.add(new H3("Payoff at Maturity"), payoffDiv,
				new H3("Distribution of Returns"), histDiv);

		try {
			String payoffOptions = mapper.writeValueAsString(payoffOptions(result));
			String histOptions = mapper.writeValueAsString(histogramOptions(result));
			UI.getCurrent().getPage().executeJs(renderChartScript(),
					payoffDiv, payoffOptions, histDiv, histOptions);
		} catch (JsonProcessingException e) {
			Notification.show("Chart options serialization failed: " + e.getMessage(),
							4000, Notification.Position.MIDDLE)
					.addThemeVariants(NotificationVariant.LUMO_ERROR);
		}
	}

	private String renderChartScript() {
		return """
				const render = () => {
				  if (typeof ApexCharts === 'undefined') { setTimeout(render, 100); return; }
				  $0.innerHTML = ''; new ApexCharts($0, JSON.parse($1)).render();
				  $2.innerHTML = ''; new ApexCharts($2, JSON.parse($3)).render();
				};
				render();
				""";
	}

	private Map<String, Object> payoffOptions(SimulationResult r) {
		List<Map<String, Number>> points = new ArrayList<>();
		double[] xs = r.getPayoffCurveX();
		double[] ys = r.getPayoffCurveY();
		for (int i = 0; i < xs.length; i++) {
			points.add(Map.of("x", round(xs[i], 1), "y", round(ys[i], 2)));
		}
		Map<String, Object> opts = new LinkedHashMap<>();
		opts.put("chart", Map.of("type", "line", "height", 400, "toolbar", Map.of("show", false)));
		opts.put("series", List.of(Map.of("name", "Payoff", "data", points)));
		opts.put("xaxis", Map.of(
				"type", "numeric",
				"title", Map.of("text", "Underlying change (% of spot)")));
		opts.put("yaxis", Map.of("title", Map.of("text", "Payoff (currency)")));
		opts.put("colors", List.of("#0b2545"));
		opts.put("stroke", Map.of("width", 3, "curve", "smooth"));
		opts.put("tooltip", Map.of("shared", true));
		return opts;
	}

	private Map<String, Object> histogramOptions(SimulationResult r) {
		double[] returns = r.getReturnDistribution();
		double min = Arrays.stream(returns).min().orElse(-0.5);
		double max = Arrays.stream(returns).max().orElse(0.5);
		if (max - min < 1e-6) { max = min + 1e-3; }
		double step = (max - min) / HIST_BINS;
		int[] counts = new int[HIST_BINS];
		for (double v : returns) {
			int bin = Math.min(HIST_BINS - 1, Math.max(0, (int) ((v - min) / step)));
			counts[bin]++;
		}
		List<Map<String, Object>> points = new ArrayList<>();
		for (int i = 0; i < HIST_BINS; i++) {
			double centre = min + step * (i + 0.5);
			points.add(Map.of(
					"x", String.format(Locale.US, "%.0f%%", centre * 100),
					"y", counts[i]));
		}
		Map<String, Object> opts = new LinkedHashMap<>();
		opts.put("chart", Map.of("type", "bar", "height", 400, "toolbar", Map.of("show", false)));
		opts.put("series", List.of(Map.of("name", "Frequency", "data", points)));
		opts.put("xaxis", Map.of("title", Map.of("text", "Final return bucket")));
		opts.put("yaxis", Map.of("title", Map.of("text", "Paths")));
		opts.put("colors", List.of("#d4af37"));
		opts.put("plotOptions", Map.of("bar", Map.of("columnWidth", "95%")));
		opts.put("dataLabels", Map.of("enabled", false));
		return opts;
	}

	// ---------- Tab 4: Report ----------

	private void renderReport() {
		reportPanel.removeAll();
		reportPanel.setPadding(false);

		if (!state.hasSimulationResult()) {
			reportPanel.add(new Paragraph("Run a simulation to generate a report."));
			return;
		}

		TextArea explanation = new TextArea("AI-Generated Explanation");
		explanation.setWidthFull();
		explanation.setHeight("160px");
		explanation.setReadOnly(true);
		explanation.setValue(state.getAiExplanation() == null ? "" : state.getAiExplanation());

		ProgressBar progress = new ProgressBar();
		progress.setIndeterminate(true);
		progress.setVisible(false);

		Button generateExplanation = new Button(
				state.getAiExplanation() == null ? "Generate Explanation" : "Regenerate Explanation");
		generateExplanation.addClickListener(e -> {
			UI ui = UI.getCurrent();
			progress.setVisible(true);
			generateExplanation.setEnabled(false);
			CompletableFuture
					.supplyAsync(() -> aiService.generateExplanation(
									state.getSelectedProduct(), state.getSimulationResult()),
							virtualThreadExecutor)
					.whenComplete((text, throwable) -> ui.access(() -> {
						progress.setVisible(false);
						generateExplanation.setEnabled(true);
						generateExplanation.setText("Regenerate Explanation");
						if (throwable != null) {
							Notification.show("Explanation generation failed: " + throwable.getMessage(),
											5000, Notification.Position.MIDDLE)
									.addThemeVariants(NotificationVariant.LUMO_ERROR);
							return;
						}
						state.setAiExplanation(text);
						explanation.setValue(text);
					}));
		});

		Anchor download = new Anchor(buildPdfResource(), "");
		download.getElement().setAttribute("download", true);
		Button pdfButton = new Button("Export PDF");
		pdfButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		download.add(pdfButton);
		download.getStyle().set("text-decoration", "none");

		HorizontalLayout actions = new HorizontalLayout(generateExplanation, download);
		actions.setAlignItems(FlexComponent.Alignment.CENTER);

		Paragraph note = new Paragraph(
				"PDF includes the client profile, selected product, simulation metrics, charts, "
						+ "AI explanation, and the compliance disclaimer.");
		note.getStyle().set("font-size", "var(--lumo-font-size-s)")
				.set("color", "var(--lumo-secondary-text-color)");

		reportPanel.add(explanation, actions, progress, note);
	}

	private StreamResource buildPdfResource() {
		String fname = "structura-report-" + System.currentTimeMillis() + ".pdf";
		return new StreamResource(fname, () -> {
			byte[] bytes = pdfService.generateReport(
					state.getProfile(),
					state.getSelectedProduct(),
					state.getSimulationResult(),
					state.getAiExplanation() == null ? "" : state.getAiExplanation());
			return new ByteArrayInputStream(bytes);
		});
	}

	// ---------- shared ----------

	private Paragraph buildDisclaimer() {
		Paragraph disc = new Paragraph(
				"⚠ This tool is for simulation and educational purposes only. "
						+ "It does not constitute financial advice. Consult a licensed financial advisor "
						+ "before making any investment decisions.");
		disc.addClassName("disclaimer-footer");
		return disc;
	}

	private static String percent(double v) {
		return String.format("%.2f%%", v * 100.0);
	}

	private static double round(double v, int decimals) {
		double scale = Math.pow(10, decimals);
		return Math.round(v * scale) / scale;
	}
}
