package com.gtog.event.domain.model;

public record GuestEmailSendResult(String guestId, InvitationSendStatus status, EmailSendFailureCode code,
		EmailSendSkipReason reason, Boolean recorded) {
	public static GuestEmailSendResult sent(String guestId, boolean recorded) {
		return new GuestEmailSendResult(guestId, InvitationSendStatus.SENT, null, null, recorded);
	}
	public static GuestEmailSendResult failed(String guestId, EmailSendFailureCode code) {
		return new GuestEmailSendResult(guestId, InvitationSendStatus.FAILED, code, null, null);
	}
	public static GuestEmailSendResult skipped(String guestId, EmailSendSkipReason reason) {
		return new GuestEmailSendResult(guestId, InvitationSendStatus.SKIPPED, null, reason, null);
	}
}
