package com.gtog.event.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.gtog.event.domain.model.Event;

public interface EventRepositoryPort {

	Event save(Event event);

	Optional<Event> findById(String id);

	Optional<Event> findByGuestToken(String token);

	List<Event> findByHostId(String hostId);
}
