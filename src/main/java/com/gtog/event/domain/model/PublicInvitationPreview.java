package com.gtog.event.domain.model;

public record PublicInvitationPreview(Event event, boolean canJoin, boolean alreadyJoined, String invitationPath) {
	@Override public String toString() { return "PublicInvitationPreview[redacted]"; }
}
