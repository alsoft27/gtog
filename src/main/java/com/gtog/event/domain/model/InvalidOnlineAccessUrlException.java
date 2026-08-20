package com.gtog.event.domain.model;

public class InvalidOnlineAccessUrlException extends EventDomainException {

	public InvalidOnlineAccessUrlException(String url) {
		super("Online access url must be a valid http or https URL: %s".formatted(url));
	}
}
