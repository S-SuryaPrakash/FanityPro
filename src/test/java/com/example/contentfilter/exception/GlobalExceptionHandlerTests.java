package com.example.contentfilter.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.contentfilter.service.ModelServiceException;
import com.example.contentfilter.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/** Verifies safe public status codes for failures from the private model service. */
class GlobalExceptionHandlerTests {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void mapsModelTimeoutToGatewayTimeoutProblemDetails() {
		ResponseEntity<ProblemDetail> response = handle(
				ModelServiceException.Reason.TIMEOUT,
				"The model service exceeded its response time limit.");

		assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
		assertEquals("MODEL_SERVICE_TIMEOUT", response.getBody().getProperties().get("errorCode"));
	}

	@Test
	void mapsInvalidEvidenceToBadGatewayWithoutRemoteBody() {
		ResponseEntity<ProblemDetail> response = handle(
				ModelServiceException.Reason.INVALID_RESPONSE,
				"The model response contains invalid score evidence.");

		assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
		assertEquals("INVALID_MODEL_SERVICE_RESPONSE",
				response.getBody().getProperties().get("errorCode"));
	}

	@Test
	void exposesTheIncompleteEvaluationGateAsServiceUnavailable() {
		ResponseEntity<ProblemDetail> response = handle(
				ModelServiceException.Reason.MODEL_NOT_APPROVED,
				"The configured model has not passed the production evaluation gate.");

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
		assertEquals("MODEL_NOT_APPROVED", response.getBody().getProperties().get("errorCode"));
	}

	private ResponseEntity<ProblemDetail> handle(
			ModelServiceException.Reason reason,
			String message) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, "module-6-error");
		return handler.handleModelService(new ModelServiceException(reason, message), request);
	}
}
