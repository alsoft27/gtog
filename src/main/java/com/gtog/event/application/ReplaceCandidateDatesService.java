package com.gtog.event.application;

import org.springframework.stereotype.Service;
import com.gtog.event.domain.model.DatePollLimits;
import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.port.in.ReplaceCandidateDatesCommand;
import com.gtog.event.domain.port.in.ReplaceCandidateDatesUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;

@Service
public class ReplaceCandidateDatesService implements ReplaceCandidateDatesUseCase {
	private final EventRepositoryPort events;
	private final DatePollLimits limits;
	public ReplaceCandidateDatesService(EventRepositoryPort events, DatePollLimits limits) { this.events = events; this.limits = limits; }
	@Override public Event replaceCandidateDates(ReplaceCandidateDatesCommand command) {
		Event event = events.findById(command.eventId()).orElseThrow(() -> new EventNotFoundException(command.eventId()));
		if (!event.getHostId().equals(command.hostId())) { throw new EventNotFoundException(command.eventId()); }
		var before = event.getSchedule();
		event.replaceCandidateDates(command.candidateDates(), command.pollRevision(), limits);
		// Un reintento idéntico no toca Mongo ni consume una versión del agregado.
		return before.equals(event.getSchedule()) ? event : events.save(event);
	}
}
