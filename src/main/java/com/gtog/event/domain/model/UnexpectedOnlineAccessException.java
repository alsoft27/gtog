package com.gtog.event.domain.model;

public class UnexpectedOnlineAccessException extends EventDomainException {

	public UnexpectedOnlineAccessException() {
		super("An IN_PERSON event must not have online access details");
	}
}
