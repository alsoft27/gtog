package com.gtog.event.domain.port.out;

import java.time.Instant;
import java.util.Optional;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.Guest;

// Cada cambio parcial incrementa version, sin comparar una version anterior del documento completo.
public interface EventGuestRepositoryPort {

	Optional<Event> markGuestOpened(String eventId, String token, Instant now);

	// Wrapper de un elemento: mantiene la firma y los conflictos anteriores de los tests individuales.
	void addGuest(String eventId, String hostId, Long expectedVersion, Guest guest);
	void addGuests(String eventId, String hostId, java.util.List<Guest> guests);

	// expectedVersion se conserva por los tests existentes; ninguna escritura lo usa como condicion.
	void updateGuest(String eventId, String hostId, Long expectedVersion, Guest guest);

	void removeGuest(String eventId, String hostId, Long expectedVersion, String guestId);
}
