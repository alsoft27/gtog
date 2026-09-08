package com.gtog.user.infrastructure.out.persistence;

import org.springframework.stereotype.Component;

import com.gtog.user.domain.model.Password;
import com.gtog.user.domain.model.User;

@Component
public class UserMapper {

	public UserDocument toDocument(User user) {
		return new UserDocument(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getPassword().hash(),
				user.getTimeZone());
	}

	public User toDomain(UserDocument document) {
		return User.reconstitute(
				document.getId(),
				document.getName(),
				document.getEmail(),
				Password.fromHash(document.getPasswordHash()),
				document.getTimeZone());
	}
}
