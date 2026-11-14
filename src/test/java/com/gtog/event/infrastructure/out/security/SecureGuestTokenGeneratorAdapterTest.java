package com.gtog.event.infrastructure.out.security;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureGuestTokenGeneratorAdapterTest {

	@Test
	void generatesUrlSafeUnpaddedTokensFrom32Bytes() {
		var generator = new SecureGuestTokenGeneratorAdapter();
		String token = generator.generate();
		assertThat(token).matches("[A-Za-z0-9_-]{43}");
		assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
		assertThat(generator.generate()).isNotEqualTo(token);
	}
}
