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
		ErrorResponse response = new ErrorResponse("INVALID_CLASSIFICATION_REQUEST", exception.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
	}
}
