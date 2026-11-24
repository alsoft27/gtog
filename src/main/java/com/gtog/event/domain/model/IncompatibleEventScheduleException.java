package com.gtog.event.domain.model;

public class IncompatibleEventScheduleException extends EventDomainException {
	public IncompatibleEventScheduleException() { super("The supplied temporal configuration is incompatible with the event type"); }
}
