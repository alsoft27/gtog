package com.gtog.event.domain.model;

public class MissingOnlineAccessFieldException extends EventDomainException {

	public MissingOnlineAccessFieldException(String fieldName) {
		super("Online access field must not be blank: %s".formatted(fieldName));
	}
}
