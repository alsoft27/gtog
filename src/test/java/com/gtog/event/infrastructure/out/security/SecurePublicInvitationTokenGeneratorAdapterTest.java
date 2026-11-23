package com.gtog.event.infrastructure.out.security;

import java.util.Base64;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SecurePublicInvitationTokenGeneratorAdapterTest {
	@Test void generatesIndependent32ByteCredentialsWithoutPadding() {
		var generator = new SecurePublicInvitationTokenGeneratorAdapter();
		var guestGenerator = new SecureGuestTokenGeneratorAdapter();
		var tokens = new HashSet<String>();
		for (int i = 0; i < 100; i++) {
			String token = generator.generate();
			assertThat(token).matches("[A-Za-z0-9_-]{43}");
			assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
			assertThat(tokens.add(token)).isTrue(); assertThat(tokens.add(guestGenerator.generate())).isTrue();
		}
	}
}
