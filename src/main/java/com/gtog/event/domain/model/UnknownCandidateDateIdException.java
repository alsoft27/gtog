package com.gtog.event.domain.model;

public class UnknownCandidateDateIdException extends EventDomainException {
	public UnknownCandidateDateIdException() { super("The candidate date does not belong to the event"); }
}
