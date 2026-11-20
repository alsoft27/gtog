package com.gtog.user.infrastructure.out.security;

import java.util.Base64;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SecureAuthTokenGeneratorTest {
	@Test void tokensHaveThirtyTwoRandomBytesAndTheUrlSafeUnpaddedFormat() {
		var generator = new SecureAuthTokenGenerator(); var values = new HashSet<String>();
		for (int i = 0; i < 100; i++) {
			String token = generator.generate(); assertThat(token).matches("[A-Za-z0-9_-]{43}");
			assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32); values.add(token);
		}
		assertThat(values).hasSize(100);
	}
}
