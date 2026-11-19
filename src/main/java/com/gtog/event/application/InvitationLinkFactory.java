package com.gtog.event.application;

import java.net.URI;

public final class InvitationLinkFactory {
	private final String publicBaseUrl;
	public InvitationLinkFactory(String publicBaseUrl) {
		URI base = URI.create(publicBaseUrl);
		if (!("https".equals(base.getScheme()) || "http".equals(base.getScheme())) || base.getHost() == null
				|| base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
			throw new IllegalArgumentException("Invalid public base URL");
		}
		this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
	}
	public String forGuest(String token) { return publicBaseUrl + "/r/" + token; }
}
