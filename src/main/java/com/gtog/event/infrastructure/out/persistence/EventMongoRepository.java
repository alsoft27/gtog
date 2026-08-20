package com.gtog.event.infrastructure.out.persistence;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventMongoRepository extends MongoRepository<EventDocument, String> {

	List<EventDocument> findByHostId(String hostId);
}
