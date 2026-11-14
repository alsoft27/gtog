package com.gtog.event.infrastructure.out.persistence;

import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.gtog.event.domain.model.DuplicateGuestTokenException;
import com.gtog.event.domain.model.EventConcurrentModificationException;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.port.out.EventGuestRepositoryPort;
import com.mongodb.client.result.UpdateResult;

@Component
public class EventGuestRepositoryAdapter implements EventGuestRepositoryPort {

	private final MongoTemplate mongoTemplate;
	private final GuestMapper mapper;

	public EventGuestRepositoryAdapter(MongoTemplate mongoTemplate, GuestMapper mapper) {
		this.mongoTemplate = mongoTemplate;
		this.mapper = mapper;
	}

	@Override
	public void addGuest(String eventId, String hostId, Long expectedVersion, Guest guest) {
		Query query = writableEvent(eventId, hostId, expectedVersion);
		// Un indice multiclave unico no evita repetidos dentro del mismo array.
		query.addCriteria(Criteria.where("guests.token").ne(guest.getToken()));
		try {
			checkUpdated(mongoTemplate.updateFirst(query,
					new Update().push("guests", mapper.toDocument(guest)).inc("version", 1), EventDocument.class));
		} catch (DuplicateKeyException e) {
			// El unico indice unico de events, aparte de _id, es guests.token. No se propaga el token del error.
			throw new DuplicateGuestTokenException();
		}
	}

	@Override
	public void updateGuest(String eventId, String hostId, Long expectedVersion, Guest guest) {
		Query query = writableEvent(eventId, hostId, expectedVersion);
		query.addCriteria(Criteria.where("guests.id").is(guest.getId()));
		Update update = new Update().set("guests.$.name", guest.getName()).set("guests.$.email", guest.getEmail())
				.set("guests.$.phoneE164", guest.getPhoneE164()).inc("version", 1);
		checkUpdated(mongoTemplate.updateFirst(query, update, EventDocument.class));
	}

	@Override
	public void removeGuest(String eventId, String hostId, Long expectedVersion, String guestId) {
		Query query = writableEvent(eventId, hostId, expectedVersion);
		query.addCriteria(Criteria.where("guests.id").is(guestId));
		checkUpdated(mongoTemplate.updateFirst(query,
				new Update().pull("guests", new Document("id", guestId)).inc("version", 1), EventDocument.class));
	}

	private Query writableEvent(String eventId, String hostId, Long expectedVersion) {
		return Query.query(Criteria.where("id").is(eventId).and("hostId").is(hostId)
				.and("version").is(expectedVersion).and("status").in("DRAFT", "PUBLISHED"));
	}

	private void checkUpdated(UpdateResult result) {
		if (result.getMatchedCount() != 1) {
			throw new EventConcurrentModificationException();
		}
	}
}
