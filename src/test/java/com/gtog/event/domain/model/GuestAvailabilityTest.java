package com.gtog.event.domain.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtog.event.DatePollTestFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuestAvailabilityTest {
	private static final Instant NOW = Instant.parse("2027-03-01T10:00:00Z");
	private final Event event = DatePollTestFixtures.published(null);
	private final DatePollSchedule poll = (DatePollSchedule) event.getSchedule();

	@Test void acceptsExplicitNoneAndSortsMultipleSelectionsByCandidateOrder() {
		String first = poll.candidateDates().getFirst().id();
		String last = poll.candidateDates().getLast().id();
		assertThat(GuestAvailability.create(event, 0L, List.of(), NOW).candidateDateIds()).isEmpty();
		assertThat(GuestAvailability.create(event, 0L, List.of(last, first), NOW).candidateDateIds()).containsExactly(first, last);
	}

	@Test void rejectsMissingUnknownNullAndRepeatedCandidates() {
		String first = poll.candidateDates().getFirst().id();
		for (var selection : Arrays.asList(null, List.of("unknown"), Arrays.asList((String) null), List.of(first, first))) {
			assertThatThrownBy(() -> GuestAvailability.create(event, 0L, selection, NOW))
					.isInstanceOf(InvalidAvailabilityException.class);
		}
	}

	@Test void rejectsAnInvalidOrMismatchedRevision() {
		assertThatThrownBy(() -> GuestAvailability.create(event, null, List.of(), NOW)).isInstanceOf(InvalidAvailabilityException.class);
		assertThatThrownBy(() -> GuestAvailability.create(event, -1L, List.of(), NOW)).isInstanceOf(InvalidAvailabilityException.class);
		assertThatThrownBy(() -> GuestAvailability.create(event, 1L, List.of(), NOW)).isInstanceOf(StalePollRevisionException.class);
	}
}
