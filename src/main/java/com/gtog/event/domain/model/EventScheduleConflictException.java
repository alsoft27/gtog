package com.gtog.event.domain.model;

public class EventScheduleConflictException extends EventDomainException {
	public EventScheduleConflictException() { super("This operation is not available for the event type"); }
}
