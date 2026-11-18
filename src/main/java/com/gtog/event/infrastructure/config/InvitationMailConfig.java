package com.gtog.event.infrastructure.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

import com.gtog.event.application.InvitationEmailComposer;
import com.gtog.event.domain.port.out.EmailSenderPort;
import com.gtog.event.infrastructure.out.mail.SmtpEmailSenderAdapter;
import com.gtog.event.infrastructure.out.mail.UnavailableEmailSenderAdapter;

@Configuration(proxyBeanMethods = false)
public class InvitationMailConfig {
	@Bean
	public InvitationEmailComposer invitationEmailComposer(@Value("${app.public-base-url}") String publicBaseUrl) {
		return new InvitationEmailComposer(publicBaseUrl);
	}

	@Bean
	@Profile("!test")
	public EmailSenderPort emailSenderPort(ObjectProvider<JavaMailSender> sender,
			@Value("${spring.mail.username:}") String username, @Value("${spring.mail.password:}") String password,
			@Value("${app.mail.from:}") String from, @Value("${app.mail.from-name:gtog}") String fromName) {
		if (username.isBlank() || password.isBlank() || from.isBlank()) {
			return new UnavailableEmailSenderAdapter();
		}
		JavaMailSender configuredSender = sender.getIfAvailable();
		return configuredSender == null ? new UnavailableEmailSenderAdapter()
				: new SmtpEmailSenderAdapter(configuredSender, from, fromName);
	}
}
