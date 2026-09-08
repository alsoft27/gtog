package com.gtog.user.domain.model;

public class BlankTimeZoneException extends UserDomainException {

	public BlankTimeZoneException() {
		super("Time zone must not be blank");
	}
}
