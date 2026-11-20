package com.gtog.user.application;

import java.time.Clock;
import java.time.Duration;
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
import static com.gtog.event.InvitationTestFixtures.NOW;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class VerificationEmailServiceTest {
	private final UserRepositoryPort users = mock(UserRepositoryPort.class);
	private final AuthMailQuotaPort quota = mock(AuthMailQuotaPort.class);
	private final AuthTokenRepositoryPort tokens = mock(AuthTokenRepositoryPort.class);
	private final AuthTokenGeneratorPort generator = mock(AuthTokenGeneratorPort.class);
	private final AuthEmailSenderPort sender = mock(AuthEmailSenderPort.class);
	private VerificationEmailService service(Executor executor) {
		return new VerificationEmailService(users, quota, tokens, generator, sender, new VerificationEmailComposer("https://public.example", "/verify-email"),
				new AuthMailPolicy(Duration.ofMinutes(5), 5), Duration.ofHours(24), Clock.fixed(NOW, ZoneOffset.UTC), executor);
	}
	@Test void allLookupReservationTokenAndSendingWorkWaitsForTheExecutor() {
		var jobs = new ArrayList<Runnable>(); var service = service(jobs::add); service.resend("user");
		verifyNoInteractions(users, quota, tokens, generator, sender); assertThat(jobs).hasSize(1);
		when(users.findById("user")).thenReturn(Optional.of(User.reconstitute("user", "Private Name", "private@example.com", Password.fromHash("hash"), "UTC")));
		when(quota.reserveVerification(eq("user"), eq(NOW), any())).thenReturn(true);
		when(generator.generate()).thenReturn("A".repeat(43)); when(sender.send(any())).thenReturn(true);
		jobs.getFirst().run();
		verify(tokens).replace(new AuthToken("user", AuthTokenPurpose.EMAIL_VERIFICATION, AuthToken.hash("A".repeat(43)), NOW.plus(Duration.ofHours(24))));
		verify(sender).send(argThat(email -> email.body().contains("#token=" + "A".repeat(43)) && !email.body().contains("Private Name") && !email.body().contains("private@example.com")));
	}
	@Test void saturationIsWarnedWithoutEchoingItsMessageOrDoingLookup(CapturedOutput output) {
		String privateValue = "private@example.com";
		service(task -> { throw new RejectedExecutionException(privateValue); }).resend("user");
		verifyNoInteractions(users, quota, tokens, generator, sender);
		assertThat(output.getAll()).contains("WARN", "AUTH_MAIL_EXECUTOR_SATURATED", RejectedExecutionException.class.getName(), "correlationId=").doesNotContain(privateValue);
	}
	@Test void aVerifiedAccountDoesNotReserveOrEmit() {
		when(users.findById("user")).thenReturn(Optional.of(User.reconstitute("user", "Name", "host@example.com", Password.fromHash("hash"), "UTC", 0, null, true, 0, null, null)));
		service(Runnable::run).resend("user"); verifyNoInteractions(quota, tokens, generator, sender);
	}
}
