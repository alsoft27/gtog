package com.gtog.user.infrastructure.out.persistence;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserMongoRepository extends MongoRepository<UserDocument, String> {

	Optional<UserDocument> findByEmail(String email);
}
