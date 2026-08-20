package com.gtog.event.infrastructure.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.port.out.EventRepositoryPort;

@Component
public class EventRepositoryAdapter implements EventRepositoryPort {

	private final EventMongoRepository eventMongoRepository;
	private final EventMapper eventMapper;

	public EventRepositoryAdapter(EventMongoRepository eventMongoRepository, EventMapper eventMapper) {
		this.eventMongoRepository = eventMongoRepository;
		this.eventMapper = eventMapper;
	}

	@Override
	public Event save(Event event) {
		EventDocument saved = eventMongoRepository.save(eventMapper.toDocument(event));
		return eventMapper.toDomain(saved);
	}

	@Override
	public Optional<Event> findById(String id) {
		return eventMongoRepository.findById(id).map(eventMapper::toDomain);
	}

	@Override
	public List<Event> findByHostId(String hostId) {
		return eventMongoRepository.findByHostId(hostId).stream().map(eventMapper::toDomain).toList();
	}
}
