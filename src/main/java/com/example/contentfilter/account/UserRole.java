package com.example.contentfilter.account;

/** V2 access roles; the names match the {@code ck_app_users_role} constraint. */
public enum UserRole {
	/** Manages accounts and reads the audit trail, in addition to analyst work. */
	ADMIN,
	/** Uploads workbooks and decides review cases. */
	ANALYST
}
