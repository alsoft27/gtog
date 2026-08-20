package com.gtog.event.application;

import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.port.in.ReplaceResponseOptionsCommand;
import com.gtog.event.domain.port.in.ReplaceResponseOptionsUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;

@Service
public class ReplaceResponseOptionsService implements ReplaceResponseOptionsUseCase {

	private final EventRepositoryPort eventRepositoryPort;

	public ReplaceResponseOptionsService(EventRepositoryPort eventRepositoryPort) {
		this.eventRepositoryPort = eventRepositoryPort;
	}

	@Override
	public Event replaceResponseOptions(ReplaceResponseOptionsCommand command) {
		Event event = eventRepositoryPort.findById(command.eventId())
				.orElseThrow(() -> new EventNotFoundException(command.eventId()));
		event.replaceResponseOptions(command.responseOptions(), command.allowComment(), command.allowResponseChange(),
				command.responseDeadline());
		return eventRepositoryPort.save(event);
	}
}
