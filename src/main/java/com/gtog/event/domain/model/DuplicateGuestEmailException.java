package com.gtog.event.domain.model;

public class DuplicateGuestEmailException extends EventDomainException {

	public DuplicateGuestEmailException() {
		super("Another guest in this event already has that email");
	}
}
