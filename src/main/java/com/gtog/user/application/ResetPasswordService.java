package com.gtog.user.application;

import java.time.Clock;
import org.springframework.stereotype.Service;
import com.gtog.user.domain.model.*;
import com.gtog.user.domain.port.in.ResetPasswordUseCase;
import com.gtog.user.domain.port.out.*;

@Service
public class ResetPasswordService implements ResetPasswordUseCase {
	private final AuthTokenRepositoryPort tokens;
	private final UserRepositoryPort users;
	private final PasswordHasherPort hasher;
	private final SessionInvalidationPort sessions;
	private final Clock clock;
	public ResetPasswordService(AuthTokenRepositoryPort tokens, UserRepositoryPort users, PasswordHasherPort hasher,
			SessionInvalidationPort sessions, Clock clock) {
		this.tokens = tokens; this.users = users; this.hasher = hasher; this.sessions = sessions; this.clock = clock;
	}
	@Override public void reset(String raw, String newPassword) {
		Password.validateRaw(newPassword);
		if (!AuthToken.isWellFormed(raw)) { throw new InvalidAuthTokenException(); }
		var now = clock.instant();
		var token = tokens.consume(AuthToken.hash(raw), AuthTokenPurpose.PASSWORD_RESET, now).orElseThrow(InvalidAuthTokenException::new);
		if (!token.isValidAt(now)) { throw new InvalidAuthTokenException(); }
		// Hash y escritura despues del consumo: un fallo gasta el token y obliga a pedir otro.
		String hash = hasher.hash(newPassword);
		if (!users.updatePasswordAndVerify(token.userId(), hash)) { throw new IllegalStateException("Account unavailable during password reset"); }
		tokens.deleteByUserId(token.userId());
		sessions.invalidateAll(token.userId());
	}
}
