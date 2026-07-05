package com.example.contentfilter.exception;

import com.example.contentfilter.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(InvalidClassificationRequestException.class)
	public ResponseEntity<ErrorResponse> handleInvalidClassificationRequest(
			InvalidClassificationRequestException exception) {
		HttpStatus status = HttpStatus.BAD_REQUEST;
		ErrorResponse response = new ErrorResponse(
				status.value(),
				"INVALID_CLASSIFICATION_REQUEST",
				exception.getMessage());
		return ResponseEntity.status(status).body(response);
	}
}
