package com.example.contentfilter.exception;

import com.example.contentfilter.service.ModelServiceException;
import com.example.contentfilter.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain validation exceptions into consistent HTTP error payloads.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** Maps workbook validation failures to the V1 HTTP error contract. */
	@ExceptionHandler(WorkbookProcessingException.class)
	public ResponseEntity<ProblemDetail> handleWorkbookProcessing(
			WorkbookProcessingException exception,
			HttpServletRequest request) {
		return switch (exception.reason()) {
			case UNSUPPORTED_TYPE -> problem(
					HttpStatus.UNSUPPORTED_MEDIA_TYPE,
					"UNSUPPORTED_WORKBOOK_TYPE",
					exception.getMessage(),
					request);
			case LIMIT_EXCEEDED -> problem(
					HttpStatus.CONTENT_TOO_LARGE,
					"WORKBOOK_LIMIT_EXCEEDED",
					exception.getMessage(),
					request);
			case INVALID_WORKBOOK -> problem(
					HttpStatus.BAD_REQUEST,
					"INVALID_WORKBOOK",
					exception.getMessage(),
					request);
			case INVALID_REQUEST -> problem(
					HttpStatus.BAD_REQUEST,
					"INVALID_UPLOAD",
					exception.getMessage(),
					request);
		};
	}

	/**
	 * Handles validation errors raised while classifying extracted text.
	 *
	 * @param exception validation failure raised by a classifier
	 * @return structured error response with HTTP 400 status
	 */
	@ExceptionHandler(InvalidClassificationRequestException.class)
	public ResponseEntity<ProblemDetail> handleInvalidClassificationRequest(
			InvalidClassificationRequestException exception,
			HttpServletRequest request) {
		return problem(
				HttpStatus.BAD_REQUEST,
				"INVALID_CLASSIFICATION_REQUEST",
				exception.getMessage(),
				request);
	}

	/** Handles request and workbook validation failures. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ProblemDetail> handleIllegalArgument(
			IllegalArgumentException exception,
			HttpServletRequest request) {
		return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), request);
	}

	/** Maps private FastAPI failures without exposing remote response bodies. */
	@ExceptionHandler(ModelServiceException.class)
	public ResponseEntity<ProblemDetail> handleModelService(
			ModelServiceException exception,
			HttpServletRequest request) {
		return switch (exception.reason()) {
			case TIMEOUT -> problem(
					HttpStatus.GATEWAY_TIMEOUT,
					"MODEL_SERVICE_TIMEOUT",
					exception.getMessage(),
					request);
			case UNAVAILABLE -> problem(
					HttpStatus.SERVICE_UNAVAILABLE,
					"MODEL_SERVICE_UNAVAILABLE",
					exception.getMessage(),
					request);
			case MODEL_NOT_APPROVED -> problem(
					HttpStatus.SERVICE_UNAVAILABLE,
					"MODEL_NOT_APPROVED",
					exception.getMessage(),
					request);
			case REQUEST_REJECTED, INVALID_RESPONSE -> problem(
					HttpStatus.BAD_GATEWAY,
					"INVALID_MODEL_SERVICE_RESPONSE",
					exception.getMessage(),
					request);
		};
	}

	/** Maps report failures without exposing workbook or Apache POI internals. */
	@ExceptionHandler(ReportGenerationException.class)
	public ResponseEntity<ProblemDetail> handleReportGeneration(
			ReportGenerationException exception,
			HttpServletRequest request) {
		LOGGER.error("Workbook report generation failed with correlation ID {}",
				correlationId(request), exception);
		return problem(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"REPORT_GENERATION_FAILED",
				exception.getMessage(),
				request);
	}

	/** Handles uploads rejected by Spring's multipart size boundary. */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ProblemDetail> handleMaximumUploadSize(
			MaxUploadSizeExceededException exception,
			HttpServletRequest request) {
		return problem(
				HttpStatus.CONTENT_TOO_LARGE,
				"UPLOAD_TOO_LARGE",
				"The uploaded file exceeds the configured maximum size.",
				request);
	}

	/** Handles malformed multipart requests without exposing parser internals. */
	@ExceptionHandler(MultipartException.class)
	public ResponseEntity<ProblemDetail> handleMultipart(
			MultipartException exception,
			HttpServletRequest request) {
		return problem(
				HttpStatus.BAD_REQUEST,
				"INVALID_MULTIPART_REQUEST",
				"The multipart request could not be processed.",
				request);
	}

	/** Converts unexpected failures to a safe response and logs the stack trace. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ProblemDetail> handleUnexpected(
			Exception exception,
			HttpServletRequest request) {
		String correlationId = correlationId(request);
		LOGGER.error("Unhandled request failure with correlation ID {}", correlationId, exception);
		return problem(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"INTERNAL_ERROR",
				"An unexpected error occurred.",
				request);
	}

	private ResponseEntity<ProblemDetail> problem(
			HttpStatus status,
			String errorCode,
			String detail,
			HttpServletRequest request) {
		String correlationId = correlationId(request);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("urn:content-filter:problem:"
				+ errorCode.toLowerCase(Locale.ROOT).replace('_', '-')));
		problem.setTitle(status.getReasonPhrase());
		problem.setInstance(URI.create("urn:content-filter:request:" + correlationId));
		problem.setProperty("errorCode", errorCode);
		problem.setProperty("correlationId", correlationId);
		problem.setProperty(
				"timestamp",
				OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
		return ResponseEntity.status(status).body(problem);
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
		return value instanceof String id ? id : "unavailable";
	}
}
