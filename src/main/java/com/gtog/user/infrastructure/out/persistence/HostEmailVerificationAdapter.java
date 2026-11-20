package com.gtog.user.infrastructure.out.persistence;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import com.gtog.event.domain.port.out.HostEmailVerificationPort;

@Component
public class HostEmailVerificationAdapter implements HostEmailVerificationPort {
	private final MongoTemplate mongo;
	public HostEmailVerificationAdapter(MongoTemplate mongo) { this.mongo = mongo; }
	@Override public boolean isVerified(String hostId) {
		return mongo.exists(Query.query(Criteria.where("id").is(hostId).and("emailVerified").is(true)), UserDocument.class);
	}
}
