package com.gtog.user.infrastructure.out.persistence;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.stereotype.Component;
import com.gtog.user.domain.model.AuthMailPolicy;
import com.gtog.user.domain.port.out.AuthMailQuotaPort;

@Component
public class AuthMailQuotaAdapter implements AuthMailQuotaPort {
	private final MongoTemplate mongo;
	public AuthMailQuotaAdapter(MongoTemplate mongo) { this.mongo = mongo; }
	@Override public boolean reserveVerification(String userId, Instant now, AuthMailPolicy policy) {
		Document count = new Document("$ifNull", List.of("$authEmailSends", 0));
		Document expired = new Document("$or", List.of(isNull("$authEmailWindowStartedAt"),
				new Document("$lte", List.of("$authEmailWindowStartedAt", Date.from(now.minus(AuthMailPolicy.WINDOW))))));
		Document cooled = new Document("$or", List.of(isNull("$lastAuthEmailAt"),
				new Document("$lte", List.of("$lastAuthEmailAt", Date.from(now.minus(policy.cooldown()))))));
		Document available = new Document("$and", List.of(cooled,
				new Document("$or", List.of(expired, new Document("$lt", List.of(count, policy.maxPerDay()))))));
		var query = new BasicQuery(new Document("_id", userId).append("emailVerified", new Document("$ne", true)).append("$expr", available));
		Document fields = new Document("authEmailSends", new Document("$cond", List.of(expired, 1, new Document("$add", List.of(count, 1)))))
				.append("authEmailWindowStartedAt", new Document("$cond", List.of(expired, Date.from(now), "$authEmailWindowStartedAt")))
				.append("lastAuthEmailAt", Date.from(now));
		AggregationOperation stage = context -> new Document("$set", fields);
		return mongo.findAndModify(query, AggregationUpdate.from(List.of(stage)), FindAndModifyOptions.options().returnNew(true), UserDocument.class) != null;
	}
	private Document isNull(String field) {
		return new Document("$eq", Arrays.asList(new Document("$ifNull", Arrays.asList(field, null)), null));
	}
}
