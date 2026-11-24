package com.gtog.event.infrastructure.config;

import com.gtog.event.domain.model.DatePollLimits;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class DatePollConfigTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(DatePollConfig.class);
	@Test void defaultsToTenCandidates() { runner.run(context -> assertThat(context.getBean(DatePollLimits.class).maxCandidates()).isEqualTo(10)); }
	@Test void bindsTheConfiguredMaximum() { runner.withPropertyValues("app.events.max-candidate-dates=3").run(context -> assertThat(context.getBean(DatePollLimits.class).maxCandidates()).isEqualTo(3)); }
	@Test void rejectsAConfiguredMaximumBelowTheMinimum() { runner.withPropertyValues("app.events.max-candidate-dates=1").run(context -> assertThat(context).hasFailed()); }
}
