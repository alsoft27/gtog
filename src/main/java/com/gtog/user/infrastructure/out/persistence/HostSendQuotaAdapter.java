package com.gtog.user.infrastructure.out.persistence;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import com.gtog.event.domain.model.EmailSendPolicy;
import com.gtog.event.domain.model.HostSendQuota;
import com.gtog.event.domain.model.HostSendQuotaReservation;
import com.gtog.event.domain.port.out.HostSendQuotaPort;

@Component
public class HostSendQuotaAdapter implements HostSendQuotaPort {
	private final MongoTemplate mongo;
	public HostSendQuotaAdapter(MongoTemplate mongo) { this.mongo = mongo; }

	@Override
	public Optional<HostSendQuotaReservation> reserve(String hostId, Instant now, int limit) {
		Document count = new Document("$ifNull", List.of("$emailSendsInWindow", 0));
		Document expired = new Document("$or", List.of(
				new Document("$eq", Arrays.asList(new Document("$ifNull", Arrays.asList("$windowStartedAt", null)), null)),
				new Document("$lte", List.of("$windowStartedAt", Date.from(now.minus(EmailSendPolicy.HOST_WINDOW))))));
		Query query = new BasicQuery(new Document("_id", hostId).append("$expr",
				new Document("$or", List.of(expired, new Document("$lt", List.of(count, limit))))));
		Document fields = new Document("emailSendsInWindow", new Document("$cond", List.of(expired, 1, new Document("$add", List.of(count, 1)))))
				.append("windowStartedAt", new Document("$cond", List.of(expired, Date.from(now), "$windowStartedAt")));
		AggregationOperation stage = context -> new Document("$set", fields);
		UserDocument updated = mongo.findAndModify(query, AggregationUpdate.from(List.of(stage)),
				FindAndModifyOptions.options().returnNew(true), UserDocument.class);
		return Optional.ofNullable(updated).map(user -> new HostSendQuotaReservation(hostId, user.getWindowStartedAt()));
	}

	@Override
	public boolean release(HostSendQuotaReservation reservation) {
		Query query = Query.query(Criteria.where("id").is(reservation.hostId()).and("windowStartedAt").is(reservation.windowStartedAt())
				.and("emailSendsInWindow").gt(0));
		Document fields = new Document("emailSendsInWindow", new Document("$subtract", List.of("$emailSendsInWindow", 1)))
				.append("windowStartedAt", new Document("$cond", Arrays.asList(new Document("$eq", List.of("$emailSendsInWindow", 1)), null, "$windowStartedAt")));
		AggregationOperation stage = context -> new Document("$set", fields);
		return mongo.updateFirst(query, AggregationUpdate.from(List.of(stage)), UserDocument.class).getMatchedCount() == 1;
	}

	@Override
	public HostSendQuota read(String hostId, Instant now, int limit) {
		UserDocument user = mongo.findById(hostId, UserDocument.class);
		return HostSendQuota.from(user == null ? 0 : user.getEmailSendsInWindow(), user == null ? null : user.getWindowStartedAt(), limit, now);
	}
}
