package com.gtog.event.domain.port.out;

import java.time.LocalDateTime;
import java.util.Optional;

import com.gtog.event.domain.model.Event;

public interface EventResponseDeadlineRepositoryPort {

	Optional<Event> updateDeadline(String eventId, String hostId, LocalDateTime responseDeadline);
}
