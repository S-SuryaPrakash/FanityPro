package com.example.contentfilter.service;

/** Safe failure raised when the private model-service contract cannot be used. */
public class ModelServiceException extends RuntimeException {

	private final Reason reason;

	public ModelServiceException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public ModelServiceException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

	/** Failure classes used by the public Problem Details translation. */
	public enum Reason {
		UNAVAILABLE,
		TIMEOUT,
		REQUEST_REJECTED,
		INVALID_RESPONSE,
		MODEL_NOT_APPROVED
	}
}
