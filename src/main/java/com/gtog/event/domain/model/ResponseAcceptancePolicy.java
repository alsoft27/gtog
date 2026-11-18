package com.gtog.event.domain.model;

public record ResponseAcceptancePolicy(boolean acceptingResponses, boolean allowResponseChange) {
	public boolean canRespond(boolean hasResponse) {
		return acceptingResponses && (!hasResponse || allowResponseChange);
	}
}
