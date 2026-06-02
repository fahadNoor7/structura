package com.wsd.structura.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wsd.structura.ai.AIService;
import com.wsd.structura.domain.ClientProfile;
import com.wsd.structura.domain.MarketView;
import com.wsd.structura.domain.RiskLevel;
import com.wsd.structura.domain.StructuredProduct;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Structura — Configurator")
public class ClientInputView extends VerticalLayout {

	private static final List<String> UNDERLYING_CHOICES = List.of(
			"AAPL", "NVDA", "TSLA", "GOOGL", "MSFT", "S&P500", "EURUSD", "Gold");

	private final WizardState state;
	private final AIService aiService;
	private final Executor virtualThreadExecutor;

	private final Binder<ClientProfile> binder = new BeanValidationBinder<>(ClientProfile.class);

	public ClientInputView(WizardState state,
	                       AIService aiService,
	                       @Qualifier("virtualThreadExecutor") Executor virtualThreadExecutor) {
		this.state = state;
		this.aiService = aiService;
		this.virtualThreadExecutor = virtualThreadExecutor;

		setSizeFull();
		setPadding(true);
		setSpacing(true);

		add(buildHeader());
		add(buildForm());
		add(buildDisclaimer());
	}

	private VerticalLayout buildHeader() {
		H1 title = new H1("Client Profile");
		Paragraph subtitle = new Paragraph(
				"Configure the client to generate AI-tailored structured product suggestions.");
		VerticalLayout box = new VerticalLayout(title, subtitle);
		box.setPadding(false);
		box.setSpacing(false);
		return box;
	}

	private FormLayout buildForm() {
		NumberField investment = new NumberField("Investment Amount");
		investment.setPrefixComponent(new com.vaadin.flow.component.html.Span("$"));
		investment.setMin(1000);
		investment.setStep(1000);
		investment.setValue(100_000.0);

		RadioButtonGroup<RiskLevel> risk = new RadioButtonGroup<>();
		risk.setLabel("Risk Tolerance");
		risk.setItems(RiskLevel.values());
		risk.setValue(RiskLevel.MEDIUM);

		IntegerField horizon = new IntegerField("Horizon (years)");
		horizon.setMin(1);
		horizon.setMax(10);
		horizon.setStepButtonsVisible(true);
		horizon.setValue(3);

		RadioButtonGroup<MarketView> view = new RadioButtonGroup<>();
		view.setLabel("Market View");
		view.setItems(MarketView.values());
		view.setValue(MarketView.NEUTRAL);

		MultiSelectComboBox<String> underlyings = new MultiSelectComboBox<>("Underlyings");
		underlyings.setItems(UNDERLYING_CHOICES);
		underlyings.setHelperText("Pick up to 5");
		underlyings.select("AAPL", "NVDA");

		TextField clientName = new TextField("Client Name (optional)");

		binder.forField(investment)
				.asRequired("Investment amount is required")
				.bind(ClientProfile::getInvestmentAmount, ClientProfile::setInvestmentAmount);
		binder.forField(risk)
				.asRequired("Risk tolerance is required")
				.bind(ClientProfile::getRiskLevel, ClientProfile::setRiskLevel);
		binder.forField(horizon)
				.asRequired("Horizon is required")
				.bind(ClientProfile::getHorizonYears, ClientProfile::setHorizonYears);
		binder.forField(view)
				.asRequired("Market view is required")
				.bind(ClientProfile::getMarketView, ClientProfile::setMarketView);
		binder.forField(underlyings)
				.asRequired("Pick at least one underlying")
				.bind(
						p -> p.getUnderlyings() == null ? java.util.Collections.emptySet()
								: new java.util.LinkedHashSet<>(p.getUnderlyings()),
						(p, set) -> p.setUnderlyings(new ArrayList<>(set))
				);
		binder.forField(clientName)
				.bind(ClientProfile::getClientName, ClientProfile::setClientName);

		ProgressBar progress = new ProgressBar();
		progress.setIndeterminate(true);
		progress.setVisible(false);

		Button generate = new Button("Generate AI Suggestions");
		generate.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		generate.setWidthFull();
		generate.addClickListener(e -> submit(generate, progress));

		FormLayout form = new FormLayout(investment, risk, horizon, view,
				underlyings, clientName, progress, generate);
		form.setColspan(progress, 2);
		form.setColspan(generate, 2);
		form.setResponsiveSteps(
				new FormLayout.ResponsiveStep("0", 1),
				new FormLayout.ResponsiveStep("600px", 2));

		// Pre-populate from existing state if user comes back
		ClientProfile existing = state.getProfile();
		if (existing != null) {
			binder.readBean(existing);
		}

		return form;
	}

	private void submit(Button generate, ProgressBar progress) {
		ClientProfile profile = new ClientProfile();
		if (!binder.writeBeanIfValid(profile)) {
			Notification.show("Please fix the highlighted fields", 3000,
							Notification.Position.MIDDLE)
					.addThemeVariants(NotificationVariant.LUMO_ERROR);
			return;
		}

		UI ui = UI.getCurrent();
		progress.setVisible(true);
		generate.setEnabled(false);

		CompletableFuture
				.supplyAsync(() -> aiService.generateSuggestions(profile), virtualThreadExecutor)
				.whenComplete((suggestions, throwable) -> ui.access(() -> {
					progress.setVisible(false);
					generate.setEnabled(true);
					if (throwable != null) {
						Notification.show("Suggestion generation failed: " + throwable.getMessage(),
										5000, Notification.Position.MIDDLE)
								.addThemeVariants(NotificationVariant.LUMO_ERROR);
						return;
					}
					List<StructuredProduct> products = suggestions;
					state.setProfile(profile);
					state.setSuggestions(products);
					ui.navigate(ResultsView.class);
				}));
	}

	private Paragraph buildDisclaimer() {
		Paragraph disc = new Paragraph(
				"⚠ This tool is for simulation and educational purposes only. "
						+ "It does not constitute financial advice. Past simulation results are not indicative "
						+ "of future performance. Consult a licensed financial advisor before making any "
						+ "investment decisions.");
		disc.addClassName("disclaimer-footer");
		return disc;
	}
}
