package com.gtog.event.infrastructure.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import com.gtog.event.domain.model.GuestLimits;
import com.gtog.event.domain.model.GuestBatch;
import com.gtog.event.domain.model.GuestBatchRejectedException;
import com.gtog.event.domain.model.GuestCapacityExceededException;

import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.gtog.event.domain.model.DuplicateGuestTokenException;
import com.gtog.event.domain.model.EventConcurrentModificationException;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.model.GuestNotFoundException;
import com.gtog.event.domain.model.DuplicateGuestEmailException;
import com.gtog.event.domain.model.DuplicateGuestPhoneException;
import com.gtog.event.domain.port.out.EventGuestRepositoryPort;
import com.mongodb.client.result.UpdateResult;

@Component
public class EventGuestRepositoryAdapter implements EventGuestRepositoryPort {

	private final MongoTemplate mongoTemplate;
	private final GuestMapper mapper;
	private final EventMapper eventMapper;
	private final GuestLimits limits;

	public EventGuestRepositoryAdapter(MongoTemplate mongoTemplate, GuestMapper mapper, EventMapper eventMapper, GuestLimits limits) {
		this.mongoTemplate = mongoTemplate;
		this.mapper = mapper;
		this.eventMapper = eventMapper;
		this.limits = limits;
	}

	@Override
	public Optional<Event> markGuestOpened(String eventId, String token, Instant now) {
		Query query = Query.query(Criteria.where("id").is(eventId).and("status").is("PUBLISHED")
				.and("guests").elemMatch(Criteria.where("token").is(token)
						.and("invitationStatus").in("NOT_SENT", "SENT")));
		Update update = new Update().set("guests.$.invitationStatus", "OPENED")
				.set("guests.$.openedAt", now).inc("version", 1);
		EventDocument updated = mongoTemplate.findAndModify(query, update,
				FindAndModifyOptions.options().returnNew(true), EventDocument.class);
		return Optional.ofNullable(updated).map(eventMapper::toDomain);
	}

	@Override
	public void addGuest(String eventId, String hostId, Long expectedVersion, Guest guest) {
		addGuests(eventId, hostId, List.of(guest), true);
	}

	@Override public void addGuests(String eventId, String hostId, List<Guest> guests) {
		addGuests(eventId, hostId, guests, false);
	}

	private void addGuests(String eventId, String hostId, List<Guest> guests, boolean individual) {
		Query query = editableEvent(eventId, hostId);
		var emails = guests.stream().map(Guest::getEmail).filter(java.util.Objects::nonNull).toList();
		var phones = guests.stream().map(Guest::getPhoneE164).filter(java.util.Objects::nonNull).toList();
		query.addCriteria(Criteria.where("guests.email").nin(emails).and("guests.phoneE164").nin(phones)
				.and("guests.token").nin(guests.stream().map(Guest::getToken).toList()));
		// Comprobar plazas en el filtro: dos lotes no pueden consumir la misma capacidad.
		query.addCriteria(Criteria.where("$expr").is(new Document("$lte", List.of(
				new Document("$size", new Document("$ifNull", List.of("$guests", List.of()))), limits.maxPerEvent() - guests.size()))));
		try {
			var result = mongoTemplate.updateFirst(query, new Update().push("guests")
					.each(guests.stream().map(mapper::toDocument).toArray()).inc("version", 1), EventDocument.class);
			if (result.getMatchedCount() != 1) { classifyAdditionFailure(eventId, hostId, guests, individual); }
		} catch (DuplicateKeyException e) {
			// El unico indice unico de events, aparte de _id, es guests.token. No se propaga el token del error.
			throw new DuplicateGuestTokenException();
		}
	}

	private void classifyAdditionFailure(String eventId, String hostId, List<Guest> guests, boolean individual) {
		var document = mongoTemplate.findById(eventId, EventDocument.class);
		if (document == null || !hostId.equals(document.getHostId())) {
			if (individual) { throw new EventConcurrentModificationException(); }
			throw new EventNotFoundException(eventId);
		}
		Event current = eventMapper.toDomain(document);
		if (!individual) {
			current.ensureGuestsEditable();
			var errors = GuestBatch.existingErrors(current, guests);
			if (!errors.isEmpty()) { throw new GuestBatchRejectedException(errors, true); }
		}
		if (current.getGuests().size() > limits.maxPerEvent() - guests.size()) { throw new GuestCapacityExceededException(); }
		// El wrapper individual conserva el conflicto generico anterior para una carrera tardia.
		if (individual) { throw new EventConcurrentModificationException(); }
		if (current.getGuests().stream().anyMatch(old -> guests.stream().anyMatch(guest -> guest.getToken().equals(old.getToken())))) {
			throw new DuplicateGuestTokenException();
		}
		throw new EventConcurrentModificationException();
	}

	@Override
	public void updateGuest(String eventId, String hostId, Long expectedVersion, Guest guest) {
		Query query = editableEvent(eventId, hostId);
		query.addCriteria(Criteria.where("guests.id").is(guest.getId()));
		var contacts = new ArrayList<Criteria>();
		if (guest.getEmail() != null) { contacts.add(Criteria.where("email").is(guest.getEmail())); }
		if (guest.getPhoneE164() != null) { contacts.add(Criteria.where("phoneE164").is(guest.getPhoneE164())); }
		if (!contacts.isEmpty()) {
			query.addCriteria(Criteria.where("guests").not().elemMatch(Criteria.where("id").ne(guest.getId())
					.orOperator(contacts.toArray(Criteria[]::new))));
		}
		// La negacion del array exige arrayFilters, no el operador posicional simple $.
		Update update = new Update().set("guests.$[g].name", guest.getName()).set("guests.$[g].email", guest.getEmail())
				.set("guests.$[g].phoneE164", guest.getPhoneE164())
				.set("guests.$[reset].emailSendCount", 0).set("guests.$[reset].lastEmailSentAt", null)
				// Spring no traduce los campos de arrayFilters: el id embebido se guarda como _id.
				.filterArray(Criteria.where("g._id").is(guest.getId()))
				.filterArray(Criteria.where("reset._id").is(guest.getId()).and("reset.email").ne(guest.getEmail()))
				.inc("version", 1);
		// Reiniciar cuota solo si cambia el correo real en Mongo; no volcar contadores de la lectura anterior.
		UpdateResult result = mongoTemplate.updateFirst(query, update, EventDocument.class);
		if (result.getMatchedCount() != 1 || result.getModifiedCount() != 1) {
			classifyGuestWriteFailure(eventId, hostId, guest.getId(), guest);
		}
	}

	@Override
	public void removeGuest(String eventId, String hostId, Long expectedVersion, String guestId) {
		Query query = editableEvent(eventId, hostId);
		query.addCriteria(Criteria.where("guests.id").is(guestId));
		if (mongoTemplate.updateFirst(query, new Update().pull("guests", new Document("id", guestId)).inc("version", 1),
				EventDocument.class).getMatchedCount() != 1) {
			classifyGuestWriteFailure(eventId, hostId, guestId, null);
		}
	}

	private Query editableEvent(String eventId, String hostId) {
		return Query.query(Criteria.where("id").is(eventId).and("hostId").is(hostId).and("status").in("DRAFT", "PUBLISHED"));
	}

	private void classifyGuestWriteFailure(String eventId, String hostId, String guestId, Guest candidate) {
		EventDocument document = mongoTemplate.findById(eventId, EventDocument.class);
		if (document == null || !hostId.equals(document.getHostId())) { throw new EventNotFoundException(eventId); }
		Event current = eventMapper.toDomain(document);
		current.ensureGuestsEditable();
		current.getGuest(guestId);
		if (candidate != null) {
			for (Guest other : current.getGuests()) {
				if (other.getId().equals(guestId)) { continue; }
				if (candidate.getEmail() != null && candidate.getEmail().equals(other.getEmail())) { throw new DuplicateGuestEmailException(); }
				if (candidate.getPhoneE164() != null && candidate.getPhoneE164().equals(other.getPhoneE164())) { throw new DuplicateGuestPhoneException(); }
			}
		}
		throw new EventConcurrentModificationException();
	}

}
