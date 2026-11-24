package com.gtog.event.domain.model;

public class InvalidPollRevisionException extends EventDomainException {
	public InvalidPollRevisionException() { super("The poll revision must be present and nonnegative"); }
}
