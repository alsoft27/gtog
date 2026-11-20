package com.gtog.user.infrastructure.out.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import com.gtog.user.domain.model.AuthToken;
import com.gtog.user.domain.model.AuthTokenPurpose;
import com.gtog.user.domain.port.out.AuthTokenRepositoryPort;

@Component
public class AuthTokenRepositoryAdapter implements AuthTokenRepositoryPort {
	private final MongoTemplate mongo;
	public AuthTokenRepositoryAdapter(MongoTemplate mongo) { this.mongo = mongo; }
	@Override public void replace(AuthToken token) {
		mongo.upsert(Query.query(Criteria.where("userId").is(token.userId()).and("purpose").is(token.purpose().name())),
				new Update().set("hash", token.hash()).set("expiresAt", token.expiresAt()), AuthTokenDocument.class);
	}
	@Override public Optional<AuthToken> consume(String hash, AuthTokenPurpose purpose, Instant now) {
		var token = mongo.findAndRemove(Query.query(Criteria.where("hash").is(hash).and("purpose").is(purpose.name()).and("expiresAt").gt(now)), AuthTokenDocument.class);
		return Optional.ofNullable(token).map(value -> new AuthToken(value.userId(), purpose, value.hash(), value.expiresAt()));
	}
	@Override public void deleteByUserId(String userId) {
		mongo.remove(Query.query(Criteria.where("userId").is(userId)), AuthTokenDocument.class);
	}
}
