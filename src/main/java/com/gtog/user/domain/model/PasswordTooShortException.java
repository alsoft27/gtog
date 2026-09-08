package com.gtog.user.domain.model;

public class PasswordTooShortException extends UserDomainException {

	public PasswordTooShortException() {
		super("Password must be at least 8 characters");
	}
}
