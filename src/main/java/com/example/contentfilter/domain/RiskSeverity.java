package com.example.contentfilter.domain;

/** Review priority assigned by policy rather than inferred directly by a model. */
public enum RiskSeverity {
	NONE,
	LOW,
	MEDIUM,
	HIGH,
	CRITICAL
}
