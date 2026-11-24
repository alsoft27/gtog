package com.gtog.event.domain.model;

public class InvalidCandidateDateCountException extends EventDomainException {
	public InvalidCandidateDateCountException() { super("The number of candidate dates is outside the allowed range"); }
}
