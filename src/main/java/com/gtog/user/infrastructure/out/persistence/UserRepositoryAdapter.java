package com.gtog.user.infrastructure.out.persistence;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.gtog.user.domain.model.EmailAlreadyRegisteredException;
import com.gtog.user.domain.model.User;
import com.gtog.user.domain.port.out.UserRepositoryPort;

@Component
public class UserRepositoryAdapter implements UserRepositoryPort {

	private final UserMongoRepository userMongoRepository;
	private final UserMapper userMapper;

	public UserRepositoryAdapter(UserMongoRepository userMongoRepository, UserMapper userMapper) {
		this.userMongoRepository = userMongoRepository;
		this.userMapper = userMapper;
	}

	@Override
	public User save(User user) {
		try {
			UserDocument saved = userMongoRepository.save(userMapper.toDocument(user));
			return userMapper.toDomain(saved);
		} catch (DuplicateKeyException e) {
			// El mensaje de MongoDB incluye el nombre del índice violado (p.ej. "email_1").
			// Fragilidad documentada como D-DUP-KEY en el plan de R5.
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
}
