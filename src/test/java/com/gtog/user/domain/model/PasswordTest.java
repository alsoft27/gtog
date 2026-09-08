package com.gtog.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordTest {

	private static final com.gtog.user.domain.port.out.PasswordHasherPort FAKE_HASHER =
			new com.gtog.user.domain.port.out.PasswordHasherPort() {
				@Override public String hash(String raw) { return "hashed:" + raw; }
				@Override public boolean matches(String raw, String hash) { return hash.equals("hashed:" + raw); }
			};

	@Test
	void rejectsNullPassword() {
		assertThatThrownBy(() -> Password.of(null, FAKE_HASHER))
				.isInstanceOf(PasswordTooShortException.class);
	}

	@Test
	void rejectsPasswordShorterThanMinimum() {
		assertThatThrownBy(() -> Password.of("short1", FAKE_HASHER))
				.isInstanceOf(PasswordTooShortException.class);
	}

	@Test
	void rejectsPasswordAtBoundary() {
		assertThatThrownBy(() -> Password.of("1234567", FAKE_HASHER))
				.isInstanceOf(PasswordTooShortException.class);
	}

	@Test
	void acceptsPasswordAtMinimumLength() {
		Password password = Password.of("12345678", FAKE_HASHER);
		assertThat(password.hash()).isEqualTo("hashed:12345678");
	}

	@Test
	void delegatesHashingToPort() {
		Password password = Password.of("validpassword", FAKE_HASHER);
		assertThat(password.hash()).isEqualTo("hashed:validpassword");
	}

	@Test
	void fromHashWrapsWithoutValidation() {
		Password password = Password.fromHash("any-stored-hash");
		assertThat(password.hash()).isEqualTo("any-stored-hash");
	}

	@Test
	void fromHashAcceptsShortHash() {
		// La rehidratación no revalida la longitud del texto en claro original
		Password password = Password.fromHash("x");
		assertThat(password.hash()).isEqualTo("x");
	}
}
