package com.gtog.event.domain.model;

public class EventConcurrentModificationException extends EventDomainException {

	public EventConcurrentModificationException() {
		super("Event changed concurrently; reload and retry");
	}
}
