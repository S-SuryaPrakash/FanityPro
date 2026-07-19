package com.example.contentfilter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.contentfilter.config.ModelServiceProperties;
import com.example.contentfilter.config.RiskPolicyProperties;
import com.example.contentfilter.domain.ClassificationResult;
import com.example.contentfilter.domain.ConversationContext;
import com.example.contentfilter.domain.ExtractedSequence;
import com.example.contentfilter.domain.ModelPrediction;
import com.example.contentfilter.domain.RiskCategory;
import com.example.contentfilter.web.CorrelationIdFilter;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;

/** Verifies the exact Spring-to-FastAPI HTTP and release-gate contract. */
class FastApiRiskModelTests {

	private static final String MODEL = "minuva/MiniLMv2-toxic-jigsaw";
	private static final String REVISION = "00eacca7ba7c09b1e82db508b03a901bf9cc89eb";
	private static final String BASE_URL = "http://model.test:8000";

	@AfterEach
	void clearLoggingContext() {
		MDC.clear();
	}

	@Test
	void sendsContextAndCorrelationIdAndRestoresRequestOrder() {
		Fixture fixture = fixture(properties(32, 2, true));
		fixture.server().expect(once(), requestTo(BASE_URL + FastApiRiskModel.CLASSIFY_PATH))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header(CorrelationIdFilter.HEADER_NAME, "module-6-test"))
				.andExpect(content().json("""
						{"sequences":[
						  {"sequenceId":"first","text":"Thank you for contacting support.",
						   "conversationId":"conversation-42","speakerRole":"agent","language":"en"},
						  {"sequenceId":"second","text":"You are stupid.",
						   "conversationId":null,"speakerRole":null,"language":"en"}
						]}
						""", JsonCompareMode.STRICT))
				.andRespond(withSuccess(batchResponse("second", "first"), MediaType.APPLICATION_JSON));

		MDC.put(CorrelationIdFilter.MDC_KEY, "module-6-test");
		List<ModelPrediction> predictions = fixture.model().predict(List.of(
				sequence("first", "Thank you for contacting support.",
						new ConversationContext(
								"conversation-42", "message-1", "agent", null, "en", "chat")),
				sequence("second", "You are stupid.", ConversationContext.empty())));

		assertEquals(List.of("first", "second"),
				predictions.stream().map(ModelPrediction::sequenceId).toList());
		assertEquals(MODEL, predictions.getFirst().modelId());
		assertEquals(REVISION, predictions.getFirst().modelRevision());
		assertEquals(0.91,
				predictions.getFirst().scores().get(RiskCategory.HARASSMENT_OR_INSULT));
		assertFalse(predictions.getFirst().inputTruncated());
		fixture.server().verify();
	}

	@Test
	void splitsLargeWorkIntoConfiguredFastApiBatches() {
		Fixture fixture = fixture(properties(1, 1, true));
		fixture.server().expect(once(), requestTo(BASE_URL + FastApiRiskModel.CLASSIFY_PATH))
				.andRespond(withSuccess(batchResponse("first"), MediaType.APPLICATION_JSON));
		fixture.server().expect(once(), requestTo(BASE_URL + FastApiRiskModel.CLASSIFY_PATH))
				.andRespond(withSuccess(batchResponse("second"), MediaType.APPLICATION_JSON));

		List<ModelPrediction> predictions = fixture.model().predict(List.of(
				sequence("first", "One", ConversationContext.empty()),
				sequence("second", "Two", ConversationContext.empty())));

		assertEquals(2, predictions.size());
		fixture.server().verify();
	}

	@Test
	void retriesOneTransientFailureButDoesNotFallBackToAnotherModel() {
		Fixture fixture = fixture(properties(32, 2, true));
		fixture.server().expect(once(), requestTo(BASE_URL + FastApiRiskModel.CLASSIFY_PATH))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		fixture.server().expect(once(), requestTo(BASE_URL + FastApiRiskModel.CLASSIFY_PATH))
				.andRespond(withSuccess(batchResponse("first"), MediaType.APPLICATION_JSON));

		List<ModelPrediction> predictions = fixture.model().predict(List.of(
				sequence("first", "One", ConversationContext.empty())));

		assertEquals(1, predictions.size());
		assertEquals(MODEL, predictions.getFirst().modelId());
		fixture.server().verify();
	}

	@Test
	void doesNotRetryARejectedInternalRequest() {
		Fixture fixture = fixture(properties(32, 2, true));
		fixture.server().expect(once(), requestTo(BASE_URL + FastApiRiskModel.CLASSIFY_PATH))
				.andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT));

		ModelServiceException exception = assertThrows(
				ModelServiceException.class,
				() -> fixture.model().predict(List.of(
						sequence("first", "One", ConversationContext.empty()))));

		assertEquals(ModelServiceException.Reason.REQUEST_REJECTED, exception.reason());
		fixture.server().verify();
	}

	@Test
	void rejectsProvisionalEvidenceUnlessDevelopmentWaiverIsExplicit() {
		Fixture fixture = fixture(properties(32, 1, false));
		fixture.server().expect(once(), requestTo(BASE_URL + FastApiRiskModel.CLASSIFY_PATH))
				.andRespond(withSuccess(batchResponse("first"), MediaType.APPLICATION_JSON));

		ModelServiceException exception = assertThrows(
				ModelServiceException.class,
				() -> fixture.model().predict(List.of(
						sequence("first", "One", ConversationContext.empty()))));

		assertEquals(ModelServiceException.Reason.MODEL_NOT_APPROVED, exception.reason());
		fixture.server().verify();
	}

	@Test
	void rejectsUnexpectedModelRevisionAndMissingPredictionIds() {
		Fixture revisionFixture = fixture(properties(32, 1, true));
		revisionFixture.server().expect(once(), requestTo(BASE_URL + FastApiRiskModel.CLASSIFY_PATH))
				.andRespond(withSuccess(
						batchResponseWithRevision("f".repeat(40), "first"),
						MediaType.APPLICATION_JSON));

		ModelServiceException revisionFailure = assertThrows(
				ModelServiceException.class,
				() -> revisionFixture.model().predict(List.of(
						sequence("first", "One", ConversationContext.empty()))));
		assertEquals(ModelServiceException.Reason.INVALID_RESPONSE, revisionFailure.reason());
		revisionFixture.server().verify();

		Fixture idFixture = fixture(properties(32, 1, true));
		idFixture.server().expect(once(), requestTo(BASE_URL + FastApiRiskModel.CLASSIFY_PATH))
				.andRespond(withSuccess(batchResponse("unexpected"), MediaType.APPLICATION_JSON));
		ModelServiceException idFailure = assertThrows(
				ModelServiceException.class,
				() -> idFixture.model().predict(List.of(
						sequence("first", "One", ConversationContext.empty()))));
		assertEquals(ModelServiceException.Reason.INVALID_RESPONSE, idFailure.reason());
		idFixture.server().verify();
	}

	@Test
	void readinessValidatesTheSamePinnedModelMetadata() {
		Fixture fixture = fixture(properties(32, 1, true));
		fixture.server().expect(once(), requestTo(BASE_URL + FastApiRiskModel.READY_PATH))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(readyResponse(), MediaType.APPLICATION_JSON));

		FastApiRiskModel.ModelServiceStatus status = fixture.model().readiness();

		assertEquals(MODEL, status.model());
		assertEquals("PROVISIONAL", status.evaluationStatus());
		assertFalse(status.approvedForProduction());
		fixture.server().verify();
	}

	@Test
	void feedsFastApiEvidenceIntoTheExistingVersionedPolicy() {
		Fixture fixture = fixture(properties(32, 1, true));
		fixture.server().expect(once(), requestTo(BASE_URL + FastApiRiskModel.CLASSIFY_PATH))
				.andRespond(withSuccess(batchResponse("first"), MediaType.APPLICATION_JSON));
		ClassificationService classificationService = new RiskClassificationService(
				fixture.model(), new VersionedRiskPolicy(policyProperties()));

		ClassificationResult result = classificationService.classify(List.of(
				sequence("first", "One", ConversationContext.empty()))).getFirst();

		assertEquals(RiskCategory.MANUAL_REVIEW, result.primaryCategory());
		assertEquals(MODEL, result.modelId());
		assertEquals(REVISION, result.modelRevision());
		assertEquals("module-6-policy-v1", result.policyVersion());
		fixture.server().verify();
	}

	@Test
	void healthIndicatorStopsReadinessWhenFastApiIsUnavailable() {
		Fixture fixture = fixture(properties(32, 1, true));
		fixture.server().expect(once(), requestTo(BASE_URL + FastApiRiskModel.READY_PATH))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		FastApiModelHealthIndicator indicator =
				new FastApiModelHealthIndicator(fixture.model());

		assertEquals(Status.OUT_OF_SERVICE, indicator.health().getStatus());
		fixture.server().verify();
	}

	private Fixture fixture(ModelServiceProperties properties) {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.createServer(builder);
		RestClient restClient = builder.baseUrl(properties.baseUrl()).build();
		return new Fixture(new FastApiRiskModel(restClient, properties), server);
	}

	private ModelServiceProperties properties(
			int batchSize,
			int maxAttempts,
			boolean allowProvisional) {
		return new ModelServiceProperties(
				URI.create(BASE_URL), MODEL, REVISION,
				Duration.ofSeconds(1), Duration.ofSeconds(5),
				batchSize, maxAttempts, allowProvisional);
	}

	private ExtractedSequence sequence(
			String id,
			String text,
			ConversationContext context) {
		return new ExtractedSequence(id, 0, 0, List.of(0), text, context);
	}

	private RiskPolicyProperties policyProperties() {
		return new RiskPolicyProperties(
				"module-6-policy-v1",
				0.05,
				Map.of(
						RiskCategory.THREAT, 0.80,
						RiskCategory.HATE_OR_IDENTITY_ATTACK, 0.80,
						RiskCategory.HARASSMENT_OR_INSULT, 0.75,
						RiskCategory.OBSCENE_OR_PROFANE, 0.75,
						RiskCategory.GENERAL_TOXICITY, 0.70),
				List.of(
						RiskCategory.THREAT,
						RiskCategory.HATE_OR_IDENTITY_ATTACK,
						RiskCategory.HARASSMENT_OR_INSULT,
						RiskCategory.OBSCENE_OR_PROFANE,
						RiskCategory.GENERAL_TOXICITY));
	}

	private String batchResponse(String... sequenceIds) {
		return batchResponseWithRevision(REVISION, sequenceIds);
	}

	private String batchResponseWithRevision(String revision, String... sequenceIds) {
		String predictions = java.util.Arrays.stream(sequenceIds)
				.map(id -> """
						{"sequenceId":"%s","scores":{
						  "THREAT":0.01,
						  "HATE_OR_IDENTITY_ATTACK":0.02,
						  "HARASSMENT_OR_INSULT":0.91,
						  "OBSCENE_OR_PROFANE":0.04,
						  "GENERAL_TOXICITY":0.89
						},"inputTruncated":false}
						""".formatted(id))
				.collect(java.util.stream.Collectors.joining(","));
		return """
				{"model":"%s","revision":"%s","evaluationStatus":"PROVISIONAL",
				 "approvedForProduction":false,"predictions":[%s]}
				""".formatted(MODEL, revision, predictions);
	}

	private String readyResponse() {
		return """
				{"status":"READY","model":"%s","revision":"%s",
				 "evaluationStatus":"PROVISIONAL","approvedForProduction":false}
				""".formatted(MODEL, REVISION);
	}

	private record Fixture(FastApiRiskModel model, MockRestServiceServer server) {
	}
}
