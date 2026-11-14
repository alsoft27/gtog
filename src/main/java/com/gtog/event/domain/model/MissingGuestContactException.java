package com.gtog.event.domain.model;

public class MissingGuestContactException extends EventDomainException {

	public MissingGuestContactException() {
		super("Guest must have an email or a phone number");
	}
}
