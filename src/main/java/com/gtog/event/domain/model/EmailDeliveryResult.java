package com.gtog.event.domain.model;

public record EmailDeliveryResult(boolean accepted, EmailSendFailureCode code) {
	public EmailDeliveryResult {
		if (accepted == (code != null)) {
			throw new IllegalArgumentException("Inconsistent email delivery result");
		}
	}

	public static EmailDeliveryResult success() {
		return new EmailDeliveryResult(true, null);
	}

	public static EmailDeliveryResult failure(EmailSendFailureCode code) {
		return new EmailDeliveryResult(false, code);
	}
}
