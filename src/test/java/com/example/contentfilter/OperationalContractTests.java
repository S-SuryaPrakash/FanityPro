package com.example.contentfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.contentfilter.config.UploadLimitsProperties;
import com.example.contentfilter.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.unit.DataSize;

/** Verifies the HTTP and configuration contracts established in V1 Module 1. */
@SpringBootTest
@AutoConfigureMockMvc
class OperationalContractTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UploadLimitsProperties uploadLimits;

	@Test
	void exposesActuatorHealthProbes() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(header().exists(CorrelationIdFilter.HEADER_NAME));

		mockMvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void returnsProblemDetailsWithCorrelationIdForInvalidUpload() throws Exception {
		String correlationId = "learning-module-1";

		mockMvc.perform(multipart("/upload")
					.header(CorrelationIdFilter.HEADER_NAME, correlationId))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(header().string(CorrelationIdFilter.HEADER_NAME, correlationId))
				.andExpect(jsonPath("$.type").value("urn:content-filter:problem:invalid-request"))
				.andExpect(jsonPath("$.title").value("Bad Request"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.correlationId").value(correlationId));
	}

	@Test
	void bindsDocumentedUploadLimits() {
		assertEquals(DataSize.ofMegabytes(5), uploadLimits.maxFileSize());
		assertEquals(2_000, uploadLimits.maxSequences());
		assertEquals(100, uploadLimits.maxCellsPerSequence());
		assertEquals(4_000, uploadLimits.maxSequenceLength());
		assertEquals(1_000_000, uploadLimits.maxTotalTextLength());
		assertFalse(uploadLimits.maxProcessingTime().isNegative());
	}
}
