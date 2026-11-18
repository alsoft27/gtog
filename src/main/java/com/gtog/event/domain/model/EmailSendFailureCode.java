package com.gtog.event.domain.model;

public enum EmailSendFailureCode {
	RECIPIENT_REJECTED, PROVIDER_UNAVAILABLE, INVALID_RECIPIENT, EMAIL_NOT_CONFIGURED;

	public boolean isProviderFailure() {
		return this == PROVIDER_UNAVAILABLE || this == EMAIL_NOT_CONFIGURED;
	}
}
