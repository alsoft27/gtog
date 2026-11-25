package com.gtog.user.infrastructure.out.persistence;

import java.util.Optional;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.gtog.event.domain.port.out.PollNoticeRecipientPort;

@Component
public class PollNoticeRecipientAdapter implements PollNoticeRecipientPort {
	private final MongoTemplate mongo;

	public PollNoticeRecipientAdapter(MongoTemplate mongo) { this.mongo = mongo; }

	@Override
	public Optional<String> findVerifiedEmail(String hostId) {
		var query = Query.query(Criteria.where("id").is(hostId).and("emailVerified").is(true));
		query.fields().include("email");
		return Optional.ofNullable(mongo.findOne(query, UserDocument.class)).map(UserDocument::getEmail);
	}
}
