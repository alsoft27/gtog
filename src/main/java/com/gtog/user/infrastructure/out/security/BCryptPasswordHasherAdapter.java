package com.gtog.user.infrastructure.out.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.gtog.user.domain.port.out.PasswordHasherPort;

@Component
public class BCryptPasswordHasherAdapter implements PasswordHasherPort {

	private final PasswordEncoder encoder;

	public BCryptPasswordHasherAdapter(PasswordEncoder encoder) {
		this.encoder = encoder;
	}

	@Override
	public String hash(String rawPassword) {
		return encoder.encode(rawPassword);
	}

	@Override
	public boolean matches(String rawPassword, String hash) {
		return encoder.matches(rawPassword, hash);
	}
}
