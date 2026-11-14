package com.gtog.event.domain.model;

public class InvalidGuestEmailException extends EventDomainException {

	public InvalidGuestEmailException() {
		super("Guest email is invalid");
	}
}
