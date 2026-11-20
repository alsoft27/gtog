package com.gtog.user.infrastructure.out.persistence;

import org.springframework.stereotype.Component;

import com.gtog.user.domain.model.Password;
import com.gtog.user.domain.model.User;

@Component
public class UserMapper {

	public UserDocument toDocument(User user) {
		UserDocument document = new UserDocument(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getPassword().hash(),
				user.getTimeZone());
		document.setEmailSendsInWindow(user.getEmailSendsInWindow());
		document.setWindowStartedAt(user.getWindowStartedAt());
		document.setEmailVerified(user.isEmailVerified());
		document.setAuthEmailSends(user.getAuthEmailSends());
		document.setAuthEmailWindowStartedAt(user.getAuthEmailWindowStartedAt());
		document.setLastAuthEmailAt(user.getLastAuthEmailAt());
		return document;
	}

	public User toDomain(UserDocument document) {
		return User.reconstitute(
				document.getId(),
				document.getName(),
				document.getEmail(),
				Password.fromHash(document.getPasswordHash()),
				document.getTimeZone(), document.getEmailSendsInWindow(), document.getWindowStartedAt(), document.isEmailVerified(),
				document.getAuthEmailSends(), document.getAuthEmailWindowStartedAt(), document.getLastAuthEmailAt());
	}
}
