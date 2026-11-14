package com.gtog.event.domain.model;

public class DuplicateGuestPhoneException extends EventDomainException {

	public DuplicateGuestPhoneException() {
		super("Another guest in this event already has that phone number");
	}
}
