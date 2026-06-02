package com.wsd.structura.ai;

import com.wsd.structura.domain.ProductType;
import com.wsd.structura.domain.StructuredProduct;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FallbackProductCatalog {

	public List<StructuredProduct> defaultSuggestions() {
		return List.of(
				StructuredProduct.builder()
						.name("Phoenix Autocallable on Tech Basket")
						.type(ProductType.AUTOCALLABLE)
						.description("Quarterly observation autocallable note linked to a tech-stock basket. "
								+ "Pays a contingent coupon if all underlyings stay above the coupon barrier, "
								+ "and redeems early at par + coupon if all are above the autocall trigger.")
						.payoffLogic("Coupon: paid each quarter if all underlyings > 70% of strike. "
								+ "Autocall: at par + coupon if all > 100% on any observation date. "
								+ "At maturity: if no autocall and worst-of > 60% barrier → par; "
								+ "otherwise indexed to worst-performing underlying.")
						.pros(List.of("Attractive headline coupon", "Possible early redemption at par",
								"Conditional capital protection above barrier"))
						.cons(List.of("Capital at risk below barrier", "Coupon not guaranteed",
								"Reinvestment risk on autocall"))
						.recommendedFor("Investors seeking enhanced yield with a moderate market view")
						.build(),
				StructuredProduct.builder()
						.name("Reverse Convertible on Single Index")
						.type(ProductType.REVERSE_CONVERTIBLE)
						.description("Fixed-coupon note on a major equity index. Pays a known coupon stream; "
								+ "principal is repaid in cash if the index stays above strike at maturity, "
								+ "otherwise converted to index units at the strike.")
						.payoffLogic("Coupons: fixed, paid regardless of market direction. "
								+ "Maturity: par if index ≥ strike, else index units delivered (or cash-settled equivalent).")
						.pros(List.of("Guaranteed coupon stream", "Simple, transparent payoff",
								"Yield-pickup vs vanilla bonds"))
						.cons(List.of("Capped upside", "Exposure to index downside below strike",
								"No barrier protection"))
						.recommendedFor("Income-focused investors with neutral-to-mildly-bullish view")
						.build(),
				StructuredProduct.builder()
						.name("Capital Protected Note with Upside Participation")
						.type(ProductType.CAPITAL_PROTECTED_NOTE)
						.description("Principal-protected at maturity with 80% participation in any positive "
								+ "performance of the underlying basket.")
						.payoffLogic("Maturity payoff = max(100%, 100% + 80% × max(0, final/initial − 1)) × principal. "
								+ "Principal is fully protected at maturity regardless of underlying performance.")
						.pros(List.of("100% capital protection at maturity", "Upside participation in growth",
								"Suited to risk-averse investors"))
						.cons(List.of("No coupon income", "Participation < 100%",
								"Issuer credit risk"))
						.recommendedFor("Conservative investors seeking equity exposure without downside risk")
						.build()
		);
	}
}
