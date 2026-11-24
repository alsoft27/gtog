package com.gtog.event.domain.model;

public class DuplicateCandidateDateException extends EventDomainException {
	public DuplicateCandidateDateException() { super("Candidate dates must be unique"); }
}
