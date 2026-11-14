package com.gtog.event.domain.model;

public class InvalidResponseEmailException extends GuestResponseException {
	public InvalidResponseEmailException() { super("Response email is invalid"); }
}
