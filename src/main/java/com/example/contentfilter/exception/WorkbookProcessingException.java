package com.example.contentfilter.exception;

/**
 * Client-facing failure raised while validating or extracting an uploaded workbook.
 */
public class WorkbookProcessingException extends RuntimeException {

	/** Categories mapped to stable HTTP statuses and API error codes. */
	public enum Reason {
		INVALID_REQUEST,
		UNSUPPORTED_TYPE,
		LIMIT_EXCEEDED,
		INVALID_WORKBOOK
	}

	private final Reason reason;

	public WorkbookProcessingException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public WorkbookProcessingException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}
}
