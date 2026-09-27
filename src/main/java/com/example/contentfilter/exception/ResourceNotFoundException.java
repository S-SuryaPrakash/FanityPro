package com.example.contentfilter.exception;

/** A requested V2 resource does not exist (HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String message) {
		super(message);
	}
}
