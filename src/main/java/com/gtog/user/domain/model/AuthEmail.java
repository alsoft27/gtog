package com.gtog.user.domain.model;

public record AuthEmail(String recipient, String subject, String body) {
	@Override public String toString() { return "AuthEmail[redacted]"; }
}
