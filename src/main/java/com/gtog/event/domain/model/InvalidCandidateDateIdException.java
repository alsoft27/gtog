package com.gtog.event.domain.model;

public class InvalidCandidateDateIdException extends EventDomainException {
	public InvalidCandidateDateIdException() { super("Candidate date identifiers must be present and unique"); }
}
