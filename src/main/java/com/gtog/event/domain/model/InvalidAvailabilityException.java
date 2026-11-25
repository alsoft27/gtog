package com.gtog.event.domain.model;

public class InvalidAvailabilityException extends GuestResponseException {
	public InvalidAvailabilityException() { super("Invalid candidate selection"); }
}
