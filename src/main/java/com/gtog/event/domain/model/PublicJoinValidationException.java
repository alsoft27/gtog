package com.gtog.event.domain.model;

import java.util.List;

public class PublicJoinValidationException extends EventDomainException {
	private final List<PublicJoinFieldError> errors;
	public PublicJoinValidationException(List<PublicJoinFieldError> errors) {
		super("Invalid public invitation registration"); this.errors = List.copyOf(errors);
	}
	public List<PublicJoinFieldError> getErrors() { return errors; }
}
