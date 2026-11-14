package com.gtog.event.domain.model;

public class InvitationNotFoundException extends EventDomainException {

	public InvitationNotFoundException() {
		super("Invitation not found");
	}
}
