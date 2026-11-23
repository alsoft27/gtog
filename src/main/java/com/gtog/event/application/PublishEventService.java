package com.gtog.event.application;

import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.port.in.PublishEventUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.PublicInvitationTokenGeneratorPort;

@Service
public class PublishEventService implements PublishEventUseCase {

	private final EventRepositoryPort eventRepositoryPort;
	private final PublicInvitationTokenGeneratorPort tokens;

	public PublishEventService(EventRepositoryPort eventRepositoryPort, PublicInvitationTokenGeneratorPort tokens) {
		this.eventRepositoryPort = eventRepositoryPort;
		this.tokens = tokens;
	}

	@Override
	public Event publish(String hostId, String eventId) {
		Event event = eventRepositoryPort.findById(eventId)
				.orElseThrow(() -> new EventNotFoundException(eventId));
		if (!event.getHostId().equals(hostId)) {
			throw new EventNotFoundException(eventId);
		}
		event.publish(tokens);
		return eventRepositoryPort.save(event);
	}
}
