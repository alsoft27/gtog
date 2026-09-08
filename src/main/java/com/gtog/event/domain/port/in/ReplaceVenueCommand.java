package com.gtog.event.domain.port.in;

import com.gtog.event.domain.model.Venue;

public record ReplaceVenueCommand(String hostId, String eventId, Venue venue) {
}
