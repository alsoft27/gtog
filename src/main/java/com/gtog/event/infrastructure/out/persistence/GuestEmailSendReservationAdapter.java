package com.gtog.event.infrastructure.out.persistence;

import java.time.Instant;
import java.util.Objects;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.out.GuestEmailSendReservationPort;

@Component
public class GuestEmailSendReservationAdapter implements GuestEmailSendReservationPort {
	private final MongoTemplate mongo;
	private final GuestMapper mapper;
	public GuestEmailSendReservationAdapter(MongoTemplate mongo, GuestMapper mapper) { this.mongo = mongo; this.mapper = mapper; }

	@Override
	public GuestEmailReservationResult reserve(String eventId, String hostId, String guestId, String email, Instant now, EmailSendPolicy policy) {
		Criteria guest = Criteria.where("id").is(guestId).and("email").is(email).andOperator(
				new Criteria().orOperator(Criteria.where("emailSendCount").is(null), Criteria.where("emailSendCount").lt(policy.maxSendsPerGuest())),
				new Criteria().orOperator(Criteria.where("lastEmailSentAt").is(null), Criteria.where("lastEmailSentAt").lte(now.minus(policy.minInterval()))));
		Query query = Query.query(Criteria.where("id").is(eventId).and("hostId").is(hostId).and("status").is("PUBLISHED")
				.and("guests").elemMatch(guest));
		// El documento anterior permite devolver un comprobante exacto sin leer antes de reservar.
		EventDocument before = mongo.findAndModify(query, new Update().inc("guests.$.emailSendCount", 1)
				.set("guests.$.lastEmailSentAt", now).inc("version", 1), FindAndModifyOptions.options(), EventDocument.class);
		if (before != null) {
			Guest previous = before.getGuests().stream().filter(g -> guestId.equals(g.id())).map(mapper::toDomain).findFirst().orElseThrow();
			return new GuestEmailReservationResult(new GuestEmailReservation(eventId, hostId, guestId, email,
					previous.getEmailSendCount(), previous.getLastEmailSentAt(), now.truncatedTo(java.time.temporal.ChronoUnit.MILLIS), before.getVersion() == null ? 1 : before.getVersion() + 1), null);
		}
		EventDocument current = mongo.findById(eventId, EventDocument.class);
		if (current != null && hostId.equals(current.getHostId()) && "PUBLISHED".equals(current.getStatus())) {
			Guest currentGuest = current.getGuests().stream().filter(g -> guestId.equals(g.id())).map(mapper::toDomain).findFirst().orElse(null);
			if (currentGuest != null && Objects.equals(email, currentGuest.getEmail())) {
				EmailSendSkipReason reason = policy.guestLimitReason(currentGuest, now);
				if (reason != null) { return new GuestEmailReservationResult(null, reason); }
			}
		}
		throw new EventConcurrentModificationException();
	}

	private Criteria matchingGuest(GuestEmailReservation reservation) {
		return Criteria.where("id").is(reservation.guestId()).and("email").is(reservation.email())
				.and("emailSendCount").is(reservation.previousCount() + 1).and("lastEmailSentAt").is(reservation.reservedAt());
	}

	private Criteria matchingEvent(GuestEmailReservation reservation) {
		return Criteria.where("id").is(reservation.eventId()).and("hostId").is(reservation.hostId());
	}

	@Override
	public boolean release(GuestEmailReservation reservation) {
		// La version actua como vallado: una apertura o edicion posterior hace fallar la compensacion de forma conservadora.
		Query query = Query.query(matchingEvent(reservation).and("version").is(reservation.version())
				.and("guests").elemMatch(matchingGuest(reservation)));
		return mongo.updateFirst(query, new Update().set("guests.$.emailSendCount", reservation.previousCount())
				.set("guests.$.lastEmailSentAt", reservation.previousSentAt()).inc("version", 1), EventDocument.class).getMatchedCount() == 1;
	}

	@Override
	public boolean recordSent(GuestEmailReservation reservation, Instant now) {
		Criteria first = matchingGuest(reservation).and("invitationStatus").is("NOT_SENT").and("sentAt").is(null);
		Update update = new Update().set("guests.$.invitationStatus", "SENT").set("guests.$.deliveryChannel", "EMAIL").inc("version", 1);
		if (mongo.updateFirst(Query.query(matchingEvent(reservation).and("guests").elemMatch(first)),
				update.set("guests.$.sentAt", now), EventDocument.class).getMatchedCount() == 1) { return true; }
		Criteria previouslySent = matchingGuest(reservation).and("invitationStatus").is("NOT_SENT").and("sentAt").ne(null);
		if (mongo.updateFirst(Query.query(matchingEvent(reservation).and("guests").elemMatch(previouslySent)),
				new Update().set("guests.$.invitationStatus", "SENT").set("guests.$.deliveryChannel", "EMAIL").inc("version", 1), EventDocument.class).getMatchedCount() == 1) { return true; }
		EventDocument current = mongo.findById(reservation.eventId(), EventDocument.class);
		return current != null && current.getGuests().stream().anyMatch(g -> reservation.guestId().equals(g.id())
				&& Objects.equals(g.email(), reservation.email()) && Objects.equals(g.lastEmailSentAt(), reservation.reservedAt())
				&& ("OPENED".equals(g.invitationStatus()) || "SENT".equals(g.invitationStatus())));
	}
}
