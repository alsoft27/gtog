package com.gtog.event.domain.model;

public class GuestCapacityExceededException extends EventDomainException {
	public GuestCapacityExceededException() { super("Guest capacity exceeded"); }
}
