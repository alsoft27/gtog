package com.gtog.event.domain.model;

public class MissingCandidateDateException extends EventDomainException {
	public MissingCandidateDateException() { super("A candidate date is required"); }
}
