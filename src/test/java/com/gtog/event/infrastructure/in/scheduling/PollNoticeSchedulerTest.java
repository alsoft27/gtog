package com.gtog.event.infrastructure.in.scheduling;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import com.gtog.event.domain.port.in.SendPollNoticesUseCase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PollNoticeSchedulerTest {
	private final ApplicationContextRunner context = new ApplicationContextRunner()
			.withUserConfiguration(PollNoticeScheduler.class)
			.withBean(SendPollNoticesUseCase.class, () -> mock(SendPollNoticesUseCase.class));

	@Test void registersTheScheduledScanByDefault() {
		context.run(application -> assertThat(application.getBean(ScheduledAnnotationBeanPostProcessor.class)
				.getScheduledTasks()).hasSize(1));
	}

	@Test void disablingNoticesDoesNotRegisterAScan() {
		context.withPropertyValues("app.events.poll-notice-enabled=false")
				.run(application -> assertThat(application).doesNotHaveBean(PollNoticeScheduler.class)
						.doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
	}
}
