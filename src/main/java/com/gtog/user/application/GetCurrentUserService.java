package com.gtog.user.application;

import org.springframework.stereotype.Service;
import com.gtog.user.domain.model.User;
import com.gtog.user.domain.model.AuthAccountUnavailableException;
import com.gtog.user.domain.port.in.GetCurrentUserUseCase;
import com.gtog.user.domain.port.out.UserRepositoryPort;

@Service
public class GetCurrentUserService implements GetCurrentUserUseCase {
	private final UserRepositoryPort users;
	public GetCurrentUserService(UserRepositoryPort users) { this.users = users; }
	@Override public User get(String userId) { return users.findById(userId).orElseThrow(AuthAccountUnavailableException::new); }
}
