package com.gtog.event.domain.model;

import java.util.List;
import org.junit.jupiter.api.Test;
import static com.gtog.event.AttendanceTestFixtures.event;
import static com.gtog.event.AttendanceTestFixtures.response;
import static com.gtog.event.InvitationTestFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

class EventAttendanceSummaryTest {
	@Test
	void anEventWithoutGuestsHasZeroTotalsAndZeroCountsForEveryOption() {
		var event = com.gtog.event.InvitationTestFixtures.event(EventStatus.PUBLISHED, null);
		var summary = EventAttendanceSummary.calculate(event, List.of());
		assertThat(summary.totals()).isEqualTo(new EventAttendanceSummary.Totals(0, 0, 0, 0));
		assertThat(summary.guests()).isEmpty();
		assertThat(summary.byOption()).hasSize(2).allSatisfy(option -> assertThat(option.count()).isZero());
	}

	@Test
	void unansweredGuestsHaveANullResponseAndANullOptionKnown() {
		var summary = EventAttendanceSummary.calculate(event(EventStatus.PUBLISHED), List.of());
		assertThat(summary.totals()).isEqualTo(new EventAttendanceSummary.Totals(3, 0, 3, 0));
		assertThat(summary.guests()).hasSize(3).allSatisfy(row -> {
			assertThat(row.answered()).isFalse();
			assertThat(row.response()).isNull();
			assertThat(row.optionKnown()).isNull();
		});
	}

	@Test
	void countsAttendanceAndNonAttendanceInGuestAndOptionOrderRegardlessOfResponseOrder() {
		var event = event(EventStatus.PUBLISHED);
		var yes = response(event, 1, "yes-id");
		var no = response(event, 2, "no-id");
		var summary = EventAttendanceSummary.calculate(event, List.of(no, yes));
		assertThat(summary.totals()).isEqualTo(new EventAttendanceSummary.Totals(3, 2, 1, 1));
		assertThat(summary.byOption()).extracting(EventAttendanceSummary.OptionCount::optionId).containsExactly("yes-id", "no-id");
		assertThat(summary.byOption()).extracting(EventAttendanceSummary.OptionCount::count).containsExactly(1, 1);
		assertThat(summary.byOption()).extracting(EventAttendanceSummary.OptionCount::countsAsAttendance).containsExactly(true, false);
		assertThat(summary.guests()).extracting(row -> row.guest().getId()).containsExactly("guest-1", "guest-2", "guest-3");
		assertThat(summary.guests().get(1).optionKnown()).isTrue();
		assertThat(summary.guests().get(2).optionKnown()).isTrue();
		assertThat(summary.guests().get(1).response()).isEqualTo(new EventAttendanceSummary.Response("yes-id", "Asisto", yes.comment(), NOW, NOW.plusSeconds(1)));
		assertThat(summary.guests().get(1).guest().getInvitationStatus()).isEqualTo(InvitationStatus.SENT);
	}

	@Test
	void anUnknownOptionIsAnsweredButNotAttendingAndOptionKnownIsFalse() {
		var event = event(EventStatus.PUBLISHED);
		var summary = EventAttendanceSummary.calculate(event, List.of(response(event, 0, "removed-option")));
		assertThat(summary.totals()).isEqualTo(new EventAttendanceSummary.Totals(3, 1, 2, 0));
		assertThat(summary.byOption()).allSatisfy(option -> assertThat(option.count()).isZero());
		var row = summary.guests().getFirst();
		assertThat(row.answered()).isTrue();
		assertThat(row.optionKnown()).isFalse();
		assertThat(row.response().responseOptionId()).isEqualTo("removed-option");
		assertThat(row.response().label()).isNull();
	}

	@Test
	void aResponseWithoutALiveGuestDoesNotContributeToAnyCounterOrRow() {
		var event = event(EventStatus.PUBLISHED);
		var orphan = new GuestResponse(event.getId(), "removed-guest-token", "yes-id", null, null, NOW, NOW);
		assertThat(EventAttendanceSummary.calculate(event, List.of(orphan)))
				.isEqualTo(EventAttendanceSummary.calculate(event, List.of()));
	}

	@Test
	void aResponseFromAnotherEventCannotBeMatchedEvenIfTheTokenCoincides() {
		var event = event(EventStatus.PUBLISHED);
		var foreign = new GuestResponse("another-event", event.getGuests().getFirst().getToken(), "yes-id", null, null, NOW, NOW);
		assertThat(EventAttendanceSummary.calculate(event, List.of(foreign)).totals())
				.isEqualTo(new EventAttendanceSummary.Totals(3, 0, 3, 0));
	}

	@Test
	void calculationDoesNotMutateTheEventOrResponses() {
		var event = event(EventStatus.PUBLISHED);
		var guests = event.getGuests();
		var options = event.getResponseOptions();
		var version = event.getVersion();
		var response = response(event, 0, "yes-id");
		EventAttendanceSummary.calculate(event, List.of(response));
		assertThat(event.getGuests()).isEqualTo(guests);
		assertThat(event.getResponseOptions()).isEqualTo(options);
		assertThat(event.getVersion()).isEqualTo(version);
		assertThat(response.email()).isEqualTo("private-response-0@example.com");
	}
}
