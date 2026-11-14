package com.gtog.event.domain.model;

public class BlankGuestNameException extends EventDomainException {

	public BlankGuestNameException() {
		super("Guest name must not be blank");
	}
}
