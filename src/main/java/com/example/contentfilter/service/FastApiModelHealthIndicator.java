package com.example.contentfilter.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Makes Spring readiness depend on the pinned FastAPI model when selected. */
@Component
@ConditionalOnProperty(
		name = "content-filter.classification.provider",
		havingValue = "fastapi")
public class FastApiModelHealthIndicator implements HealthIndicator {

	private final FastApiRiskModel riskModel;

	public FastApiModelHealthIndicator(FastApiRiskModel riskModel) {
		this.riskModel = riskModel;
	}

	@Override
	public Health health() {
		try {
			FastApiRiskModel.ModelServiceStatus status = riskModel.readiness();
			return Health.up()
					.withDetail("model", status.model())
					.withDetail("revision", status.revision())
					.withDetail("evaluationStatus", status.evaluationStatus())
					.withDetail("approvedForProduction", status.approvedForProduction())
					.build();
		}
		catch (ModelServiceException exception) {
			return Health.outOfService()
					.withDetail("reason", exception.reason().name())
					.build();
		}
		catch (RuntimeException exception) {
			return Health.down()
					.withDetail("reason", "INTERNAL_ERROR")
					.build();
		}
	}
}
