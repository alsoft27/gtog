package com.gtog.event.domain.model;

public class EventNotCancellableException extends EventDomainException {

	public EventNotCancellableException(String eventId) {
		super("Event %s cannot be cancelled from its current status".formatted(eventId));
	}
}
