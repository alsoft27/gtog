package com.gtog.event.domain.model;

public class UnexpectedVenueException extends EventDomainException {

	public UnexpectedVenueException() {
		super("An ONLINE event must not have a venue");
	}
}
