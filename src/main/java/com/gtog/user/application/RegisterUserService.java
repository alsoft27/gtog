package com.gtog.user.application;

import com.gtog.user.domain.model.Password;
import com.gtog.user.domain.model.User;
import org.springframework.stereotype.Service;

import com.gtog.user.domain.port.in.RegisterUserCommand;
import com.gtog.user.domain.port.in.RegisterUserUseCase;
import com.gtog.user.domain.port.out.PasswordHasherPort;
import com.gtog.user.domain.port.out.UserRepositoryPort;

@Service
public class RegisterUserService implements RegisterUserUseCase {

	private final UserRepositoryPort userRepository;
	private final PasswordHasherPort passwordHasher;

	public RegisterUserService(UserRepositoryPort userRepository, PasswordHasherPort passwordHasher) {
		this.userRepository = userRepository;
		this.passwordHasher = passwordHasher;
	}

	@Override
	public User register(RegisterUserCommand command) {
		Password password = Password.of(command.rawPassword(), passwordHasher);
		User user = User.register(command.name(), command.email(), password, command.timeZone());
		return userRepository.save(user);
	}
}
