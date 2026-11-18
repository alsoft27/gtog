package com.gtog.event.domain.model;

public record InvitationEmail(String recipient, String subject, String body) {
	@Override
	public String toString() {
		return "InvitationEmail[redacted]";
	}
}
