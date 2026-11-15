package com.gtog.event.application;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventAttendanceSummary;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.model.EventSummary;
import com.gtog.event.domain.model.GuestResponse;
import com.gtog.event.domain.port.in.GetEventByIdUseCase;
import com.gtog.event.domain.port.in.ListEventsByHostUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;

@Service
public class EventQueryService implements GetEventByIdUseCase, ListEventsByHostUseCase {

	private final EventRepositoryPort eventRepositoryPort;
	private final GuestResponseRepositoryPort responses;

	public EventQueryService(EventRepositoryPort eventRepositoryPort, GuestResponseRepositoryPort responses) {
		this.eventRepositoryPort = eventRepositoryPort;
		this.responses = responses;
	}

	@Override
	public Event getEventById(String hostId, String eventId) {
		Event event = eventRepositoryPort.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
		if (!event.getHostId().equals(hostId)) {
			throw new EventNotFoundException(eventId);
		}
		return event;
	}

	@Override
	public List<EventSummary> listEventsByHost(String hostId) {
		var events = eventRepositoryPort.findByHostId(hostId);
		if (events.isEmpty()) {
			return List.of();
		}
		var eventIds = events.stream().map(Event::getId).collect(Collectors.toSet());
		var responsesByEvent = responses.findByEventIds(eventIds).stream()
				.collect(Collectors.groupingBy(GuestResponse::eventId));
		return events.stream().map(event -> new EventSummary(event,
				EventAttendanceSummary.calculate(event, responsesByEvent.getOrDefault(event.getId(), List.of())).totals()))
				.toList();
	}
}
