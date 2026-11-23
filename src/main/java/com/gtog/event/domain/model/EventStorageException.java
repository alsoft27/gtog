package com.gtog.event.domain.model;

// Nunca conserva el mensaje ni la causa del driver, que pueden incluir credenciales.
public class EventStorageException extends RuntimeException {
	public EventStorageException() { super("Unable to store event"); }
}
