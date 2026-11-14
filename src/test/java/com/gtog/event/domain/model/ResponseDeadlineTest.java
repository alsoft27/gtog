package com.gtog.event.domain.model;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.gtog.event.InvitationTestFixtures.NOW;
import static com.gtog.event.InvitationTestFixtures.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseDeadlineTest {

	@Test
	void draftAcceptsPastDeadlinesAndNullEvenAfterTheStart() {
		Event event = event(EventStatus.DRAFT, null);
		LocalDateTime past = event.getStartsAt().minusDays(1);
		event.changeResponseDeadline(past, event.startsAtInstant().plusSeconds(1));
		assertThat(event.getResponseDeadline()).isEqualTo(past);
		event.changeResponseDeadline(null, event.startsAtInstant().plusSeconds(1));
		assertThat(event.getResponseDeadline()).isNull();
	}

	@Test
	void publishedAcceptsADeadlineAndItsRemovalBeforeTheStart() {
		Event event = event(EventStatus.PUBLISHED, null);
		event.changeResponseDeadline(event.getStartsAt(), event.startsAtInstant().minusNanos(1));
		assertThat(event.getResponseDeadline()).isEqualTo(event.getStartsAt());
		event.changeResponseDeadline(null, NOW);
		assertThat(event.getResponseDeadline()).isNull();
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1})
	void publishedRejectsAtAndAfterTheStart(int seconds) {
		Event event = event(EventStatus.PUBLISHED, null);
		assertThatThrownBy(() -> event.changeResponseDeadline(null, event.startsAtInstant().plusSeconds(seconds)))
				.isInstanceOf(ResponseDeadlineNotEditableException.class);
	}

	@ParameterizedTest
	@EnumSource(value = EventStatus.class, names = {"CANCELLED", "FINISHED"})
	void closedEventsRejectDeadlineChanges(EventStatus status) {
		Event event = event(status, null);
		assertThatThrownBy(() -> event.changeResponseDeadline(null, NOW)).isInstanceOf(ResponseDeadlineNotEditableException.class);
	}

	@Test
	void publishedAcceptsAPastDeadlineToCloseResponsesImmediately() {
		Event event = event(EventStatus.PUBLISHED, null);
		LocalDateTime past = event.getStartsAt().minusDays(1);
		event.changeResponseDeadline(past, NOW);
		assertThat(event.getResponseDeadline()).isEqualTo(past);
	}

	@Test
	void dedicatedChangeAndDraftEditUseTheSameDeadlineValidation() {
		Event event = event(EventStatus.DRAFT, null);
		LocalDateTime invalid = event.getStartsAt().plusSeconds(1);
		assertThatThrownBy(() -> event.changeResponseDeadline(invalid, NOW))
				.isInstanceOf(InvalidResponseDeadlineException.class);
		assertThatThrownBy(() -> event.edit(new EventEdit(event.getTitle(), event.getDescription(), event.getStartsAt(),
				event.getEndsAt(), event.getTimeZone(), event.getModality(), event.getVenue(), null, true, false, invalid)))
				.isInstanceOf(InvalidResponseDeadlineException.class);
	}
}
