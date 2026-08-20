package com.gtog.event.domain.model;

public class BlankResponseOptionLabelException extends EventDomainException {

	public BlankResponseOptionLabelException() {
		super("Response option label must not be blank");
	}
}
