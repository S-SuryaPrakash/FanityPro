package com.example.contentfilter.exception;

/**
 * Exception thrown when a classification request fails validation.
 * This is translated to a 400 Bad Request by {@link GlobalExceptionHandler}.
 */
public class InvalidClassificationRequestException extends RuntimeException {

	public InvalidClassificationRequestException(String message) {
		super(message);
	}
}
