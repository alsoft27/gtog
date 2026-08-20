package com.gtog.event.domain.model;

public class BlankEventTitleException extends EventDomainException {

	public BlankEventTitleException() {
		super("Event title must not be blank");
	}
}
