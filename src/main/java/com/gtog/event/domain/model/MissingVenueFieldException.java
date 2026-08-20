package com.gtog.event.domain.model;

public class MissingVenueFieldException extends EventDomainException {

	public MissingVenueFieldException(String fieldName) {
		super("Venue field must not be blank: %s".formatted(fieldName));
	}
}
