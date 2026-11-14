package com.gtog.event.application;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventConcurrentModificationException;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.port.in.ChangeResponseDeadlineCommand;
import com.gtog.event.domain.port.in.ChangeResponseDeadlineUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.EventResponseDeadlineRepositoryPort;

@Service
public class ChangeResponseDeadlineService implements ChangeResponseDeadlineUseCase {

	private final EventRepositoryPort events;
	private final EventResponseDeadlineRepositoryPort deadlines;
	private final Clock clock;

	public ChangeResponseDeadlineService(EventRepositoryPort events, EventResponseDeadlineRepositoryPort deadlines, Clock clock) {
		this.events = events;
		this.deadlines = deadlines;
		this.clock = clock;
	}

	@Override
	public Event changeResponseDeadline(ChangeResponseDeadlineCommand command) {
		Event event = events.findById(command.eventId()).orElseThrow(() -> new EventNotFoundException(command.eventId()));
		if (!event.getHostId().equals(command.hostId())) {
			throw new EventNotFoundException(command.eventId());
		}
		event.changeResponseDeadline(command.responseDeadline(), clock.instant());
		return deadlines.updateDeadline(command.eventId(), command.hostId(), command.responseDeadline())
				.orElseThrow(EventConcurrentModificationException::new);
	}
}
