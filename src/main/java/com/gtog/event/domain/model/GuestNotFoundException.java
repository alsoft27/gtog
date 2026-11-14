package com.gtog.event.domain.model;

public class GuestNotFoundException extends EventDomainException {

	public GuestNotFoundException() {
		super("Guest not found in this event");
	}
}
