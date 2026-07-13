package com.example.contentfilter.exception;

/**
 * Exception thrown when a classification request fails validation.
 * This is translated to a 400 Bad Request by {@link GlobalExceptionHandler}.
 */
public class InvalidClassificationRequestException extends RuntimeException {

	/**
	 * Creates a validation exception with a client-facing explanation.
	 *
	 * @param message description of the invalid classification input
	 */
	public InvalidClassificationRequestException(String message) {
		super(message);
	}
}
