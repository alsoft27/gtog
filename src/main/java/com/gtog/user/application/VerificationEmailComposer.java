package com.gtog.user.application;

import com.gtog.user.domain.model.AuthEmail;

public final class VerificationEmailComposer {
	private final AuthEmailLink link;
	public VerificationEmailComposer(String base, String path) {
		this.link = new AuthEmailLink(base, path);
	}
	public AuthEmail compose(String recipient, String token) {
		return new AuthEmail(recipient, "Verifica tu correo en gtog", "Verifica tu correo desde este enlace:\n" + link.build(token)
				+ "\n\nSi no lo has pedido, ignóralo.\n");
	}
}
