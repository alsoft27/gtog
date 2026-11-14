package com.gtog.event.domain.model;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.gtog.event.InvitationTestFixtures.NOW;
import static com.gtog.event.ResponseTestFixtures.responseEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuestResponseTest {
	@Test
	void rejectsAnOptionThatDoesNotBelongToTheEvent() {
		assertThatThrownBy(() -> GuestResponse.create(event(true), "secret", "foreign-option", null, null, NOW))
				.isInstanceOf(InvalidResponseOptionException.class);
	}

	@Test
	void rejectsAMissingOption() {
		assertThatThrownBy(() -> GuestResponse.create(event(true), "secret", null, null, null, NOW))
				.isInstanceOf(InvalidResponseOptionException.class);
	}

	@Test
	void rejectsContentWhenCommentsAreDisabled() {
		assertThatThrownBy(() -> GuestResponse.create(event(false), "secret", "yes-id", "comment", null, NOW))
				.isInstanceOf(ResponseCommentNotAllowedException.class);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\t\n"})
	void blankCommentsAreAbsentEvenWhenCommentsAreDisabled(String comment) {
		assertThat(GuestResponse.create(event(false), "secret", "yes-id", comment, null, NOW).comment()).isNull();
	}

	@Test
	void acceptsACommentOfExactly500Characters() {
		assertThat(GuestResponse.create(event(true), "secret", "yes-id", "a".repeat(500), null, NOW).comment()).hasSize(500);
	}

	@Test
	void rejectsACommentOver500Characters() {
		assertThatThrownBy(() -> GuestResponse.create(event(true), "secret", "yes-id", "a".repeat(501), null, NOW))
				.isInstanceOf(ResponseCommentTooLongException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"invalid", "a@b", "a b@example.com", "a\t@example.com"})
	void rejectsInvalidEmail(String email) {
		assertThatThrownBy(() -> GuestResponse.create(event(true), "secret", "yes-id", null, email, NOW))
				.isInstanceOf(InvalidResponseEmailException.class);
	}

	@Test
	void normalizesValidEmailAndUsesTheSuppliedInstant() {
		var response = GuestResponse.create(event(true), "secret", "yes-id", "comment", " Guest@Example.com ", NOW);
		assertThat(response.email()).isEqualTo("guest@example.com");
		assertThat(response.answeredAt()).isEqualTo(NOW);
		assertThat(response.updatedAt()).isEqualTo(NOW);
		assertThat(response.toString()).doesNotContain("secret", "comment", "guest@example.com");
	}

	@ParameterizedTest
	@EnumSource(value = EventStatus.class, names = {"DRAFT", "CANCELLED", "FINISHED"})
	void rejectsStatesOtherThanPublished(EventStatus status) {
		assertThatThrownBy(() -> GuestResponse.create(responseEvent(status, true, true, null), "secret", "yes-id", null, null, NOW))
				.isInstanceOf(ResponseNotAcceptedException.class);
	}

	@Test
	void theDeadlineIsInclusiveAndUsesTheEventsTimeZone() {
		Event event = responseEvent(EventStatus.PUBLISHED, true, true, LocalDateTime.of(2027, 1, 1, 17, 0));
		var deadline = java.time.Instant.parse("2027-01-01T16:00:00Z");
		assertThat(event.acceptsResponses(deadline.minusNanos(1))).isTrue();
		assertThat(event.acceptsResponses(deadline)).isTrue();
		assertThatThrownBy(() -> event.requireAcceptingResponses(deadline.plusNanos(1)))
				.isInstanceOf(ResponseNotAcceptedException.class);
	}

	@Test
	void theEndIsExclusiveWithoutADeadline() {
		Event event = event(true);
		assertThat(event.acceptsResponses(event.endsAtInstant().minusNanos(1))).isTrue();
		assertThatThrownBy(() -> event.requireAcceptingResponses(event.endsAtInstant())).isInstanceOf(ResponseNotAcceptedException.class);
		assertThat(event.acceptsResponses(event.endsAtInstant().plusNanos(1))).isFalse();
	}

	private Event event(boolean comments) { return responseEvent(EventStatus.PUBLISHED, comments, true, null); }
}
