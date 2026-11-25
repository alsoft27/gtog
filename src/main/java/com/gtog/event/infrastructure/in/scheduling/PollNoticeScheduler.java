package com.gtog.event.infrastructure.in.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.gtog.event.domain.port.in.SendPollNoticesUseCase;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.events.poll-notice-enabled", havingValue = "true", matchIfMissing = true)
class PollNoticeScheduler {
	private static final Logger log = LoggerFactory.getLogger(PollNoticeScheduler.class);
	private final SendPollNoticesUseCase notices;

	PollNoticeScheduler(SendPollNoticesUseCase notices) { this.notices = notices; }

	@Scheduled(initialDelayString = "${app.events.poll-notice-interval:PT1M}",
			fixedDelayString = "${app.events.poll-notice-interval:PT1M}")
	void scan() {
		try {
			notices.sendDueNotices();
		} catch (RuntimeException failure) {
			log.warn("POLL_NOTICE_SCAN_FAILED failureType={}", failure.getClass().getSimpleName());
		}
	}
}
