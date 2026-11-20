package com.gtog.user.domain.model;

import java.time.Instant;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class AuthTokenTest {
	@Test void sha256UsesTheStandardDigestWithoutKeepingThePlainToken() {
		assertThat(AuthToken.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
		assertThat(new AuthToken("user", AuthTokenPurpose.EMAIL_VERIFICATION, AuthToken.hash("abc"), Instant.EPOCH).toString()).doesNotContain("abc");
	}
	@ParameterizedTest @ValueSource(ints = { -1, 0, 1 })
	void expiryIsExclusive(int offset) {
		var expiry = Instant.parse("2027-01-01T12:00:00Z");
		assertThat(new AuthToken("user", AuthTokenPurpose.EMAIL_VERIFICATION, "hash", expiry).isValidAt(expiry.plusSeconds(offset))).isEqualTo(offset < 0);
	}
	@Test void authLimitsRejectNonPositiveValues() {
		assertThatThrownBy(() -> new AuthMailPolicy(Duration.ZERO, 5)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new AuthMailPolicy(Duration.ofMinutes(5), 0)).isInstanceOf(IllegalArgumentException.class);
	}
}
