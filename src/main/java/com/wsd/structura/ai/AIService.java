package com.wsd.structura.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsd.structura.domain.ClientProfile;
import com.wsd.structura.domain.SimulationResult;
import com.wsd.structura.domain.StructuredProduct;
import com.wsd.structura.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIService {

	private static final Logger log = LoggerFactory.getLogger(AIService.class);

	private final RestClient anthropic;
	private final AnthropicProperties props;
	private final ObjectMapper mapper;
	private final FallbackProductCatalog fallback;

	public AIService(RestClient anthropicRestClient,
	                 AnthropicProperties props,
	                 ObjectMapper mapper,
	                 FallbackProductCatalog fallback) {
		this.anthropic = anthropicRestClient;
		this.props = props;
		this.mapper = mapper;
		this.fallback = fallback;
	}

	public List<StructuredProduct> generateSuggestions(ClientProfile profile) {
		if (isApiKeyMissing()) {
			log.warn("Anthropic API key not configured — using fallback product catalog.");
			return fallback.defaultSuggestions();
		}

		String systemPrompt = """
				You are a structured products specialist advising a financial advisor.
				Respond with ONLY a JSON array of 3 to 4 structured product suggestions.
				Each item must match this exact schema:
				{
				  "name": string,
				  "type": one of ["AUTOCALLABLE","REVERSE_CONVERTIBLE","CAPITAL_PROTECTED_NOTE","BARRIER_REVERSE_CONVERTIBLE"],
				  "description": string,
				  "payoffLogic": string,
				  "pros": [string],
				  "cons": [string],
				  "recommendedFor": string
				}
				Do not wrap the JSON in Markdown code fences. Do not add commentary.
				""";

		String userPrompt = ("""
				Client profile:
				- Investment amount: $%,.0f
				- Risk tolerance: %s
				- Investment horizon: %d years
				- Market view: %s
				- Underlyings of interest: %s
				- Client name: %s

				Suggest 3 to 4 structured products tailored to this profile.
				""").formatted(
				profile.getInvestmentAmount(),
				profile.getRiskLevel(),
				profile.getHorizonYears(),
				profile.getMarketView(),
				String.join(", ", profile.getUnderlyings()),
				profile.getClientName() == null ? "(unspecified)" : profile.getClientName()
		);

		try {
			String body = callAnthropic(systemPrompt, userPrompt);
			String json = JsonUtils.stripCodeFence(body);
			List<StructuredProduct> parsed = mapper.readValue(json,
					new TypeReference<List<StructuredProduct>>() {});
			if (parsed == null || parsed.isEmpty()) {
				log.warn("AI returned empty suggestion list — falling back to defaults.");
				return fallback.defaultSuggestions();
			}
			return parsed;
		} catch (Exception e) {
			log.warn("AI suggestion call failed ({}). Using fallback catalog.", e.getMessage());
			return fallback.defaultSuggestions();
		}
	}

	public String generateExplanation(StructuredProduct product, SimulationResult result) {
		if (isApiKeyMissing()) {
			return defaultExplanation(product, result);
		}

		String systemPrompt = """
				You are explaining a structured product to a non-expert investor.
				Write a single concise paragraph (180 words max). Use plain English,
				avoid jargon, and address both best- and worst-case scenarios.
				""";

		String userPrompt = ("""
				Product: %s (type: %s)
				Description: %s
				Payoff logic: %s

				Simulation results:
				- Expected return: %.2f%%
				- Probability of beating the initial investment: %.1f%%
				- Maximum simulated loss: %.2f%%
				- 95%% Value-at-Risk (5th percentile return): %.2f%%
				- Median payoff (in currency units): %.2f
				- Probability of early autocall: %.1f%%

				Write the explanation now.
				""").formatted(
				product.getName(),
				product.getType(),
				product.getDescription(),
				product.getPayoffLogic(),
				result.getExpectedReturn() * 100,
				result.getSuccessProbability() * 100,
				result.getMaxLoss() * 100,
				result.getVar95() * 100,
				result.getMedianPayoff(),
				result.getAutocallProbability() * 100
		);

		try {
			return callAnthropic(systemPrompt, userPrompt).trim();
		} catch (Exception e) {
			log.warn("AI explanation call failed ({}). Using fallback text.", e.getMessage());
			return defaultExplanation(product, result);
		}
	}

	private String callAnthropic(String systemPrompt, String userPrompt) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", props.getModel());
		body.put("max_tokens", props.getMaxTokens());
		body.put("system", systemPrompt);
		body.put("messages", List.of(Map.of("role", "user", "content", userPrompt)));

		JsonNode response = anthropic.post()
				.body(body)
				.retrieve()
				.body(JsonNode.class);

		if (response == null) {
			throw new IllegalStateException("Empty response from Anthropic API");
		}
		JsonNode content = response.path("content");
		if (!content.isArray() || content.isEmpty()) {
			throw new IllegalStateException("Anthropic response missing content array: " + response);
		}
		String text = content.get(0).path("text").asText();
		if (text.isBlank()) {
			throw new IllegalStateException("Anthropic response content text is blank");
		}
		return text;
	}

	private boolean isApiKeyMissing() {
		String key = props.getApiKey();
		return key == null || key.isBlank() || "changeme".equalsIgnoreCase(key);
	}

	private static String defaultExplanation(StructuredProduct product, SimulationResult result) {
		return ("This %s, %s, was simulated 10,000 times. On average it returns about %.2f%% over the horizon. "
				+ "There is roughly a %.1f%% chance it beats your initial investment, and in the worst 5%% of "
				+ "simulated outcomes the return is around %.2f%% (Value-at-Risk). In the worst case observed, "
				+ "the loss reaches %.2f%%. Read all product terms and the regulatory disclaimer carefully — "
				+ "this is a simulation, not financial advice.").formatted(
						product.getType(),
						product.getName(),
						result.getExpectedReturn() * 100,
						result.getSuccessProbability() * 100,
						result.getVar95() * 100,
						result.getMaxLoss() * 100);
	}
}
