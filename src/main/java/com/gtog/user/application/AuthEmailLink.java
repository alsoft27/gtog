package com.gtog.user.application;

import java.net.URI;

final class AuthEmailLink {
	private final String base;
	private final String path;
	AuthEmailLink(String base, String path) {
		URI uri;
		try { uri = URI.create(base); } catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Invalid authentication link configuration"); }
		if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
				|| uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
				|| !path.startsWith("/") || path.startsWith("//") || path.contains("?") || path.contains("#")) {
			throw new IllegalArgumentException("Invalid authentication link configuration");
		}
		this.base = base.replaceAll("/+$", ""); this.path = path;
	}
	String build(String token) { return base + path + "#token=" + token; }
}
