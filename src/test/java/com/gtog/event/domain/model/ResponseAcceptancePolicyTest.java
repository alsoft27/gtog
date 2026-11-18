package com.gtog.event.domain.model;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.gtog.event.InvitationTestFixtures.NOW;
import static com.gtog.event.ResponseTestFixtures.responseEvent;
import static org.assertj.core.api.Assertions.assertThat;

class ResponseAcceptancePolicyTest {
	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	@Test void permitsTheFirstResponseEvenWhenChangesAreDisabled() {
		var policy = responseEvent(EventStatus.PUBLISHED, true, false, null).responseAcceptancePolicy(clock.instant());
		assertThat(policy.canRespond(false)).isTrue();
	}

	@ParameterizedTest @ValueSource(booleans = { false, true })
	void anExistingResponseRequiresChangesToBeEnabled(boolean allowChanges) {
		var policy = responseEvent(EventStatus.PUBLISHED, true, allowChanges, null).responseAcceptancePolicy(clock.instant());
		assertThat(policy.canRespond(true)).isEqualTo(allowChanges);
	}

	@Test void aClosedWindowPreventsBothFirstResponsesAndChanges() {
		var event = responseEvent(EventStatus.PUBLISHED, true, true, null);
		var atEnd = Clock.fixed(event.endsAtInstant(), ZoneOffset.UTC);
		var policy = event.responseAcceptancePolicy(atEnd.instant());
		assertThat(policy.canRespond(false)).isFalse();
		assertThat(policy.canRespond(true)).isFalse();
	}
}
