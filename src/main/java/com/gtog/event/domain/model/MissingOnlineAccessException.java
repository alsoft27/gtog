package com.gtog.event.domain.model;

public class MissingOnlineAccessException extends EventDomainException {

	public MissingOnlineAccessException() {
		super("An ONLINE event requires online access details");
	}
}
