package com.gtog.event.domain.model;

public record PublicInvitation(String token) {
	public PublicInvitation {
		if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
			throw new InvalidPublicInvitationTokenException();
		}
	}

	@Override public String toString() { return "PublicInvitation[redacted]"; }
}
