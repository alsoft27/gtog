package com.gtog.event.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.DatePollSchedule;
import com.gtog.event.domain.model.EventAvailabilitySummary;
import com.gtog.event.domain.model.EventType;
import com.gtog.event.domain.model.GuestAvailabilityResponse;
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
		var fixedIds = idsOfType(events, EventType.FIXED_DATE);
		var pollIds = idsOfType(events, EventType.DATE_POLL);
		var attendanceByEvent = fixedIds.isEmpty() ? Map.<String, List<GuestResponse>>of()
				: responses.findByEventIds(fixedIds).stream().collect(Collectors.groupingBy(GuestResponse::eventId));
		var availabilityByEvent = pollIds.isEmpty() ? Map.<String, List<GuestAvailabilityResponse>>of()
				: responses.findAvailabilityByEventIds(pollIds).stream().collect(Collectors.groupingBy(GuestAvailabilityResponse::eventId));
		return events.stream().map(event -> {
			var totals = event.getSchedule() instanceof DatePollSchedule poll
					? EventAvailabilitySummary.totals(event, poll, availabilityByEvent.getOrDefault(event.getId(), List.of()))
					: EventAttendanceSummary.calculate(event, attendanceByEvent.getOrDefault(event.getId(), List.of())).totals();
			return new EventSummary(event, totals);
		}).toList();
	}

	private Set<String> idsOfType(List<Event> events, EventType type) {
		return events.stream().filter(event -> event.getEventType() == type).map(Event::getId).collect(Collectors.toSet());
	}
}
