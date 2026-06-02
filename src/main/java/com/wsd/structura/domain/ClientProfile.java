package com.wsd.structura.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientProfile {

	@NotNull
	@DecimalMin(value = "1000.0", message = "Minimum investment is $1,000")
	private Double investmentAmount;

	@NotNull
	private RiskLevel riskLevel;

	@Min(1)
	@Max(10)
	private int horizonYears;

	@NotNull
	private MarketView marketView;

	@NotEmpty
	@Size(max = 5, message = "Pick at most 5 underlyings")
	private List<String> underlyings;

	private String clientName;
}
