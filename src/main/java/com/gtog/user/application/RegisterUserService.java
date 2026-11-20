package com.gtog.user.application;

import com.gtog.user.domain.model.Password;
import com.gtog.user.domain.model.User;
import org.springframework.stereotype.Service;

import com.gtog.user.domain.port.in.RegisterUserCommand;
import com.gtog.user.domain.port.in.RegisterUserUseCase;
import com.gtog.user.domain.port.out.PasswordHasherPort;
import com.gtog.user.domain.port.out.UserRepositoryPort;
import com.gtog.user.domain.port.in.ResendVerificationUseCase;
import com.gtog.user.domain.model.RegistrationVerificationPolicy;

@Service
public class RegisterUserService implements RegisterUserUseCase {

	private final UserRepositoryPort userRepository;
	private final PasswordHasherPort passwordHasher;
	private final ResendVerificationUseCase verification;
	private final RegistrationVerificationPolicy policy;

	public RegisterUserService(UserRepositoryPort userRepository, PasswordHasherPort passwordHasher,
			ResendVerificationUseCase verification, RegistrationVerificationPolicy policy) {
		this.userRepository = userRepository;
		this.passwordHasher = passwordHasher;
		this.verification = verification; this.policy = policy;
	}

	@Override
	public User register(RegisterUserCommand command) {
		Password password = Password.of(command.rawPassword(), passwordHasher);
		User user = User.register(command.name(), command.email(), password, command.timeZone(), policy.autoVerifyEmail());
		User registered = userRepository.save(user);
		if (!registered.isEmailVerified()) { verification.resend(registered.getId()); }
		return registered;
	}
}
