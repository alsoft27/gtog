package com.gtog.event.application;

import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.port.in.CreateEventCommand;
import com.gtog.event.domain.port.in.CreateEventUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;

@Service
public class CreateEventService implements CreateEventUseCase {

	private final EventRepositoryPort eventRepositoryPort;

	public CreateEventService(EventRepositoryPort eventRepositoryPort) {
		this.eventRepositoryPort = eventRepositoryPort;
	}

	@Override
	public Event createEvent(CreateEventCommand command) {
		Event event = Event.create(command.hostId(), command.title(), command.description(), command.startsAt(),
				command.endsAt(), command.timeZone(), command.modality(), command.responseOptions(),
				command.allowComment(), command.allowResponseChange(), command.responseDeadline());
		return eventRepositoryPort.save(event);
	}
}
