package com.gtog.event.domain.model;

public class EventNotPublishableException extends EventDomainException {

	public EventNotPublishableException(String eventId) {
		super("Event %s cannot be published from its current status".formatted(eventId));
	}
}
