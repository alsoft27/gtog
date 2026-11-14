package com.gtog.event.infrastructure.out.persistence;

import java.time.Instant;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("responses")
public record GuestResponseDocument(String eventId, String guestToken, String responseOptionId, String comment,
		String email, Instant answeredAt, Instant updatedAt) {
	@Override
	public String toString() { return "GuestResponseDocument[redacted]"; }
}
