package com.gtog.event.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.gtog.event.domain.model.GuestLimits;
import com.gtog.event.domain.model.PublicJoinLimits;

@Configuration(proxyBeanMethods = false)
public class GuestRegistrationConfig {
	@Bean GuestLimits guestLimits(@Value("${app.guests.max-batch:100}") int batch,
			@Value("${app.guests.max-per-event:500}") int event) { return new GuestLimits(batch, event); }
	@Bean PublicJoinLimits publicJoinLimits(@Value("${app.guests.public-link-max:25}") int maximum, GuestLimits guests) {
		return new PublicJoinLimits(maximum, guests.maxPerEvent());
	}
}
