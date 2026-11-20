package com.gtog.user.infrastructure.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.gtog.user.application.VerificationEmailComposer;
import com.gtog.user.application.PasswordResetEmailComposer;
import com.gtog.user.domain.model.AuthMailPolicy;
import com.gtog.user.domain.model.RegistrationVerificationPolicy;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthVerificationConfig {
	@Bean RegistrationVerificationPolicy registrationVerificationPolicy(AuthProperties properties) { return new RegistrationVerificationPolicy(properties.isAutoVerifyEmail()); }
	@Bean LocalEmailVerificationGuard localEmailVerificationGuard(AuthProperties properties, @Value("${app.public-base-url}") String baseUrl) {
		return new LocalEmailVerificationGuard(properties, baseUrl);
	}
	@Bean AuthMailPolicy authMailPolicy(AuthProperties properties) { return new AuthMailPolicy(properties.getMailCooldown(), properties.getMailMaxPerDay()); }
	@Bean @DependsOn("localEmailVerificationGuard")
	VerificationEmailComposer verificationEmailComposer(AuthProperties properties, @Value("${app.public-base-url}") String baseUrl) {
		return new VerificationEmailComposer(baseUrl, properties.getVerifyEmailPath());
	}
	@Bean("authMailExecutor") @Profile("!test")
	ThreadPoolTaskExecutor authMailExecutor(AuthProperties properties) {
		var options = properties.getMailExecutor();
		var executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(options.getCoreSize()); executor.setMaxPoolSize(options.getMaxSize()); executor.setQueueCapacity(options.getQueueCapacity());
		executor.setThreadNamePrefix("auth-mail-"); executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		return executor;
	}
	@Bean @DependsOn("localEmailVerificationGuard")
	PasswordResetEmailComposer passwordResetEmailComposer(AuthProperties properties, @Value("${app.public-base-url}") String baseUrl) {
		return new PasswordResetEmailComposer(baseUrl, properties.getResetPasswordPath());
	}
}
