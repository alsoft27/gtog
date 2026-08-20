package com.gtog.event.domain.model;

public class InvalidResponseOptionCountException extends EventDomainException {

	public InvalidResponseOptionCountException(int count) {
		super("An event must have between 2 and 5 response options, got %d".formatted(count));
	}
}
