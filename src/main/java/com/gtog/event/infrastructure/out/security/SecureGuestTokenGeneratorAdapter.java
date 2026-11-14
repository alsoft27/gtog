package com.gtog.event.infrastructure.out.security;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.gtog.event.domain.port.out.GuestTokenGeneratorPort;

@Component
public class SecureGuestTokenGeneratorAdapter implements GuestTokenGeneratorPort {

	private final SecureRandom random = new SecureRandom();

	@Override
	public String generate() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
