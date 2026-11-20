package com.gtog.user.application;

import com.gtog.user.domain.model.AuthEmail;

public final class PasswordResetEmailComposer {
	private final AuthEmailLink link;
	public PasswordResetEmailComposer(String base, String path) { this.link = new AuthEmailLink(base, path); }
	public AuthEmail compose(String recipient, String token) {
		return new AuthEmail(recipient, "Restablece tu contraseña en gtog", "Restablece tu contraseña desde este enlace:\n" + link.build(token)
				+ "\n\nSi no lo has pedido, ignóralo.\n");
	}
}
