package com.gtog.event.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.gtog.event.application.WhatsAppInvitationComposer;
import com.gtog.event.domain.model.WhatsAppLinkPolicy;

@Configuration(proxyBeanMethods = false)
public class WhatsAppInvitationConfig {
	@Bean public WhatsAppLinkPolicy whatsAppLinkPolicy(@Value("${app.invitations.max-whatsapp-links-per-request:100}") int maximum) {
		return new WhatsAppLinkPolicy(maximum);
	}
	@Bean public WhatsAppInvitationComposer whatsAppInvitationComposer(@Value("${app.public-base-url}") String baseUrl) {
		return new WhatsAppInvitationComposer(baseUrl);
	}
}
