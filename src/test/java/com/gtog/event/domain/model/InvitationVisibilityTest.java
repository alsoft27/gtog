package com.gtog.event.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.gtog.event.InvitationTestFixtures.NOW;
import static com.gtog.event.InvitationTestFixtures.event;
import static org.assertj.core.api.Assertions.assertThat;

class InvitationVisibilityTest {

	@ParameterizedTest
	@EnumSource(value = EventStatus.class, names = {"DRAFT", "CANCELLED", "FINISHED"})
	void neverExposesAccessOutsidePublishedEvenForAConfirmedGuest(EventStatus status) {
		for (LinkVisibility visibility : LinkVisibility.values()) {
			Event event = event(status, visibility);
			assertThat(event.visibleOnlineAccess(true, NOW.plusSeconds(3600))).isEmpty();
		}
	}

	@Test
	void exposesAccessExactlyAtTheHoursBeforeThreshold() {
		Event event = event(EventStatus.PUBLISHED, LinkVisibility.HOURS_BEFORE);
		assertThat(event.visibleOnlineAccess(false, NOW.minusNanos(1))).isEmpty();
		assertThat(event.visibleOnlineAccess(false, NOW)).contains(event.getOnlineAccess());
	}
}
