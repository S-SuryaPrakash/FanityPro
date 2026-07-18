package com.example.contentfilter.domain;

/** Stable risk taxonomy exposed by the V1 decision policy. */
public enum RiskCategory {
	THREAT,
	HATE_OR_IDENTITY_ATTACK,
	HARASSMENT_OR_INSULT,
	OBSCENE_OR_PROFANE,
	GENERAL_TOXICITY,
	NO_AUTOMATED_FLAG,
	MANUAL_REVIEW;

	public boolean isDetectedRisk() {
		return this != NO_AUTOMATED_FLAG && this != MANUAL_REVIEW;
	}
}
