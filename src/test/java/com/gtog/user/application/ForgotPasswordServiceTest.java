package com.gtog.user.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import com.gtog.user.domain.model.*;
import com.gtog.user.domain.port.out.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class ForgotPasswordServiceTest {
	private static final Instant NOW = Instant.parse("2027-01-01T10:00:00Z");
	private final UserRepositoryPort users = mock(UserRepositoryPort.class);
	private final AuthMailQuotaPort quota = mock(AuthMailQuotaPort.class);
	private final AuthTokenRepositoryPort tokens = mock(AuthTokenRepositoryPort.class);
	private final AuthTokenGeneratorPort generator = mock(AuthTokenGeneratorPort.class);
	private final AuthEmailSenderPort sender = mock(AuthEmailSenderPort.class);
	private ForgotPasswordService service(Executor executor) {
		return new ForgotPasswordService(users, quota, tokens, generator, sender,
				new PasswordResetEmailComposer("https://public.example", "/reset-password"),
				new AuthMailPolicy(Duration.ofMinutes(5), 5), Duration.ofHours(1), Clock.fixed(NOW, ZoneOffset.UTC), executor);
	}
	@Test void accountLookupAndReservationWaitUntilTheControlledExecutorRuns() {
		var jobs = new ArrayList<Runnable>();
		service(jobs::add).request("  Private@Example.com  ");
		verifyNoInteractions(users, quota, tokens, generator, sender); assertThat(jobs).hasSize(1);
		when(users.findByEmail("private@example.com")).thenReturn(Optional.of(User.reconstitute("user", "Private Name", "private@example.com", Password.fromHash("hash"), "UTC")));
		when(quota.reserveReset(eq("user"), eq(NOW), any())).thenReturn(true);
		when(generator.generate()).thenReturn("A".repeat(43)); when(sender.send(any())).thenReturn(true);
		jobs.getFirst().run();
		var order = inOrder(users, quota, generator, tokens, sender);
		order.verify(users).findByEmail("private@example.com"); order.verify(quota).reserveReset(eq("user"), eq(NOW), any());
		order.verify(generator).generate();
		order.verify(tokens).replace(new AuthToken("user", AuthTokenPurpose.PASSWORD_RESET, AuthToken.hash("A".repeat(43)), NOW.plusSeconds(3600)));
		order.verify(sender).send(argThat(email -> email.body().contains("/reset-password#token=" + "A".repeat(43)) && email.body().contains("Si no lo has pedido, ignóralo.")
				&& !email.body().contains("Private Name") && !email.body().contains("private@example.com")));
	}
	@Test void invalidEmailIsRejectedBeforeEnqueueing() {
		var jobs = new ArrayList<Runnable>();
		assertThatThrownBy(() -> service(jobs::add).request("invalid")).isInstanceOf(InvalidEmailException.class);
		assertThat(jobs).isEmpty(); verifyNoInteractions(users, quota, tokens, generator, sender);
	}
	@Test void anUnknownAccountDoesNotReserveOrEmit() {
		when(users.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
		service(Runnable::run).request("unknown@example.com"); verifyNoInteractions(quota, tokens, generator, sender);
	}
	@Test void saturationWarnsWithoutTheEmailOrOriginalMessage(CapturedOutput output) {
		service(task -> { throw new RejectedExecutionException("private@example.com"); }).request("private@example.com");
		verifyNoInteractions(users, quota, tokens, generator, sender);
		assertThat(output.getAll()).contains("WARN", "AUTH_MAIL_EXECUTOR_SATURATED", RejectedExecutionException.class.getName(), "correlationId=").doesNotContain("private@example.com");
	}
}
