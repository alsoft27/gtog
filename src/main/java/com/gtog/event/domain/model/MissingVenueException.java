package com.gtog.event.domain.model;

public class MissingVenueException extends EventDomainException {

	public MissingVenueException() {
		super("An IN_PERSON event requires a venue");
	}
}
