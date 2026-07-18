package com.example.contentfilter.config;

import com.example.contentfilter.domain.RiskCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Versioned thresholds and precedence used to convert scores into decisions. */
@Validated
@ConfigurationProperties("content-filter.classification.policy")
public record RiskPolicyProperties(
		@NotBlank String version,
		@DecimalMin("0.0") @DecimalMax("1.0") double uncertaintyMargin,
		@NotEmpty Map<RiskCategory, Double> thresholds,
		@NotEmpty List<RiskCategory> precedence) {

	public RiskPolicyProperties {
		thresholds = thresholds == null ? Map.of() : Map.copyOf(thresholds);
		precedence = precedence == null ? List.of() : List.copyOf(precedence);
		Set<RiskCategory> expected = EnumSet.noneOf(RiskCategory.class);
		for (RiskCategory category : RiskCategory.values()) {
			if (category.isDetectedRisk()) {
				expected.add(category);
			}
		}
		if (!thresholds.keySet().equals(expected)) {
			throw new IllegalArgumentException("Policy thresholds must cover every detected-risk category.");
		}
		if (thresholds.values().stream().anyMatch(value -> value == null
				|| !Double.isFinite(value) || value < 0.0 || value > 1.0)) {
			throw new IllegalArgumentException("Policy thresholds must be between 0 and 1.");
		}
		if (precedence.size() != expected.size()
				|| !EnumSet.copyOf(precedence).equals(expected)) {
			throw new IllegalArgumentException("Policy precedence must contain every risk category once.");
		}
	}
}
