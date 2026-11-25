package com.gtog.event.domain.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record GuestAvailabilityResponse(String eventId, String guestToken, GuestAvailability availability) {
	@Override
	public String toString() { return "GuestAvailabilityResponse[redacted]"; }
	public static Map<String, GuestAvailability> currentByToken(Event event, DatePollSchedule poll,
			List<GuestAvailabilityResponse> responses) {
		var current = new HashMap<String, GuestAvailability>();
		for (GuestAvailabilityResponse response : responses) {
			if (event.getId().equals(response.eventId()) && response.availability().pollRevision() == poll.pollRevision()) {
				current.put(response.guestToken(), response.availability());
			}
		}
		return current;
	}
}
