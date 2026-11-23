package com.gtog.event.domain.port.in;

public record JoinPublicInvitationCommand(String joinToken, String previousGuestToken, String name, String email, String phone) {
	@Override public String toString() { return "JoinPublicInvitationCommand[redacted]"; }
}
