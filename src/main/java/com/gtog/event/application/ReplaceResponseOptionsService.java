package com.gtog.event.application;

import java.util.Set;
import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.port.in.ReplaceResponseOptionsCommand;
import com.gtog.event.domain.port.in.ReplaceResponseOptionsUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;

@Service
public class ReplaceResponseOptionsService implements ReplaceResponseOptionsUseCase {

	private final EventRepositoryPort eventRepositoryPort;
	private final GuestResponseRepositoryPort responses;

	public ReplaceResponseOptionsService(EventRepositoryPort eventRepositoryPort, GuestResponseRepositoryPort responses) {
		this.eventRepositoryPort = eventRepositoryPort;
		this.responses = responses;
	}

	@Override
	public Event replaceResponseOptions(ReplaceResponseOptionsCommand command) {
		Event event = eventRepositoryPort.findById(command.eventId())
				.orElseThrow(() -> new EventNotFoundException(command.eventId()));
		if (!event.getHostId().equals(command.hostId())) {
			throw new EventNotFoundException(command.eventId());
		}
		Set<String> answeredOptionIds = event.getStatus() == EventStatus.PUBLISHED
				? responses.findAnsweredOptionIds(event.getId()) : Set.of();
		event.replaceResponseOptions(command.responseOptions(), answeredOptionIds);
		return eventRepositoryPort.save(event);
	}
}
