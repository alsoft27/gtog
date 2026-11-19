package com.gtog.event.infrastructure.out.persistence;

import java.time.Instant;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import com.gtog.event.domain.port.out.GuestWhatsAppConfirmationPort;

@Component
public class GuestWhatsAppConfirmationAdapter implements GuestWhatsAppConfirmationPort {
	private final MongoTemplate mongo;
	public GuestWhatsAppConfirmationAdapter(MongoTemplate mongo) { this.mongo = mongo; }

	@Override public boolean confirm(String eventId, String hostId, String guestId, Instant now) {
		Criteria notSent = withPhone(guestId).and("invitationStatus").is("NOT_SENT");
		if (mongo.updateFirst(event(eventId, hostId, notSent), sent(now).set("guests.$.invitationStatus", "SENT"),
				EventDocument.class).getMatchedCount() == 1) { return true; }
		// Una apertura previa no impide registrar el primer envio; nunca se rebaja OPENED.
		Criteria openedWithoutSend = withPhone(guestId).and("invitationStatus").is("OPENED").and("sentAt").is(null);
		return mongo.updateFirst(event(eventId, hostId, openedWithoutSend), sent(now), EventDocument.class).getMatchedCount() == 1;
	}

	private Criteria withPhone(String guestId) {
		return Criteria.where("id").is(guestId).and("phoneE164").regex("\\S");
	}
	private Query event(String eventId, String hostId, Criteria guest) {
		return Query.query(Criteria.where("id").is(eventId).and("hostId").is(hostId).and("status").is("PUBLISHED")
				.and("guests").elemMatch(guest));
	}
	private Update sent(Instant now) {
		return new Update().set("guests.$.deliveryChannel", "WHATSAPP").set("guests.$.sentAt", now).inc("version", 1);
	}
}
