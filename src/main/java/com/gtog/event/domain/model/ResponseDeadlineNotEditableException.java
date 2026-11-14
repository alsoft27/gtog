package com.gtog.event.domain.model;

public class ResponseDeadlineNotEditableException extends EventDomainException {

	public ResponseDeadlineNotEditableException() {
		super("Response deadline can only be changed in DRAFT or before the start of a PUBLISHED event");
	}
}
