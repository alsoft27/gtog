package com.gtog.event.domain.model;

public class InvalidGuestPhoneException extends EventDomainException {

	public InvalidGuestPhoneException() {
		super("Guest phone must be a valid international number without an extension");
	}
}
