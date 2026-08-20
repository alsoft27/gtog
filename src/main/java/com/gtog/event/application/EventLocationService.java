package com.gtog.event.application;

import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.port.in.ReplaceOnlineAccessCommand;
import com.gtog.event.domain.port.in.ReplaceOnlineAccessUseCase;
import com.gtog.event.domain.port.in.ReplaceVenueCommand;
import com.gtog.event.domain.port.in.ReplaceVenueUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;

@Service
public class EventLocationService implements ReplaceVenueUseCase, ReplaceOnlineAccessUseCase {

	private final EventRepositoryPort eventRepositoryPort;

	public EventLocationService(EventRepositoryPort eventRepositoryPort) {
		this.eventRepositoryPort = eventRepositoryPort;
	}

	@Override
	public Event replaceVenue(ReplaceVenueCommand command) {
		Event event = eventRepositoryPort.findById(command.eventId())
				.orElseThrow(() -> new EventNotFoundException(command.eventId()));
		event.replaceVenue(command.venue());
		return eventRepositoryPort.save(event);
	}

	@Override
	public Event replaceOnlineAccess(ReplaceOnlineAccessCommand command) {
		Event event = eventRepositoryPort.findById(command.eventId())
				.orElseThrow(() -> new EventNotFoundException(command.eventId()));
		event.replaceOnlineAccess(command.onlineAccess());
		return eventRepositoryPort.save(event);
	}
}
