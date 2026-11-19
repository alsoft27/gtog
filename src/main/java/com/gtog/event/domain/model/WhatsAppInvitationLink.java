package com.gtog.event.domain.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public record WhatsAppInvitationLink(String url, String message) {
	public static WhatsAppInvitationLink create(String phoneE164, String message) {
		String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
		return new WhatsAppInvitationLink("https://wa.me/" + phoneE164.substring(1) + "?text=" + encoded, message);
	}
	@Override public String toString() { return "WhatsAppInvitationLink[redacted]"; }
}
