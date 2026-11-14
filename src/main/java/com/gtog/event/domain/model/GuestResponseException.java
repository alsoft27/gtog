package com.gtog.event.domain.model;

public abstract class GuestResponseException extends EventDomainException {
	protected GuestResponseException(String message) { super(message); }
}
