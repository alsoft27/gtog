package com.gtog.event.domain.model;

public class PublicInvitationNotEditableException extends EventDomainException {
	public PublicInvitationNotEditableException() { super("Public invitation can only be changed in DRAFT or PUBLISHED"); }
}
