package com.gtog.event.infrastructure.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import com.mongodb.MongoException;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.out.PublicInvitationRepositoryPort;

@Component
public class PublicInvitationRepositoryAdapter implements PublicInvitationRepositoryPort {
	private final MongoTemplate mongo;
	private final EventMapper events;
	private final GuestMapper guests;
	private final PublicJoinLimits limits;

	public PublicInvitationRepositoryAdapter(MongoTemplate mongo, EventMapper events, GuestMapper guests, PublicJoinLimits limits) {
		this.mongo = mongo; this.events = events; this.guests = guests; this.limits = limits;
	}

	@Override public Optional<Event> findByToken(String token) {
		if (token == null) { return Optional.empty(); }
		return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("publicInvitation.token").is(token)), EventDocument.class))
				.map(events::toDomain);
	}

	@Override public Optional<PublicInvitation> change(Event snapshot, PublicInvitation desired) {
		snapshot.ensurePublicInvitationEditable();
		String eventId = snapshot.getId(), hostId = snapshot.getHostId();
		// El estado exacto evita aplicar una intención DRAFT después de publicar.
		Document filter = new Document("_id", eventId).append("hostId", hostId).append("status", snapshot.getStatus().name());
		Document update = new Document("$inc", new Document("version", 1));
		if (snapshot.getStatus() == EventStatus.DRAFT) {
			if (desired.token() != null) { throw new InvalidPublicInvitationStateException(); }
			update.append("$set", new Document("publicInvitation", new Document("enabled", desired.enabled())));
		} else if (desired.enabled()) {
			if (desired.token() == null) { throw new InvalidPublicInvitationStateException(); }
			filter.append("publicInvitation.token", null).append("guests.token", new Document("$ne", desired.token()));
			update.append("$set", new Document("publicInvitation", new Document("enabled", true).append("token", desired.token())));
		} else {
			filter.append("publicInvitation.token", new Document("$type", "string"));
			update.append("$set", new Document("publicInvitation.enabled", false));
			update.append("$unset", new Document("publicInvitation.token", ""));
		}
		try {
			// Escritura explícita: version depende de este $inc, no del autoincremento de Spring Data.
			if (mongo.getCollection(mongo.getCollectionName(EventDocument.class)).updateOne(filter, update).getMatchedCount() == 1) {
				return desired.enabled() ? Optional.of(desired) : Optional.empty();
			}
		} catch (MongoException exception) { throw new PublicInvitationStorageException(); }
		EventDocument document = mongo.findById(eventId, EventDocument.class);
		if (document == null || !hostId.equals(document.getHostId())) { throw new EventNotFoundException(eventId); }
		Event current = events.toDomain(document); current.ensurePublicInvitationEditable();
		if (current.getStatus() != snapshot.getStatus() || current.isPublicInvitationEnabled() != desired.enabled()) {
			throw new EventConcurrentModificationException();
		}
		return current.isPublicInvitationEnabled() ? Optional.of(current.getPublicInvitation()) : Optional.empty();
	}

	@Override public PublicJoinWriteResult tryAddGuest(Event snapshot, String joinToken, Guest guest, Instant now) {
		// Los arrays ausentes cuentan como cero; un source ausente es HOST y no consume plazas públicas.
		Document array = new Document("$ifNull", List.of("$guests", List.of()));
		Document total = new Document("$size", array);
		Document publicCount = new Document("$size", new Document("$filter", new Document("input", array)
				.append("as", "guest").append("cond", new Document("$eq", List.of("$$guest.source", "PUBLIC_LINK")))));
		Document filter = new Document("_id", snapshot.getId()).append("publicInvitation.token", joinToken).append("status", "PUBLISHED")
				.append("guests.token", new Document("$ne", guest.getToken()))
				// Protege la configuración validada sin comparar version ni repetir la política temporal en Mongo.
				.append("endsAt", mongo.getConverter().convertToMongoType(snapshot.getEndsAt()))
				.append("timeZone", snapshot.getTimeZone())
				.append("responseDeadline", mongo.getConverter().convertToMongoType(snapshot.getResponseDeadline()));
		if (snapshot.getSchedule() instanceof DatePollSchedule poll) {
			filter.append("eventType", "DATE_POLL")
					.append("datePoll.pollRevision", poll.pollRevision() == 0 ? new Document("$in", java.util.Arrays.asList(0L, null)) : poll.pollRevision())
					.append("datePoll.selectedCandidateDateId", poll.selectedCandidateDateId());
		} else {
			filter.append("eventType", new Document("$in", java.util.Arrays.asList("FIXED_DATE", null)));
		}
		// Ambos topes se comprueban en el $push, para que dos altas no consuman la misma plaza.
		filter.append("$expr", new Document("$and", List.of(
				new Document("$lt", List.of(total, limits.maxPerEvent())),
				new Document("$lt", List.of(publicCount, limits.maxPublicGuests())))));
		// Todos los invitados participan: nunca filtrar la unicidad por source.
		if (guest.getEmail() != null) { filter.append("guests.email", new Document("$ne", guest.getEmail())); }
		if (guest.getPhoneE164() != null) { filter.append("guests.phoneE164", new Document("$ne", guest.getPhoneE164())); }
		Document embedded = new Document(); mongo.getConverter().write(guests.toDocument(guest), embedded);
		try {
			if (mongo.getCollection(mongo.getCollectionName(EventDocument.class)).updateOne(filter,
					new Document("$push", new Document("guests", embedded)).append("$inc", new Document("version", 1)))
					.getMatchedCount() == 1) { return PublicJoinWriteResult.CREATED; }
		} catch (MongoException exception) { throw new PublicInvitationStorageException(); }
		return classifyFailure(joinToken, guest, now);
	}

	private PublicJoinWriteResult classifyFailure(String joinToken, Guest guest, Instant now) {
		Event current = findByToken(joinToken).orElse(null);
		if (current == null || current.getStatus() == EventStatus.DRAFT) { return PublicJoinWriteResult.NOT_FOUND; }
		if (!current.responseAcceptancePolicy(now).canRespond(false)) { return PublicJoinWriteResult.JOIN_CLOSED; }
		if (current.publicJoinedCount() >= limits.maxPublicGuests()) { return PublicJoinWriteResult.PUBLIC_LINK_FULL; }
		if (current.getGuests().size() >= limits.maxPerEvent()) { return PublicJoinWriteResult.CAPACITY_EXCEEDED; }
		if (current.hasGuestContact(guest)) { return PublicJoinWriteResult.CONTACT_CONFLICT; }
		if (current.getGuests().stream().anyMatch(existing -> guest.getToken().equals(existing.getToken()))) {
			throw new DuplicateGuestTokenException();
		}
		// Cambió la configuración entre lectura y escritura: rechazar sin usar la política de una lectura obsoleta.
		return PublicJoinWriteResult.JOIN_CLOSED;
	}
}
