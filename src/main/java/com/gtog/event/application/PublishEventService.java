package com.gtog.event.application;

import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.port.in.PublishEventUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;

@Service
public class PublishEventService implements PublishEventUseCase {

	private final EventRepositoryPort eventRepositoryPort;

	public PublishEventService(EventRepositoryPort eventRepositoryPort) {
		this.eventRepositoryPort = eventRepositoryPort;
	}

	@Override
	public Event publish(String eventId) {
		Event event = eventRepositoryPort.findById(eventId)
				.orElseThrow(() -> new EventNotFoundException(eventId));
		event.publish();
		return eventRepositoryPort.save(event);
	}
}
