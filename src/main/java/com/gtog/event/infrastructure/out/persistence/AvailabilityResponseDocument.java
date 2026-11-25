package com.gtog.event.infrastructure.out.persistence;

import org.springframework.data.mongodb.core.mapping.Document;
import com.gtog.event.domain.model.GuestAvailability;

@Document("responses")
record AvailabilityResponseDocument(String eventId, String guestToken, GuestAvailability availability) {
	@Override
	public String toString() { return "AvailabilityResponseDocument[redacted]"; }
}
