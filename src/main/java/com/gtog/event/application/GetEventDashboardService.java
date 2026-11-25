package com.gtog.event.application;

import java.util.Set;
import org.springframework.stereotype.Service;
import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.DatePollSchedule;
import com.gtog.event.domain.model.EventAvailabilitySummary;
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
		var ids = Set.of(event.getId());
		var summary = event.getSchedule() instanceof DatePollSchedule poll
				? EventAvailabilitySummary.dashboard(event, poll, responses.findAvailabilityByEventIds(ids))
				: EventAttendanceSummary.calculate(event, responses.findByEventIds(ids));
		return summary.withPublicInvitation(event, clock.instant(), limits);
	}
}
