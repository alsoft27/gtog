package com.gtog.event.domain.model;

public class InvalidInvitationBatchException extends EventDomainException {
	public InvalidInvitationBatchException() {
		super("Invitation batch must contain unique guest ids and respect the configured size limit");
	}
}
