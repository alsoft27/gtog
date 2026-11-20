package com.gtog.user.infrastructure.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("auth_tokens")
public record AuthTokenDocument(@Id String id, String userId, String purpose, String hash, Instant expiresAt) {
	@Override public String toString() { return "AuthTokenDocument[redacted]"; }
}
