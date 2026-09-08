package com.gtog.user.domain.model;

public class EmailAlreadyRegisteredException extends UserDomainException {

	public EmailAlreadyRegisteredException(String email) {
		super("Email already registered: " + email);
	}
}
