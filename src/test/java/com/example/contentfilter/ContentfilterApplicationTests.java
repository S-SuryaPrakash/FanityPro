package com.example.contentfilter;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.example.contentfilter.service.DeterministicRiskModel;
import com.example.contentfilter.service.RiskModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that Spring can create the complete application context and wire
 * all required components.
 */
@SpringBootTest
class ContentfilterApplicationTests {

	@Autowired
	private RiskModel riskModel;

	/**
	 * Fails when application configuration or dependency injection is invalid.
	 */
	@Test
	void contextLoads() {
		assertInstanceOf(DeterministicRiskModel.class, riskModel);
	}

}
