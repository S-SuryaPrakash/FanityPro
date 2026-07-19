package com.example.contentfilter.service;

import com.example.contentfilter.config.ModelServiceProperties;
import com.example.contentfilter.domain.ConversationContext;
import com.example.contentfilter.domain.ExtractedSequence;
import com.example.contentfilter.domain.ModelPrediction;
import com.example.contentfilter.domain.RiskCategory;
import com.example.contentfilter.web.CorrelationIdFilter;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Batch-first {@link RiskModel} that obtains provider-neutral scores from FastAPI.
 *
 * <p>It never falls back to the deterministic adapter. A provider outage must
 * remain visible so one workbook cannot contain results from different models.</p>
 */
@Component
@ConditionalOnProperty(
		name = "content-filter.classification.provider",
		havingValue = "fastapi")
public class FastApiRiskModel implements RiskModel {

	static final String CLASSIFY_PATH = "/api/v1/classify/batch";
	static final String READY_PATH = "/ready";
	private static final Set<RiskCategory> EXPECTED_CATEGORIES =
			Set.copyOf(EnumSet.of(
					RiskCategory.THREAT,
					RiskCategory.HATE_OR_IDENTITY_ATTACK,
					RiskCategory.HARASSMENT_OR_INSULT,
					RiskCategory.OBSCENE_OR_PROFANE,
					RiskCategory.GENERAL_TOXICITY));

	private final RestClient restClient;
	private final ModelServiceProperties properties;

	public FastApiRiskModel(
			@Qualifier("fastApiRestClient") RestClient restClient,
			ModelServiceProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public List<ModelPrediction> predict(List<ExtractedSequence> sequences) {
		validateRequest(sequences);
		if (sequences.isEmpty()) {
			return List.of();
		}

		List<ModelPrediction> predictions = new ArrayList<>(sequences.size());
		for (int start = 0; start < sequences.size(); start += properties.batchSize()) {
			int end = Math.min(start + properties.batchSize(), sequences.size());
			List<ExtractedSequence> batch = sequences.subList(start, end);
			predictions.addAll(classifyBatch(batch));
		}
		return List.copyOf(predictions);
	}

	/** Retrieves and validates the operational identity exposed by FastAPI. */
	public ModelServiceStatus readiness() {
		try {
			ReadyResponse response = restClient.get()
					.uri(READY_PATH)
					.headers(this::addCorrelationId)
					.retrieve()
					.body(ReadyResponse.class);
			if (response == null || !"READY".equals(response.status())) {
				throw invalidResponse("The model service did not report READY status.");
			}
			validateMetadata(response.model(), response.revision(), response.evaluationStatus(),
					response.approvedForProduction());
			return new ModelServiceStatus(
					response.model(), response.revision(), response.evaluationStatus(),
					response.approvedForProduction());
		}
		catch (ModelServiceException exception) {
			throw exception;
		}
		catch (ResourceAccessException exception) {
			throw unavailable(exception);
		}
		catch (RestClientResponseException exception) {
			throw fromStatus(exception);
		}
		catch (RestClientException exception) {
			throw new ModelServiceException(
					ModelServiceException.Reason.INVALID_RESPONSE,
					"The model readiness response could not be processed.",
					exception);
		}
	}

	private List<ModelPrediction> classifyBatch(List<ExtractedSequence> sequences) {
		BatchRequest request = new BatchRequest(sequences.stream()
				.map(this::toRequest)
				.toList());
		BatchResponse response = executeWithRetry(request);
		return validateAndMap(response, sequences);
	}

	private BatchResponse executeWithRetry(BatchRequest request) {
		for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
			try {
				BatchResponse response = restClient.post()
						.uri(CLASSIFY_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.headers(this::addCorrelationId)
						.body(request)
						.retrieve()
						.body(BatchResponse.class);
				if (response == null) {
					throw new ModelServiceException(
							ModelServiceException.Reason.INVALID_RESPONSE,
							"The model service returned an empty response.");
				}
				return response;
			}
			catch (ResourceAccessException exception) {
				if (attempt < properties.maxAttempts()) {
					continue;
				}
				throw unavailable(exception);
			}
			catch (RestClientResponseException exception) {
				if (attempt < properties.maxAttempts() && isRetryable(exception)) {
					continue;
				}
				throw fromStatus(exception);
			}
			catch (RestClientException exception) {
				throw new ModelServiceException(
						ModelServiceException.Reason.INVALID_RESPONSE,
						"The model response could not be processed.",
						exception);
			}
		}
		throw new IllegalStateException("The configured model attempt count is invalid.");
	}

	private List<ModelPrediction> validateAndMap(
			BatchResponse response,
			List<ExtractedSequence> sequences) {
		validateMetadata(response.model(), response.revision(), response.evaluationStatus(),
				response.approvedForProduction());
		if (response.predictions() == null) {
			throw invalidResponse("The model response contains no predictions.");
		}

		Map<String, PredictionResponse> byId = new HashMap<>();
		for (PredictionResponse prediction : response.predictions()) {
			if (prediction == null || prediction.sequenceId() == null
					|| prediction.sequenceId().isBlank()
					|| byId.put(prediction.sequenceId(), prediction) != null) {
				throw invalidResponse("The model response contains a missing or duplicate sequence ID.");
			}
		}
		Set<String> requestedIds = new HashSet<>();
		for (ExtractedSequence sequence : sequences) {
			requestedIds.add(sequence.sequenceId());
		}
		if (!byId.keySet().equals(requestedIds)) {
			throw invalidResponse("The model response IDs do not match the requested sequence IDs.");
		}

		try {
			return sequences.stream()
					.map(sequence -> toPrediction(
							byId.get(sequence.sequenceId()), response.model(), response.revision()))
					.toList();
		}
		catch (IllegalArgumentException exception) {
			throw new ModelServiceException(
					ModelServiceException.Reason.INVALID_RESPONSE,
					"The model response contains invalid score evidence.",
					exception);
		}
	}

	private ModelPrediction toPrediction(
			PredictionResponse response,
			String model,
			String revision) {
		if (response.scores() == null || !response.scores().keySet().equals(EXPECTED_CATEGORIES)
				|| response.inputTruncated() == null) {
			throw new IllegalArgumentException("Incomplete model evidence.");
		}
		return new ModelPrediction(
				response.sequenceId(), response.scores(), response.inputTruncated(), model, revision);
	}

	private void validateMetadata(
			String model,
			String revision,
			String evaluationStatus,
			Boolean approvedForProduction) {
		if (!properties.expectedModelId().equals(model)
				|| !properties.expectedRevision().equals(revision)) {
			throw invalidResponse("The model identity or revision does not match configuration.");
		}
		if (approvedForProduction == null || evaluationStatus == null
				|| evaluationStatus.isBlank()) {
			throw invalidResponse("The model response is missing release-gate metadata.");
		}
		boolean provisional = "PROVISIONAL".equals(evaluationStatus);
		boolean approved = "APPROVED".equals(evaluationStatus);
		if ((!provisional && !approved)
				|| (provisional && approvedForProduction)
				|| (approved && !approvedForProduction)) {
			throw invalidResponse("The model release-gate metadata is inconsistent.");
		}
		if (!approvedForProduction && !properties.allowProvisional()) {
			throw new ModelServiceException(
					ModelServiceException.Reason.MODEL_NOT_APPROVED,
					"The configured model has not passed the production evaluation gate.");
		}
	}

	private SequenceRequest toRequest(ExtractedSequence sequence) {
		ConversationContext context = sequence.context();
		String language = context.language() == null ? "en" : context.language();
		return new SequenceRequest(
				sequence.sequenceId(), sequence.text(), context.conversationId(),
				context.speakerRole(), language);
	}

	private void validateRequest(List<ExtractedSequence> sequences) {
		if (sequences == null) {
			throw new IllegalArgumentException("Sequence batch must not be null.");
		}
		Set<String> identifiers = new HashSet<>();
		for (ExtractedSequence sequence : sequences) {
			if (sequence == null || !identifiers.add(sequence.sequenceId())) {
				throw new IllegalArgumentException("Sequence IDs must be present and unique.");
			}
		}
	}

	private void addCorrelationId(HttpHeaders headers) {
		String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
		if (correlationId != null && !correlationId.isBlank()) {
			headers.set(CorrelationIdFilter.HEADER_NAME, correlationId);
		}
	}

	private boolean isRetryable(RestClientResponseException exception) {
		int status = exception.getStatusCode().value();
		return status == 502 || status == 503 || status == 504;
	}

	private ModelServiceException fromStatus(RestClientResponseException exception) {
		int status = exception.getStatusCode().value();
		if (status == 504) {
			return new ModelServiceException(
					ModelServiceException.Reason.TIMEOUT,
					"The model service exceeded its response time limit.",
					exception);
		}
		if (status == 502 || status == 503) {
			return new ModelServiceException(
					ModelServiceException.Reason.UNAVAILABLE,
					"The model service is temporarily unavailable.",
					exception);
		}
		return new ModelServiceException(
				ModelServiceException.Reason.REQUEST_REJECTED,
				"The model service rejected the internal classification contract.",
				exception);
	}

	private ModelServiceException unavailable(ResourceAccessException exception) {
		boolean timeout = causedByTimeout(exception);
		return new ModelServiceException(
				timeout ? ModelServiceException.Reason.TIMEOUT
						: ModelServiceException.Reason.UNAVAILABLE,
				timeout ? "The model service exceeded its response time limit."
						: "The model service is temporarily unavailable.",
				exception);
	}

	private boolean causedByTimeout(Throwable error) {
		Throwable current = error;
		while (current != null) {
			if (current instanceof SocketTimeoutException
					|| current instanceof HttpTimeoutException) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private ModelServiceException invalidResponse(String message) {
		return new ModelServiceException(ModelServiceException.Reason.INVALID_RESPONSE, message);
	}

	private record BatchRequest(List<SequenceRequest> sequences) {

		@Override
		public String toString() {
			return "BatchRequest[sequenceCount=" + sequences.size() + "]";
		}
	}

	private record SequenceRequest(
			String sequenceId,
			String text,
			String conversationId,
			String speakerRole,
			String language) {
	}

	private record BatchResponse(
			String model,
			String revision,
			String evaluationStatus,
			Boolean approvedForProduction,
			List<PredictionResponse> predictions) {
	}

	private record PredictionResponse(
			String sequenceId,
			Map<RiskCategory, Double> scores,
			Boolean inputTruncated) {
	}

	private record ReadyResponse(
			String status,
			String model,
			String revision,
			String evaluationStatus,
			Boolean approvedForProduction) {
	}

	/** Safe operational model metadata returned to the Actuator health indicator. */
	public record ModelServiceStatus(
			String model,
			String revision,
			String evaluationStatus,
			boolean approvedForProduction) {
	}
}
