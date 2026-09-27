package com.example.contentfilter.exception;

import static com.example.contentfilter.exception.GlobalExceptionHandler.problem;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Request-validation failures in the V1 error shape ({@code errorCode},
 * {@code correlationId}).
 *
 * <p>Ordered first because Spring Boot's own ProblemDetail advice also handles
 * these framework exceptions and would otherwise win. Kept separate from
 * {@link GlobalExceptionHandler}, whose catch-all must stay lowest precedence
 * so framework 404/405 responses are not turned into 500s.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestValidationExceptionHandler {

	/** Bean Validation failures on a JSON request body, listed per field. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ProblemDetail> handleValidation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		String detail = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.sorted()
				.collect(Collectors.joining("; "));
		return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
				detail.isEmpty() ? "The request is invalid." : detail, request);
	}

	/** Constraint violations on request parameters, such as an out-of-range page size. */
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ProblemDetail> handleMethodValidation(
			HandlerMethodValidationException exception,
			HttpServletRequest request) {
		String detail = exception.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(error -> result.getMethodParameter().getParameterName()
								+ ": " + error.getDefaultMessage()))
				.sorted()
				.collect(Collectors.joining("; "));
		return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
				detail.isEmpty() ? "The request is invalid." : detail, request);
	}

	/** Malformed JSON or an unknown enum value, without echoing parser internals. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ProblemDetail> handleUnreadableBody(
			HttpMessageNotReadableException exception,
			HttpServletRequest request) {
		return problem(
				HttpStatus.BAD_REQUEST,
				"MALFORMED_REQUEST_BODY",
				"The request body is not valid JSON for this operation.",
				request);
	}

	/** A path or query parameter of the wrong type, such as a malformed UUID. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ProblemDetail> handleTypeMismatch(
			MethodArgumentTypeMismatchException exception,
			HttpServletRequest request) {
		return problem(
				HttpStatus.BAD_REQUEST,
				"INVALID_REQUEST_PARAMETER",
				"Parameter '" + exception.getName() + "' has an invalid value.",
				request);
	}
}
