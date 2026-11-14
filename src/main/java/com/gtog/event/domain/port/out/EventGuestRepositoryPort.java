package com.gtog.event.domain.port.out;

import com.gtog.event.domain.model.Guest;

// Escrituras parciales del agregado Event, comprobando e incrementando su version.
public interface EventGuestRepositoryPort {

	void addGuest(String eventId, String hostId, Long expectedVersion, Guest guest);

	void updateGuest(String eventId, String hostId, Long expectedVersion, Guest guest);

	void removeGuest(String eventId, String hostId, Long expectedVersion, String guestId);
}
