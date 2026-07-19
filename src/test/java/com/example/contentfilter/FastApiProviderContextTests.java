package com.example.contentfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.contentfilter.service.FastApiRiskModel;
import com.example.contentfilter.service.RiskModel;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Confirms that the explicit fastapi profile replaces, rather than supplements, the test model. */
@SpringBootTest(properties =
		"content-filter.classification.model-service.base-url=http://127.0.0.1:1")
@ActiveProfiles("fastapi")
@AutoConfigureMockMvc
class FastApiProviderContextTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void selectsExactlyOneFastApiRiskModel() {
		Map<String, RiskModel> models = applicationContext.getBeansOfType(RiskModel.class);

		assertEquals(1, models.size());
		assertInstanceOf(FastApiRiskModel.class, models.values().iterator().next());
	}

	@Test
	void keepsLivenessUpButReadinessDownWhenFastApiIsUnavailable() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isServiceUnavailable());
	}
}
