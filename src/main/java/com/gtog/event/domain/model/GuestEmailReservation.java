package com.gtog.event.domain.model;

import java.time.Instant;

// Comprobante interno de compensacion. Nunca se serializa ni se escribe su correo en logs.
public record GuestEmailReservation(String eventId, String hostId, String guestId, String email,
		int previousCount, Instant previousSentAt, Instant reservedAt, long version) {
	@Override public String toString() { return "GuestEmailReservation[redacted]"; }
}
