package com.gtog.event.domain.model;

// El dominio no sabe de codigos HTTP: hereda de EventDomainException igual que el resto. Es el advice quien
// decide que esta subclase concreta se traduce a 409 en vez del 422 generico.
public class EventNotEditableException extends EventDomainException {

	public EventNotEditableException(String eventId) {
		super("Event %s is not in DRAFT status and can no longer be edited".formatted(eventId));
	}
}
