package com.example.contentfilter.exception;

/** A review decision that breaks the decision rules (HTTP 400). */
public class InvalidReviewDecisionException extends RuntimeException {

	public InvalidReviewDecisionException(String message) {
		super(message);
	}
}
