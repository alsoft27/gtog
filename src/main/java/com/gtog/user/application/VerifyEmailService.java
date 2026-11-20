package com.gtog.user.application;

import java.time.Clock;
import org.springframework.stereotype.Service;
import com.gtog.user.domain.model.*;
import com.gtog.user.domain.port.in.VerifyEmailUseCase;
import com.gtog.user.domain.port.out.AuthTokenRepositoryPort;
import com.gtog.user.domain.port.out.UserRepositoryPort;

@Service
public class VerifyEmailService implements VerifyEmailUseCase {
	private final AuthTokenRepositoryPort tokens;
	private final UserRepositoryPort users;
	private final Clock clock;
	public VerifyEmailService(AuthTokenRepositoryPort tokens, UserRepositoryPort users, Clock clock) { this.tokens = tokens; this.users = users; this.clock = clock; }
	@Override public void verify(String raw) {
		if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) { throw new InvalidAuthTokenException(); }
		var now = clock.instant();
		var token = tokens.consume(AuthToken.hash(raw), AuthTokenPurpose.EMAIL_VERIFICATION, now).orElseThrow(InvalidAuthTokenException::new);
		if (!token.isValidAt(now) || !users.markEmailVerified(token.userId())) { throw new InvalidAuthTokenException(); }
	}
}
