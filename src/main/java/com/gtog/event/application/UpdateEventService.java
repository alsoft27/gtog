package com.gtog.event.application;

import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.port.in.UpdateEventCommand;
import com.gtog.event.domain.port.in.UpdateEventUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;

@Service
public class UpdateEventService implements UpdateEventUseCase {

	private final EventRepositoryPort eventRepositoryPort;

	public UpdateEventService(EventRepositoryPort eventRepositoryPort) {
		this.eventRepositoryPort = eventRepositoryPort;
	}

	@Override
	public Event update(UpdateEventCommand command) {
		Event event = eventRepositoryPort.findById(command.eventId())
				.orElseThrow(() -> new EventNotFoundException(command.eventId()));
		if (!event.getHostId().equals(command.hostId())) {
			throw new EventNotFoundException(command.eventId());
		}
		event.edit(command.edit());
		return eventRepositoryPort.save(event);
	}
}
