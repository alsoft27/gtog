package com.gtog.event.domain.model;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtog.event.DatePollTestFixtures;
import com.gtog.event.GuestTestFixtures;

import static org.assertj.core.api.Assertions.assertThat;

class EventAvailabilitySummaryTest {
	@Test void anEmptyPollStillListsEveryCandidateInOrder() {
		var event = DatePollTestFixtures.published(null);
		var poll = (DatePollSchedule) event.getSchedule();

		var summary = EventAvailabilitySummary.dashboard(event, poll, List.of());

		assertThat(summary.totals()).isEqualTo(new EventAttendanceSummary.Totals(0, 0, 0, 0));
		assertThat(summary.availability().byCandidate()).extracting(EventAvailabilitySummary.CandidateCount::candidate)
				.containsExactlyElementsOf(poll.candidateDates());
		assertThat(summary.availability().byCandidate()).allSatisfy(candidate -> assertThat(candidate.guests()).isEmpty());
	}

	@Test void onlyLivingGuestsInTheCurrentEventAndRoundEnterDashboardAndListingTotals() {
		var event = DatePollTestFixtures.draft(null);
		var ana = GuestTestFixtures.guest(1);
		var bea = GuestTestFixtures.guest(2);
		event.addGuest(ana);
		event.addGuest(bea);
		event.publish();
		var poll = (DatePollSchedule) event.getSchedule();
		var now = Instant.EPOCH;
		var responses = List.of(
				new GuestAvailabilityResponse(event.getId(), ana.getToken(), new GuestAvailability(0, List.of(), now, now)),
				new GuestAvailabilityResponse("different-event", bea.getToken(), new GuestAvailability(0, List.of(), now, now)),
				new GuestAvailabilityResponse(event.getId(), bea.getToken(), new GuestAvailability(1, List.of(), now, now)),
				new GuestAvailabilityResponse(event.getId(), "orphan", new GuestAvailability(0, List.of(), now, now)));

		var summary = EventAvailabilitySummary.dashboard(event, poll, responses);

		assertThat(summary.totals()).isEqualTo(new EventAttendanceSummary.Totals(2, 1, 1, 0));
		assertThat(EventAvailabilitySummary.totals(event, poll, responses)).isEqualTo(summary.totals());
		assertThat(summary.availability().unavailableGuests()).extracting(EventAvailabilitySummary.GuestRef::id)
				.containsExactly(ana.getId());
		assertThat(summary.availability().pendingGuests()).extracting(EventAvailabilitySummary.GuestRef::id)
				.containsExactly(bea.getId());
	}
}
