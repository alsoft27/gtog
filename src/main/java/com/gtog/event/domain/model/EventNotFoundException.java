package com.gtog.event.domain.model;

// El dominio no sabe de codigos HTTP: hereda de EventDomainException igual que el resto. Es el advice quien
// decide que esta subclase concreta se traduce a 404 en vez del 422 generico.
public class EventNotFoundException extends EventDomainException {

	public EventNotFoundException(String eventId) {
		super("Event not found: %s".formatted(eventId));
	}
}
