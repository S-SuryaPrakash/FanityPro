package com.example.contentfilter.exception;

/**
 * Login failed. Deliberately does not say whether the email is unknown, the
 * password is wrong, or the account is disabled.
 */
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("The email or password is incorrect.");
	}
}
