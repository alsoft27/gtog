package com.gtog.user.application;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.gtog.user.domain.model.*;
import com.gtog.user.domain.port.in.ForgotPasswordUseCase;
import com.gtog.user.domain.port.out.*;

@Service
public class ForgotPasswordService implements ForgotPasswordUseCase {
	private static final Logger LOG = LoggerFactory.getLogger(ForgotPasswordService.class);
	private final UserRepositoryPort users;
	private final AuthMailQuotaPort quota;
	private final AuthTokenRepositoryPort tokens;
	private final AuthTokenGeneratorPort generator;
	private final AuthEmailSenderPort sender;
	private final PasswordResetEmailComposer composer;
	private final AuthMailPolicy policy;
	private final Duration ttl;
	private final Clock clock;
	private final Executor executor;
	public ForgotPasswordService(UserRepositoryPort users, AuthMailQuotaPort quota, AuthTokenRepositoryPort tokens,
			AuthTokenGeneratorPort generator, AuthEmailSenderPort sender, PasswordResetEmailComposer composer, AuthMailPolicy policy,
			@Value("${app.auth.reset-ttl:PT1H}") Duration ttl, Clock clock, @Qualifier("authMailExecutor") Executor executor) {
		this.users = users; this.quota = quota; this.tokens = tokens; this.generator = generator; this.sender = sender;
		this.composer = composer; this.policy = policy; this.ttl = ttl; this.clock = clock; this.executor = executor;
	}
	@Override public void request(String rawEmail) {
		String email = User.normalizeEmail(rawEmail);
		try { executor.execute(() -> send(email)); }
		catch (RejectedExecutionException exception) {
			LOG.warn("AUTH_MAIL_EXECUTOR_SATURATED exceptionType={} correlationId={}", exception.getClass().getName(), UUID.randomUUID());
		}
	}
	private void send(String email) {
		try {
			var user = users.findByEmail(email).orElse(null);
			if (user == null) { return; }
			var now = clock.instant();
			if (!quota.reserveReset(user.getId(), now, policy)) { return; }
			String raw = generator.generate();
			tokens.replace(new AuthToken(user.getId(), AuthTokenPurpose.PASSWORD_RESET, AuthToken.hash(raw), now.plus(ttl)));
			if (!sender.send(composer.compose(user.getEmail(), raw))) { throw new AuthMailDeliveryException(); }
		} catch (RuntimeException exception) {
			// Conservar la reserva incluso si falla SMTP. Sin mensajes originales ni reintentos.
			LOG.error("AUTH_MAIL_SEND_FAILED exceptionType={} correlationId={}", exception.getClass().getName(), UUID.randomUUID());
		}
	}
	private static final class AuthMailDeliveryException extends RuntimeException {}
}
