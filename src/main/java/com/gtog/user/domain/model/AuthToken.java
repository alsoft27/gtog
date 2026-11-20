package com.gtog.user.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

public record AuthToken(String userId, AuthTokenPurpose purpose, String hash, Instant expiresAt) {
	public static String hash(String raw) {
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
		catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable"); }
	}
	public boolean isValidAt(Instant now) { return expiresAt.isAfter(now); }
	@Override public String toString() { return "AuthToken[redacted]"; }
}
