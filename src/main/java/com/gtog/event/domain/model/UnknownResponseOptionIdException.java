package com.gtog.event.domain.model;

public class UnknownResponseOptionIdException extends EventDomainException {

	public UnknownResponseOptionIdException(String responseOptionId) {
		super("Response option id does not belong to this event: %s".formatted(responseOptionId));
	}
}
