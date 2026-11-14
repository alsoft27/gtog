package com.gtog.event.domain.model;

public class DuplicateGuestTokenException extends EventDomainException {

	public DuplicateGuestTokenException() {
		super("Guest invitation token conflicts with an existing invitation");
	}
}
