package com.gtog.user.domain.model;

public class InvalidUserTimeZoneException extends UserDomainException {

	public InvalidUserTimeZoneException(String timeZone) {
		super("Invalid IANA time zone: %s".formatted(timeZone));
	}
}
