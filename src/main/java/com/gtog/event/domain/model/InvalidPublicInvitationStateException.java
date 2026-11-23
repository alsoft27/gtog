package com.gtog.event.domain.model;

public class InvalidPublicInvitationStateException extends EventDomainException {
	public InvalidPublicInvitationStateException() { super("Invalid public invitation state"); }
}
