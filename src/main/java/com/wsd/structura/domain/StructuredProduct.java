package com.wsd.structura.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuredProduct {

	private String name;
	private ProductType type;
	private String description;
	private String payoffLogic;
	private List<String> pros;
	private List<String> cons;
	private String recommendedFor;
}
