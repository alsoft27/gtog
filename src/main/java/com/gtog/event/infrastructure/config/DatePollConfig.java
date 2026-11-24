package com.gtog.event.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.gtog.event.domain.model.DatePollLimits;

@Configuration(proxyBeanMethods = false)
public class DatePollConfig {
	@Bean public DatePollLimits datePollLimits(@Value("${app.events.max-candidate-dates:10}") int maximum) {
		return new DatePollLimits(maximum);
	}
}
