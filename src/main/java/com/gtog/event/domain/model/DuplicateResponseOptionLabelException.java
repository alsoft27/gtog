package com.gtog.event.domain.model;

public class DuplicateResponseOptionLabelException extends EventDomainException {

	public DuplicateResponseOptionLabelException() {
		super("Response option labels must not be duplicated within the same event");
	}
}
