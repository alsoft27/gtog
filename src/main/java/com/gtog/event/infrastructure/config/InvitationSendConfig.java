package com.gtog.event.infrastructure.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.gtog.event.domain.model.EmailSendPolicy;

@Configuration
public class InvitationSendConfig {
	@Bean
	EmailSendPolicy emailSendPolicy(@Value("${app.invitations.max-sends-per-guest:3}") int guestLimit,
			@Value("${app.invitations.min-interval:PT1H}") Duration interval,
			@Value("${app.invitations.max-sends-per-host-per-day:100}") int hostLimit,
			@Value("${app.invitations.max-batch:20}") int batchLimit) {
		return new EmailSendPolicy(guestLimit, interval, hostLimit, batchLimit);
	}
}
