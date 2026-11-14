package com.gtog.event.domain.model;

public class ResponseOptionDeletionNotAllowedException extends EventDomainException {
	public ResponseOptionDeletionNotAllowedException() {
		super("Cannot delete a response option with responses on a published event");
	}
}
