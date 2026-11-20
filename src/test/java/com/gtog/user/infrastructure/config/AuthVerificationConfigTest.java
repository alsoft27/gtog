package com.gtog.user.infrastructure.config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class AuthVerificationConfigTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(GuardConfiguration.class)
			.withPropertyValues("app.auth.auto-verify-email=false", "app.public-base-url=http://localhost:4200");
	@Test void theExactEnvironmentVariableBindsToAutoVerifyEmail(CapturedOutput output) {
		new ApplicationContextRunner().withUserConfiguration(GuardConfiguration.class)
				.withPropertyValues("app.public-base-url=http://localhost:4200")
				.withInitializer(context -> {
					context.getEnvironment().getPropertySources().remove("systemEnvironment");
					TestPropertyValues.of("APP_AUTH_AUTOVERIFYEMAIL=true").applyTo(context.getEnvironment(), TestPropertyValues.Type.SYSTEM_ENVIRONMENT);
				})
				.run(context -> { assertThat(context).hasNotFailed(); assertThat(context.getBean(AuthProperties.class).isAutoVerifyEmail()).isTrue(); });
		assertThat(output.getAll()).contains("WARN", "app.auth.auto-verify-email=true", "APP_AUTH_AUTOVERIFYEMAIL=true", "NO ES PARA PRODUCCION");
	}
	@Test void aMisspelledEnvironmentVariableDoesNotActivateTheBypass(CapturedOutput output) {
		runner.withInitializer(context -> TestPropertyValues.of("APP_AUTH_AUTOVERIFYEMIAL=true").applyTo(context.getEnvironment(), TestPropertyValues.Type.SYSTEM_ENVIRONMENT))
				.run(context -> { assertThat(context).hasNotFailed(); assertThat(context.getBean(AuthProperties.class).isAutoVerifyEmail()).isFalse(); });
		assertThat(output.getAll()).doesNotContain("LOCAL BYPASS ENABLED");
	}
	@ParameterizedTest @ValueSource(strings = { "http://localhost:4200", "http://127.0.0.1:4200" })
	void theBypassStartsWithAnAllowedLocalHost(String base) {
		runner.withPropertyValues("app.auth.auto-verify-email=true", "app.public-base-url=" + base).run(context -> assertThat(context).hasNotFailed());
	}
	@ParameterizedTest @ValueSource(strings = { "https://remote.example", "https://localhost.evil.example", "https://private-user:private-password@remote.example/path",
			"http://localhost.evil.com", "http://localhost@evil.com", "http://127.0.0.1.evil.com" })
	void theBypassRejectsOtherHostsWithoutLeakingCredentials(String base, CapturedOutput output) {
		runner.withPropertyValues("app.auth.auto-verify-email=true", "app.public-base-url=" + base).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
			assertThat(context.getStartupFailure().getMessage()).contains("app.auth.auto-verify-email", "host=");
		});
		assertThat(output.getAll()).doesNotContain("private-user", "private-password");
	}
	@Test void disabledBypassAllowsAPublicFrontendHost() {
		runner.withPropertyValues("app.public-base-url=https://public.example").run(context -> assertThat(context).hasNotFailed());
	}
	@Test void mailExecutorHasDocumentedDefaultsAndRejectsRatherThanRunningOnTheCaller() throws Exception {
		new ApplicationContextRunner().withUserConfiguration(AuthVerificationConfig.class)
				.withPropertyValues("app.auth.auto-verify-email=false", "app.public-base-url=http://localhost:4200")
				.run(context -> {
					assertThat(context).hasNotFailed(); var executor = context.getBean(ThreadPoolTaskExecutor.class);
					assertThat(executor.getCorePoolSize()).isEqualTo(2); assertThat(executor.getMaxPoolSize()).isEqualTo(4);
					assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(100);
				});
		CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
		new ApplicationContextRunner().withUserConfiguration(AuthVerificationConfig.class)
				.withPropertyValues("app.auth.auto-verify-email=false", "app.public-base-url=http://localhost:4200",
						"app.auth.mail-executor.core-size=1", "app.auth.mail-executor.max-size=1", "app.auth.mail-executor.queue-capacity=1")
				.run(context -> {
					var executor = context.getBean(ThreadPoolTaskExecutor.class);
					try {
						executor.execute(() -> { started.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); } });
						assertThat(started.await(5, TimeUnit.SECONDS)).isTrue(); executor.execute(() -> {});
						assertThatThrownBy(() -> executor.execute(() -> { throw new AssertionError("Must not run on the caller"); })).isInstanceOf(RejectedExecutionException.class);
					} finally { release.countDown(); }
				});
	}
	@TestConfiguration(proxyBeanMethods = false)
	@EnableConfigurationProperties(AuthProperties.class)
	static class GuardConfiguration {
		@Bean LocalEmailVerificationGuard guard(AuthProperties properties, @Value("${app.public-base-url}") String base) { return new LocalEmailVerificationGuard(properties, base); }
	}
}
