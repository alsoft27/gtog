package com.gtog.user.domain.model;

public class BlankUserNameException extends UserDomainException {

	public BlankUserNameException() {
		super("User name must not be blank");
	}
}
