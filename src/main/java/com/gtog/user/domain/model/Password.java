package com.gtog.user.domain.model;

import com.gtog.user.domain.port.out.PasswordHasherPort;

public final class Password {

	private static final int MIN_LENGTH = 8;

	private final String hash;

	private Password(String hash) {
		this.hash = hash;
	}

	/** Para usuarios nuevos: valida la longitud del texto en claro y hashea. */
	public static Password of(String raw, PasswordHasherPort hasher) {
		if (raw == null || raw.length() < MIN_LENGTH) {
			throw new PasswordTooShortException();
		}
		return new Password(hasher.hash(raw));
	}

	/** Para rehidratación desde persistencia: envuelve un hash ya almacenado sin revalidar. */
	public static Password fromHash(String hash) {
		return new Password(hash);
	}

	public String hash() {
		return hash;
	}
}
