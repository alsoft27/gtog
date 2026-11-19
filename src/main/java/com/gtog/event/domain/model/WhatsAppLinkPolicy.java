package com.gtog.event.domain.model;

public record WhatsAppLinkPolicy(int maxLinksPerRequest) {
	public static final int MAX_URL_LENGTH = 2000;
	public WhatsAppLinkPolicy {
		if (maxLinksPerRequest < 1) { throw new IllegalArgumentException("WhatsApp request limit must be positive"); }
	}
	public void ensurePublished(Event event) {
		if (event.getStatus() != EventStatus.PUBLISHED) { throw new EventInvitationSendingNotAllowedException(); }
	}
	public WhatsAppLinkSkipReason skipReason(Guest guest, boolean answered) {
		if (guest.getPhoneE164() == null || guest.getPhoneE164().isBlank()) { return WhatsAppLinkSkipReason.NO_PHONE; }
		if (answered) { return WhatsAppLinkSkipReason.ALREADY_ANSWERED; }
		return guest.hasValidPhoneE164() ? null : WhatsAppLinkSkipReason.INVALID_PHONE;
	}
	public boolean acceptsUrl(String url) { return url.length() <= MAX_URL_LENGTH; }
}
