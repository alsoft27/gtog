package com.gtog.user.domain.model;

public class InvalidEmailException extends UserDomainException {

	public InvalidEmailException(String email) {
		super("Invalid email address: " + email);
	}
}
