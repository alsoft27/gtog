package com.gtog.event.domain.model;

public record GuestAvailabilityResponse(String eventId, String guestToken, GuestAvailability availability) {
	@Override
	public String toString() { return "GuestAvailabilityResponse[redacted]"; }
}
