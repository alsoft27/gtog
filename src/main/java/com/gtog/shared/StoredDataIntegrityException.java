package com.gtog.shared;

// No conserva el mensaje ni la causa original: pueden contener datos personales o credenciales.
public class StoredDataIntegrityException extends RuntimeException {

	private final String eventId;
	private final String failureType;

	public StoredDataIntegrityException(String eventId, Class<? extends Exception> failureType) {
		super("Stored event data is invalid");
		this.eventId = eventId;
		this.failureType = failureType.getName();
	}

	public String getEventId() { return eventId; }
	public String getFailureType() { return failureType; }
}
