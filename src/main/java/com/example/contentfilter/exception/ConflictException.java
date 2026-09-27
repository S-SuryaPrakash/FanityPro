package com.example.contentfilter.exception;

/** The request is valid but conflicts with the current state of a resource (HTTP 409). */
public class ConflictException extends RuntimeException {

	private final String errorCode;

	public ConflictException(String errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public String errorCode() {
		return errorCode;
	}
}
