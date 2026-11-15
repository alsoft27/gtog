package com.gtog.event.infrastructure.out.persistence;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import org.bson.BsonDocument;
import org.bson.Document;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import com.mongodb.MongoWriteException;
import com.mongodb.MongoCommandException;
import com.gtog.event.domain.model.GuestResponse;
import com.gtog.event.domain.model.GuestResponseStorageException;
import com.gtog.event.domain.model.ResponseChangeNotAllowedException;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseCleanupPort;

@Component
public class GuestResponseRepositoryAdapter implements GuestResponseRepositoryPort, GuestResponseCleanupPort {
	private final MongoTemplate mongo;

	public GuestResponseRepositoryAdapter(MongoTemplate mongo) { this.mongo = mongo; }

	@Override
	public GuestResponse insert(GuestResponse response) {
		try {
			Document document = new Document();
			mongo.getConverter().write(toDocument(response), document);
			// insert sigue siendo atomico. El comando conserva keyPattern; insertOne del driver
			// lo descarta al construir MongoWriteException y solo deja el mensaje con el token.
			String collection = mongo.getCollectionName(GuestResponseDocument.class);
			Document result = mongo.executeCommand(new Document("insert", collection)
					.append("documents", List.of(document)).append("ordered", true)
					.append("writeConcern", mongo.getCollection(collection).getWriteConcern().asDocument()));
			var errors = result.getList("writeErrors", Document.class, List.of());
			if (!errors.isEmpty()) {
				Document error = errors.getFirst();
				if (Integer.valueOf(11000).equals(error.getInteger("code"))
						&& isResponseKeyPattern(error.toBsonDocument())) {
					throw new ResponseChangeNotAllowedException();
				}
				throw new GuestResponseStorageException();
			}
			if (result.containsKey("writeConcernError") || result.getInteger("n", 0) != 1) {
				throw new GuestResponseStorageException();
			}
			return response;
		} catch (DuplicateKeyException duplicate) {
			if (isResponseKeyDuplicate(duplicate)) { throw new ResponseChangeNotAllowedException(); }
			throw new GuestResponseStorageException();
		} catch (DataAccessException failure) {
			throw new GuestResponseStorageException();
		}
	}

	@Override
	public GuestResponse upsert(GuestResponse response) {
		try {
			return update(response, true);
		} catch (DuplicateKeyException duplicate) {
			if (!isResponseKeyDuplicate(duplicate)) { throw new GuestResponseStorageException(); }
			// Una insercion concurrente gano: reintentar solo la actualizacion atomica, sin consultar existencia.
			try { return update(response, false); }
			catch (DataAccessException failure) { throw new GuestResponseStorageException(); }
		} catch (DataAccessException failure) {
			throw new GuestResponseStorageException();
		}
	}

	private GuestResponse update(GuestResponse response, boolean upsert) {
		Update update = new Update().setOnInsert("eventId", response.eventId()).setOnInsert("guestToken", response.guestToken())
				.setOnInsert("answeredAt", response.answeredAt()).set("responseOptionId", response.responseOptionId())
				.set("comment", response.comment()).set("email", response.email()).set("updatedAt", response.updatedAt());
		var document = mongo.findAndModify(key(response.eventId(), response.guestToken()), update,
				FindAndModifyOptions.options().upsert(upsert).returnNew(true), GuestResponseDocument.class);
		if (document == null) { throw new GuestResponseStorageException(); }
		return toDomain(document);
	}

	@Override
	public Optional<GuestResponse> find(String eventId, String guestToken) {
		try { return Optional.ofNullable(mongo.findOne(key(eventId, guestToken), GuestResponseDocument.class)).map(this::toDomain); }
		catch (DataAccessException failure) { throw new GuestResponseStorageException(); }
	}

	private Query key(String eventId, String token) {
		return Query.query(Criteria.where("eventId").is(eventId).and("guestToken").is(token));
	}

	@Override
	public List<GuestResponse> findByEventIds(Set<String> eventIds) {
		if (eventIds.isEmpty()) { return List.of(); }
		try {
			return mongo.find(Query.query(Criteria.where("eventId").in(eventIds)), GuestResponseDocument.class)
					.stream().map(this::toDomain).toList();
		} catch (DataAccessException failure) { throw new GuestResponseStorageException(); }
	}

	@Override
	public Set<String> findAnsweredOptionIds(String eventId) {
		try {
			return Set.copyOf(mongo.findDistinct(Query.query(Criteria.where("eventId").is(eventId)),
					"responseOptionId", GuestResponseDocument.class, String.class));
		} catch (DataAccessException failure) { throw new GuestResponseStorageException(); }
	}

	@Override
	public boolean hasResponse(String eventId, String guestToken) {
		try { return mongo.exists(key(eventId, guestToken), GuestResponseDocument.class); }
		catch (DataAccessException failure) { throw new GuestResponseStorageException(); }
	}

	@Override
	public void deleteResponse(String eventId, String guestToken) {
		try { mongo.remove(key(eventId, guestToken), GuestResponseDocument.class); }
		catch (DataAccessException failure) { throw new GuestResponseStorageException(); }
	}

	// D-DUP-KEY: codigo y keyPattern estructurados; nunca buscar palabras en el mensaje E11000.
	private boolean isResponseKeyDuplicate(DuplicateKeyException duplicate) {
		for (Throwable cause = duplicate.getCause(); cause != null; cause = cause.getCause()) {
			BsonDocument details = null;
			if (cause instanceof MongoWriteException write && write.getError().getCode() == 11000) {
				details = write.getError().getDetails();
			} else if (cause instanceof MongoCommandException command && command.getErrorCode() == 11000) {
				details = command.getResponse();
			}
			if (details != null && isResponseKeyPattern(details)) { return true; }
		}
		return false;
	}

	private boolean isResponseKeyPattern(BsonDocument details) {
		if (!details.isDocument("keyPattern")) { return false; }
		var pattern = details.getDocument("keyPattern");
		return pattern.size() == 2 && pattern.containsKey("eventId") && pattern.containsKey("guestToken");
	}

	private GuestResponseDocument toDocument(GuestResponse r) {
		return new GuestResponseDocument(r.eventId(), r.guestToken(), r.responseOptionId(), r.comment(), r.email(), r.answeredAt(), r.updatedAt());
	}

	private GuestResponse toDomain(GuestResponseDocument r) {
		return new GuestResponse(r.eventId(), r.guestToken(), r.responseOptionId(), r.comment(), r.email(), r.answeredAt(), r.updatedAt());
	}
}
