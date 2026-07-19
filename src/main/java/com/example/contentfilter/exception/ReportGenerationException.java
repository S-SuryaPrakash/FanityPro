package com.example.contentfilter.exception;

/** Safe application failure raised when an annotated workbook cannot be produced. */
public class ReportGenerationException extends RuntimeException {

	public ReportGenerationException(String message, Throwable cause) {
		super(message, cause);
	}

	public ReportGenerationException(String message) {
		super(message);
	}
}
