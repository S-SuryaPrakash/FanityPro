package com.example.contentfilter.exception;

import com.example.contentfilter.dto.ErrorResponse;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain validation exceptions into consistent HTTP error payloads.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	/**
	 * Handles validation errors raised while classifying extracted text.
	 *
	 * @param exception validation failure raised by a classifier
	 * @return structured error response with HTTP 400 status
	 */
	@ExceptionHandler(InvalidClassificationRequestException.class)
	public ResponseEntity<ErrorResponse> handleInvalidClassificationRequest(
			InvalidClassificationRequestException exception) {
		HttpStatus status = HttpStatus.BAD_REQUEST;
		String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		ErrorResponse response = new ErrorResponse(
				status.value(),
				"INVALID_CLASSIFICATION_REQUEST",
				exception.getMessage(),
				timestamp);
		return ResponseEntity.status(status).body(response);
	}
}
