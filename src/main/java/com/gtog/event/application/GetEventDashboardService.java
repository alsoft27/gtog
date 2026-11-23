package com.gtog.event.application;

import java.util.Set;
import org.springframework.stereotype.Service;
import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventAttendanceSummary;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.port.in.GetEventDashboardUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;

@Service
public class GetEventDashboardService implements GetEventDashboardUseCase {
	private final EventRepositoryPort events;
	private final GuestResponseRepositoryPort responses;
	private final java.time.Clock clock;
	private final com.gtog.event.domain.model.PublicJoinLimits limits;

	public GetEventDashboardService(EventRepositoryPort events, GuestResponseRepositoryPort responses,
			java.time.Clock clock, com.gtog.event.domain.model.PublicJoinLimits limits) {
		this.events = events;
		this.responses = responses;
		this.clock = clock; this.limits = limits;
	}

	@Override
	public EventAttendanceSummary getDashboard(String hostId, String eventId) {
		Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
		if (!event.getHostId().equals(hostId)) { throw new EventNotFoundException(eventId); }
		return EventAttendanceSummary.calculate(event, responses.findByEventIds(Set.of(event.getId())))
				.withPublicInvitation(event, clock.instant(), limits);
	}
}
