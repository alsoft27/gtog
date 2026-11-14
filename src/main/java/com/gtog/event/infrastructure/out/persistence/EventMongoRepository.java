package com.gtog.event.infrastructure.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventMongoRepository extends MongoRepository<EventDocument, String> {

	Optional<EventDocument> findByGuestsToken(String token);

	List<EventDocument> findByHostId(String hostId);
}
