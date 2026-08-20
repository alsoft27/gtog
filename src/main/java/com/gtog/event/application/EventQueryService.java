package com.gtog.event.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.port.in.GetEventByIdUseCase;
import com.gtog.event.domain.port.in.ListEventsByHostUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;

@Service
public class EventQueryService implements GetEventByIdUseCase, ListEventsByHostUseCase {

	private final EventRepositoryPort eventRepositoryPort;

	public EventQueryService(EventRepositoryPort eventRepositoryPort) {
		this.eventRepositoryPort = eventRepositoryPort;
	}

	@Override
	public Event getEventById(String eventId) {
		return eventRepositoryPort.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
	}

	@Override
	public List<Event> listEventsByHost(String hostId) {
		return eventRepositoryPort.findByHostId(hostId);
	}
}
