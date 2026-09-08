package com.gtog.user.domain.port.in;

import com.gtog.user.domain.model.User;

public interface RegisterUserUseCase {

	User register(RegisterUserCommand command);
}
