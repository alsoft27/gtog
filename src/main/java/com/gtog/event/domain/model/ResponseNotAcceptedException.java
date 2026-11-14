package com.gtog.event.domain.model;

public class ResponseNotAcceptedException extends GuestResponseException {
	public ResponseNotAcceptedException() { super("Event is not accepting responses at this time"); }
}
