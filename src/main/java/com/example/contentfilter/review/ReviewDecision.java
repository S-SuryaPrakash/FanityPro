package com.example.contentfilter.review;

/** A reviewer's final call on one automated result. */
public enum ReviewDecision {
	/** The automated category is correct. */
	CONFIRMED,
	/** The message is harmful, but belongs to a different category. */
	OVERRIDDEN,
	/** The message is not harmful; the automated flag was a false positive. */
	DISMISSED
}
