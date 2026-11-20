package com.gtog.user.infrastructure.out.security;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;
import com.gtog.user.domain.port.out.AuthTokenGeneratorPort;

@Component
public class SecureAuthTokenGenerator implements AuthTokenGeneratorPort {
	private final SecureRandom random = new SecureRandom();
	@Override public String generate() {
		byte[] bytes = new byte[32]; random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
