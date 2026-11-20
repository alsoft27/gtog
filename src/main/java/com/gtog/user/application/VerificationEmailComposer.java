package com.gtog.user.application;

import java.net.URI;
import com.gtog.user.domain.model.AuthEmail;

public final class VerificationEmailComposer {
	private final String base;
	private final String path;
	public VerificationEmailComposer(String base, String path) {
		URI uri;
		try { uri = URI.create(base); } catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Invalid authentication link configuration"); }
		if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
				|| uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
				|| !path.startsWith("/") || path.startsWith("//") || path.contains("?") || path.contains("#")) {
			throw new IllegalArgumentException("Invalid authentication link configuration");
		}
		this.base = base.replaceAll("/+$", ""); this.path = path;
	}
	public AuthEmail compose(String recipient, String token) {
		return new AuthEmail(recipient, "Verifica tu correo en gtog", "Verifica tu correo desde este enlace:\n" + base + path + "#token=" + token
				+ "\n\nSi no lo has pedido, ignóralo.\n");
	}
}
