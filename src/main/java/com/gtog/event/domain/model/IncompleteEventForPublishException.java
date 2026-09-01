package com.gtog.event.domain.model;

public class IncompleteEventForPublishException extends EventDomainException {

	public IncompleteEventForPublishException(String reason) {
		super("Event cannot be published: %s".formatted(reason));
	}
}
