package com.gtog.event.domain.port.out;

import java.time.Instant;
import java.util.Optional;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.Guest;

// Cada cambio parcial incrementa version; las mutaciones del anfitrion comprueban la version esperada.
public interface EventGuestRepositoryPort {

	Optional<Event> markGuestOpened(String eventId, String token, Instant now);

	void addGuest(String eventId, String hostId, Long expectedVersion, Guest guest);

	void updateGuest(String eventId, String hostId, Long expectedVersion, Guest guest);

	void removeGuest(String eventId, String hostId, Long expectedVersion, String guestId);
}
