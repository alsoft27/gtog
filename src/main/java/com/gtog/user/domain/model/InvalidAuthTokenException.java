package com.gtog.user.domain.model;

public class InvalidAuthTokenException extends UserDomainException {
	public InvalidAuthTokenException() { super("Invalid or expired token"); }
}
