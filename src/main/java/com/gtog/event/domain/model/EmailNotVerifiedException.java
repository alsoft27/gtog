package com.gtog.event.domain.model;

public class EmailNotVerifiedException extends EventDomainException {
	public EmailNotVerifiedException() { super("Email verification required"); }
}
