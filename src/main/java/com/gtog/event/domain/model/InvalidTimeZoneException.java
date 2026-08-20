package com.gtog.event.domain.model;

public class InvalidTimeZoneException extends EventDomainException {

	public InvalidTimeZoneException(String timeZone) {
		super("Invalid IANA time zone: %s".formatted(timeZone));
	}
}
