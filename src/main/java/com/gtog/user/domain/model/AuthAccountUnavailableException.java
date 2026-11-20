package com.gtog.user.domain.model;

public class AuthAccountUnavailableException extends UserDomainException {
	public AuthAccountUnavailableException() { super("Authentication required"); }
}
