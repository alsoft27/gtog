package com.gtog.event.domain.model;

public class InvalidPublicInvitationTokenException extends EventDomainException {
	public InvalidPublicInvitationTokenException() { super("Invalid public invitation credential"); }
}
