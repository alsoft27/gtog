package com.gtog.user.infrastructure.out.persistence;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.gtog.user.domain.model.EmailAlreadyRegisteredException;
import com.gtog.user.domain.model.User;
import com.gtog.user.domain.port.out.UserRepositoryPort;

@Component
public class UserRepositoryAdapter implements UserRepositoryPort {

	private final UserMongoRepository userMongoRepository;
	private final UserMapper userMapper;
	private final MongoTemplate mongo;

	public UserRepositoryAdapter(UserMongoRepository userMongoRepository, UserMapper userMapper, MongoTemplate mongo) {
		this.userMongoRepository = userMongoRepository;
		this.userMapper = userMapper;
		this.mongo = mongo;
	}

	@Override
	public User save(User user) {
		try {
			UserDocument saved = userMongoRepository.insert(userMapper.toDocument(user));
			return userMapper.toDomain(saved);
		} catch (DuplicateKeyException e) {
			// El mensaje de MongoDB incluye el nombre del índice violado (p.ej. "email_1").
			// Fragilidad documentada como D-DUP-KEY en docs/plan-iteracion-1.md.
			if (e.getMessage() != null && e.getMessage().contains("email")) {
				throw new EmailAlreadyRegisteredException(user.getEmail());
			}
			throw e;
		}
	}

	@Override
	public Optional<User> findByEmail(String email) {
		return userMongoRepository.findByEmail(email).map(userMapper::toDomain);
	}
	@Override public Optional<User> findById(String userId) { return userMongoRepository.findById(userId).map(userMapper::toDomain); }
	@Override public boolean markEmailVerified(String userId) {
		return mongo.updateFirst(Query.query(Criteria.where("id").is(userId)), new Update().set("emailVerified", true), UserDocument.class).getMatchedCount() == 1;
	}
}
