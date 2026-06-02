package com.wsd.structura.ui;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import com.wsd.structura.domain.ClientProfile;
import com.wsd.structura.domain.SimulationParameters;
import com.wsd.structura.domain.SimulationResult;
import com.wsd.structura.domain.StructuredProduct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@VaadinSessionScope
@Getter
@Setter
public class WizardState {

	private ClientProfile profile;
	private List<StructuredProduct> suggestions;
	private StructuredProduct selectedProduct;
	private SimulationParameters simulationParameters;
	private SimulationResult simulationResult;
	private String aiExplanation;

	public boolean hasSuggestions() {
		return suggestions != null && !suggestions.isEmpty();
	}

	public boolean hasSelectedProduct() {
		return selectedProduct != null;
	}

	public boolean hasSimulationResult() {
		return simulationResult != null;
	}

	public void clear() {
		profile = null;
		suggestions = null;
		selectedProduct = null;
		simulationParameters = null;
		simulationResult = null;
		aiExplanation = null;
	}
}
