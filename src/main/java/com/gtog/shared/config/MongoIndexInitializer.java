package com.gtog.shared.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import com.gtog.event.infrastructure.out.persistence.EventDocument;
import com.gtog.event.infrastructure.out.persistence.GuestResponseDocument;
import com.gtog.user.infrastructure.out.persistence.AuthTokenDocument;
import java.time.Duration;

// Los indices se crean aqui en el arranque, no con @Indexed en los documentos: asi se sabe que existe
// en produccion mirando una sola clase, sin tener que rastrear anotaciones repartidas por el modelo.
@Component
public class MongoIndexInitializer {

	private final MongoTemplate mongoTemplate;

	public MongoIndexInitializer(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void createIndexes() {
		mongoTemplate.indexOps(EventDocument.class).createIndex(new Index().on("hostId", Sort.Direction.ASC));
		mongoTemplate.indexOps(EventDocument.class).createIndex(new Index().on("guests.token", Sort.Direction.ASC)
				.unique().partial(PartialIndexFilter.of(Criteria.where("guests.token").type(2))));
		mongoTemplate.indexOps(EventDocument.class).createIndex(new Index().on("publicInvitation.token", Sort.Direction.ASC)
				.unique().partial(PartialIndexFilter.of(Criteria.where("publicInvitation.token").type(2)))
				.named("public_invitation_token"));
		mongoTemplate.indexOps(GuestResponseDocument.class).createIndex(new Index().on("eventId", Sort.Direction.ASC)
				.on("guestToken", Sort.Direction.ASC).unique().named("response_per_guest"));
		mongoTemplate.indexOps(GuestResponseDocument.class).createIndex(new Index().on("eventId", Sort.Direction.ASC));
		mongoTemplate.indexOps("users").createIndex(new Index().on("email", Sort.Direction.ASC).unique());
		mongoTemplate.indexOps(AuthTokenDocument.class).createIndex(new Index().on("hash", Sort.Direction.ASC).unique().named("auth_token_hash"));
		mongoTemplate.indexOps(AuthTokenDocument.class).createIndex(new Index().on("userId", Sort.Direction.ASC).on("purpose", Sort.Direction.ASC).unique().named("auth_token_per_purpose"));
		mongoTemplate.indexOps(AuthTokenDocument.class).createIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO).named("auth_token_expiry"));
	}
}
