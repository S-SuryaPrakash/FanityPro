package com.example.contentfilter.audit;

/** Security- and decision-relevant actions recorded in the audit trail. */
public enum AuditAction {
	USER_CREATED,
	LOGIN_SUCCEEDED,
	BATCH_UPLOADED,
	CASE_DECIDED
}
