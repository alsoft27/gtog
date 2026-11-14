package com.gtog.event.domain.model;

public class InvalidResponseOptionException extends GuestResponseException {
	public InvalidResponseOptionException() { super("Response option does not belong to this event"); }
}
