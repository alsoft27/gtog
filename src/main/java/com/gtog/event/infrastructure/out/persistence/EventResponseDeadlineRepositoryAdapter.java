package com.gtog.event.infrastructure.out.persistence;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.port.out.EventResponseDeadlineRepositoryPort;

@Component
public class EventResponseDeadlineRepositoryAdapter implements EventResponseDeadlineRepositoryPort {

	private final MongoTemplate mongoTemplate;
	private final EventMapper mapper;

	public EventResponseDeadlineRepositoryAdapter(MongoTemplate mongoTemplate, EventMapper mapper) {
		this.mongoTemplate = mongoTemplate;
		this.mapper = mapper;
	}

	@Override
	public Optional<Event> updateDeadline(String eventId, String hostId, LocalDateTime responseDeadline) {
		Query query = Query.query(Criteria.where("id").is(eventId).and("hostId").is(hostId)
				.and("status").in("DRAFT", "PUBLISHED"));
		// Sin version esperada: una primera apertura concurrente no debe bloquear el cambio del plazo.
		Update update = new Update().set("responseDeadline", responseDeadline).inc("version", 1);
		EventDocument updated = mongoTemplate.findAndModify(query, update,
				FindAndModifyOptions.options().returnNew(true), EventDocument.class);
		return Optional.ofNullable(updated).map(mapper::toDomain);
	}
}
