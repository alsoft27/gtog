package com.gtog.event.domain.model;

public class StalePollRevisionException extends EventDomainException {
	public StalePollRevisionException() { super("The candidate dates have changed; reload the event"); }
}
