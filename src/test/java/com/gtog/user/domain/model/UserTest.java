package com.gtog.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserTest {

	private static final Password VALID_PASSWORD = Password.fromHash("some-hash");

	@Test
	void registersUserWithValidFields() {
		User user = User.register("Ana García", "Ana@Example.COM", VALID_PASSWORD, "Europe/Madrid");

		assertThat(user.getId()).isNotBlank();
		assertThat(user.getName()).isEqualTo("Ana García");
		assertThat(user.getEmail()).isEqualTo("ana@example.com");
		assertThat(user.getPassword()).isEqualTo(VALID_PASSWORD);
		assertThat(user.getTimeZone()).isEqualTo("Europe/Madrid");
	}

	@Test
	void normalizesEmailToLowercase() {
		User user = User.register("Test", "TEST@EXAMPLE.COM", VALID_PASSWORD, "UTC");
		assertThat(user.getEmail()).isEqualTo("test@example.com");
	}

	@Test
	void stripsWhitespaceFromEmail() {
		User user = User.register("Test", "  user@example.com  ", VALID_PASSWORD, "UTC");
		assertThat(user.getEmail()).isEqualTo("user@example.com");
	}

	@Test
	void generatesDifferentIdForEachUser() {
		User user1 = User.register("Alice", "alice@example.com", VALID_PASSWORD, "UTC");
		User user2 = User.register("Bob", "bob@example.com", VALID_PASSWORD, "UTC");
		assertThat(user1.getId()).isNotEqualTo(user2.getId());
	}

	@Test
	void rejectsNullName() {
		assertThatThrownBy(() -> User.register(null, "user@example.com", VALID_PASSWORD, "UTC"))
				.isInstanceOf(BlankUserNameException.class);
	}

	@Test
	void rejectsBlankName() {
		assertThatThrownBy(() -> User.register("   ", "user@example.com", VALID_PASSWORD, "UTC"))
				.isInstanceOf(BlankUserNameException.class);
	}

	@Test
	void rejectsNullEmail() {
		assertThatThrownBy(() -> User.register("Ana", null, VALID_PASSWORD, "UTC"))
				.isInstanceOf(InvalidEmailException.class);
	}

	@Test
	void rejectsEmailWithoutAtSign() {
		assertThatThrownBy(() -> User.register("Ana", "notanemail", VALID_PASSWORD, "UTC"))
				.isInstanceOf(InvalidEmailException.class);
	}

	@Test
	void rejectsEmailWithoutDomain() {
		assertThatThrownBy(() -> User.register("Ana", "user@", VALID_PASSWORD, "UTC"))
				.isInstanceOf(InvalidEmailException.class);
	}

	@Test
	void rejectsEmailWithoutTld() {
		assertThatThrownBy(() -> User.register("Ana", "user@example", VALID_PASSWORD, "UTC"))
				.isInstanceOf(InvalidEmailException.class);
	}

	@Test
	void rejectsNullTimeZone() {
		assertThatThrownBy(() -> User.register("Ana", "user@example.com", VALID_PASSWORD, null))
				.isInstanceOf(BlankTimeZoneException.class);
	}

	@Test
	void rejectsBlankTimeZone() {
		assertThatThrownBy(() -> User.register("Ana", "user@example.com", VALID_PASSWORD, "  "))
				.isInstanceOf(BlankTimeZoneException.class);
	}

	@Test
	void reconstitutePreservesAllFields() {
		Password password = Password.fromHash("stored-hash");
		User user = User.reconstitute("fixed-id", "Bob", "bob@example.com", password, "America/New_York");

		assertThat(user.getId()).isEqualTo("fixed-id");
		assertThat(user.getName()).isEqualTo("Bob");
		assertThat(user.getEmail()).isEqualTo("bob@example.com");
		assertThat(user.getPassword().hash()).isEqualTo("stored-hash");
		assertThat(user.getTimeZone()).isEqualTo("America/New_York");
	}

	@Test
	void reconstituteDoesNotRevalidate() {
		// reconstitute no valida: si los datos vinieron de Mongo, ya fueron validados al crear
		User user = User.reconstitute("id", "", "not-an-email", Password.fromHash("h"), "");
		assertThat(user.getEmail()).isEqualTo("not-an-email");
	}
}
