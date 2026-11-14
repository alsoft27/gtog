package com.gtog.event.domain.model;

public class GuestResponseDeletionConfirmationRequiredException extends EventDomainException {

	public GuestResponseDeletionConfirmationRequiredException() {
		super("Deleting this guest requires explicit confirmation to delete their response");
	}
}
