package com.gtog.event.infrastructure.out.persistence;

import java.util.List;
import java.util.stream.Stream;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.EventType;
import com.gtog.event.domain.port.out.PollNoticeRepositoryPort;
import com.gtog.shared.StoredDataIntegrityException;

@Component
public class PollNoticeRepositoryAdapter implements PollNoticeRepositoryPort {
	private static final Logger log = LoggerFactory.getLogger(PollNoticeRepositoryAdapter.class);
	private static final String REVISION = "datePoll.pollRevision";
	private static final String CLAIMED = "datePoll.noticeClaimedRevision";
	private final MongoTemplate mongo;
	private final EventMapper mapper;

	public PollNoticeRepositoryAdapter(MongoTemplate mongo, EventMapper mapper) {
		this.mongo = mongo;
		this.mapper = mapper;
	}

	@Override
	public Stream<Event> findUnclaimedPublishedPolls() {
		var current = new Document("$ifNull", List.of("$" + REVISION, 0));
		var claimed = new Document("$ifNull", List.of("$" + CLAIMED, -1));
		var filter = new Document("eventType", EventType.DATE_POLL.name())
				.append("status", EventStatus.PUBLISHED.name())
				.append("$expr", new Document("$gt", List.of(current, claimed)));
		return mongo.stream(new BasicQuery(filter), EventDocument.class).flatMap(this::mapPoll);
	}

	private Stream<Event> mapPoll(EventDocument document) {
		try {
			return Stream.of(mapper.toDomain(document));
		} catch (StoredDataIntegrityException failure) {
			log.warn("POLL_NOTICE_INVALID_EVENT eventId={}", document.getId());
			return Stream.empty();
		}
	}

	@Override
	public boolean claim(String eventId, long eventVersion, long pollRevision) {
		var criteria = Criteria.where("id").is(eventId).and("eventType").is(EventType.DATE_POLL.name())
				.and("status").is(EventStatus.PUBLISHED.name()).and("version").is(eventVersion);
		if (pollRevision == 0) {
			criteria.andOperator(new Criteria().orOperator(Criteria.where(REVISION).is(0L), Criteria.where(REVISION).isNull()),
					unclaimed(pollRevision));
		} else {
			criteria.and(REVISION).is(pollRevision).andOperator(unclaimed(pollRevision));
		}
		// La versión protege el plazo, los invitados y el estado leídos; también protege la marca de un save obsoleto.
		var update = new Update().set(CLAIMED, pollRevision).inc("version", 1);
		return mongo.updateFirst(Query.query(criteria), update, EventDocument.class).getModifiedCount() == 1;
	}

	private Criteria unclaimed(long pollRevision) {
		return new Criteria().orOperator(Criteria.where(CLAIMED).isNull(), Criteria.where(CLAIMED).lt(pollRevision));
	}
}
