package com.wsd.structura.ui;

import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.accordion.AccordionPanel;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "how-ai-helped", layout = MainLayout.class)
@PageTitle("Structura — How AI Helped")
public class HowAIHelpedView extends VerticalLayout {

	public HowAIHelpedView() {
		setPadding(true);
		setSpacing(true);

		add(new H1("How AI Helped Build Structura"));
		add(new Paragraph(
				"Claude (Anthropic) participated across the entire build. The panels below "
						+ "walk through where AI contributed and where humans verified or steered."));

		Accordion accordion = new Accordion();
		accordion.setWidthFull();
		accordion.add(panel("Ideation",
				"Claude proposed the feature set: client profile capture, AI suggestions, Monte Carlo "
						+ "simulator, charts, PDF export, and the disclaimer model. We narrowed to four "
						+ "structured product types: autocallable, reverse convertible, capital protected "
						+ "note, and barrier reverse convertible."));
		accordion.add(panel("Domain Modelling",
				"Claude drafted the Geometric Brownian Motion path generator (GBM in log form), the "
						+ "payoff logic per product type, and the metrics derived from 10,000 simulated "
						+ "paths (expected return, success probability, VaR 95%, median payoff, autocall "
						+ "probability, delta approximation)."));
		accordion.add(panel("Prompt Engineering",
				"Two prompts power AIService: (1) a JSON-only suggestion prompt that asks Claude Haiku "
						+ "for 3–4 tailored products, schema-locked; (2) a plain-English explanation "
						+ "prompt that takes the simulation outcome and turns the numbers into an "
						+ "advisor-grade paragraph (≤180 words)."));
		accordion.add(panel("Explanation Generation",
				"Once a simulation finishes, the metrics are sent to Claude with the product context. "
						+ "Claude returns a single paragraph that frames the best-, expected-, and "
						+ "worst-case outcomes in plain language. The advisor can regenerate at any time."));
		accordion.add(panel("Code Assistance",
				"Claude wrote the bulk of the implementation: Spring Boot wiring, Vaadin Flow views, "
						+ "the virtual-thread-backed Monte Carlo engine, JFreeChart server-side rendering, "
						+ "PDFBox report assembly, and the ApexCharts JS injection. Human review focused "
						+ "on numerical correctness, threading, and compliance language."));
		accordion.add(panel("Safety & Compliance",
				"The disclaimer surfaces on every screen and in the PDF. AIService falls back to a "
						+ "static FallbackProductCatalog if the API key is missing or the call fails, so "
						+ "the app never crashes mid-demo. No PII is sent to the AI — only the structured "
						+ "client profile and simulation numbers."));

		add(accordion);

		Paragraph disc = new Paragraph(
				"⚠ This tool is for simulation and educational purposes only. It does not constitute "
						+ "financial advice. Consult a licensed financial advisor before making any "
						+ "investment decisions.");
		disc.addClassName("disclaimer-footer");
		add(disc);
	}

	private AccordionPanel panel(String title, String body) {
		AccordionPanel p = new AccordionPanel(title, new Paragraph(body));
		return p;
	}
}
