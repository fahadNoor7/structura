package com.wsd.structura.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "anthropic")
public class AnthropicProperties {

	private String apiKey;
	private String model;
	private String apiUrl;
	private String anthropicVersion;
	private int maxTokens = 1024;
	private int timeoutSeconds = 30;
}
