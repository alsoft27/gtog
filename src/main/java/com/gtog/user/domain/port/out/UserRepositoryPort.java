package com.gtog.user.domain.port.out;

import java.util.Optional;

import com.gtog.user.domain.model.User;

public interface UserRepositoryPort {

	User save(User user);

	Optional<User> findByEmail(String email);
	Optional<User> findById(String userId);
	boolean markEmailVerified(String userId);
}
