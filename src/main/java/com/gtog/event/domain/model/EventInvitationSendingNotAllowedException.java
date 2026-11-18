package com.gtog.event.domain.model;

public class EventInvitationSendingNotAllowedException extends EventDomainException {
	public EventInvitationSendingNotAllowedException() { super("Invitations can only be sent for a published event"); }
}
