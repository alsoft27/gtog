package com.gtog.event.domain.model;

public record WhatsAppLinkResult(String guestId, WhatsAppLinkStatus status, WhatsAppLinkSkipReason reason,
		String url, String message) {
	public static WhatsAppLinkResult ready(String guestId, WhatsAppInvitationLink link) {
		return new WhatsAppLinkResult(guestId, WhatsAppLinkStatus.READY, null, link.url(), link.message());
	}
	public static WhatsAppLinkResult skipped(String guestId, WhatsAppLinkSkipReason reason) {
		return new WhatsAppLinkResult(guestId, WhatsAppLinkStatus.SKIPPED, reason, null, null);
	}
	@Override public String toString() { return "WhatsAppLinkResult[redacted]"; }
}
