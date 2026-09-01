package com.gtog.event.application;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.port.in.CancelEventUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;

@Service
public class CancelEventService implements CancelEventUseCase {

	private final EventRepositoryPort eventRepositoryPort;

	public CancelEventService(EventRepositoryPort eventRepositoryPort) {
		this.eventRepositoryPort = eventRepositoryPort;
	}

	@Override
	public Event cancel(String eventId, String reason, Instant now) {
		Event event = eventRepositoryPort.findById(eventId)
				.orElseThrow(() -> new EventNotFoundException(eventId));
		event.cancel(reason, now);
		return eventRepositoryPort.save(event);
	}
}
