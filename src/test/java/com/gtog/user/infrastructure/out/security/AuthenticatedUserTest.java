package com.gtog.user.infrastructure.out.security;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedUserTest {
	@Test void theSameUserRemainsEqualWhenProfileOrPasswordChanges() {
		var first = new AuthenticatedUser("user-id", "Name", "first@example.com", "old-hash", "Europe/Madrid");
		var updated = new AuthenticatedUser("user-id", "Updated", "updated@example.com", "new-hash", "UTC");
		assertThat(first).isEqualTo(updated); assertThat(updated).isEqualTo(first);
		assertThat(first.hashCode()).isEqualTo(updated.hashCode());
		assertThat(new HashSet<>(List.of(first, updated))).hasSize(1);
	}
	@Test void differentUsersWithTheSameEmailAreDifferentRegistryKeys() {
		var first = new AuthenticatedUser("first-id", "Name", "same@example.com", "hash", "UTC");
		var second = new AuthenticatedUser("second-id", "Name", "same@example.com", "hash", "UTC");
		assertThat(first).isNotEqualTo(second);
		assertThat(new HashSet<>(List.of(first, second))).hasSize(2);
	}
	@Test void aPrincipalIsEqualToItselfButNotToNullOrAnotherType() {
		var principal = new AuthenticatedUser("user-id", "Name", "host@example.com", "hash", "UTC");
		assertThat(principal).isEqualTo(principal).isNotEqualTo(null).isNotEqualTo("user-id");
	}
}
