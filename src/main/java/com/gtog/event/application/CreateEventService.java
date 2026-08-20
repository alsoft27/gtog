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
		Event event = Event.builder()
				.hostId(command.hostId())
				.title(command.title())
				.description(command.description())
				.startsAt(command.startsAt())
				.endsAt(command.endsAt())
				.timeZone(command.timeZone())
				.modality(command.modality())
				.responseOptions(command.responseOptions())
				.allowComment(command.allowComment())
				.allowResponseChange(command.allowResponseChange())
				.responseDeadline(command.responseDeadline())
				.venue(command.venue())
				.onlineAccess(command.onlineAccess())
				.build();
		return eventRepositoryPort.save(event);
	}
}
