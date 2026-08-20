package com.gtog.event.domain.model;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTest {

	private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 9, 1, 20, 0);
	private static final LocalDateTime ENDS_AT = LocalDateTime.of(2026, 9, 1, 23, 0);
	private static final String TIME_ZONE = "Europe/Madrid";

	@Test
	void createSetsStatusDraft() {
		Event event = createEvent();

		assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
	}

	@Test
	void createGeneratesNonNullUniqueId() {
		Event first = createEvent();
		Event second = createEvent();

		assertThat(first.getId()).isNotNull();
		assertThat(first.getId()).isNotEqualTo(second.getId());
	}

	@Test
	void createSetsVersionNull() {
		Event event = createEvent();

		assertThat(event.getVersion()).isNull();
	}

	@Test
	void startsAtInstantAndEndsAtInstantAreComputedFromLocalTimeAndTimeZone() {
		Event event = createEvent();

		assertThat(event.startsAtInstant()).isEqualTo(STARTS_AT.atZone(ZoneOffset.of("+02:00")).toInstant());
		assertThat(event.endsAtInstant()).isEqualTo(ENDS_AT.atZone(ZoneOffset.of("+02:00")).toInstant());
	}

	@Test
	void doesNotAllowEndsAtBeforeStartsAt() {
		LocalDateTime endsAt = STARTS_AT.minusHours(1);

		assertThatThrownBy(() -> Event.create("host-1", "Cumpleaños", "Descripción", STARTS_AT, endsAt, TIME_ZONE,
				Modality.IN_PERSON, null, null, null, null)).isInstanceOf(InvalidEventPeriodException.class);
	}

	@Test
	void doesNotAllowEndsAtEqualToStartsAt() {
		assertThatThrownBy(() -> Event.create("host-1", "Cumpleaños", "Descripción", STARTS_AT, STARTS_AT, TIME_ZONE,
				Modality.IN_PERSON, null, null, null, null)).isInstanceOf(InvalidEventPeriodException.class);
	}

	@Test
	void rejectsInvalidTimeZone() {
		assertThatThrownBy(() -> Event.create("host-1", "Cumpleaños", "Descripción", STARTS_AT, ENDS_AT,
				"Not/AZone", Modality.IN_PERSON, null, null, null, null))
				.isInstanceOf(InvalidTimeZoneException.class);
	}

	@Test
	void rejectsNullTitle() {
		assertThatThrownBy(() -> Event.create("host-1", null, "Descripción", STARTS_AT, ENDS_AT, TIME_ZONE,
				Modality.IN_PERSON, null, null, null, null))
				.isInstanceOf(BlankEventTitleException.class);
	}

	@Test
	void rejectsBlankTitle() {
		assertThatThrownBy(() -> Event.create("host-1", "   ", "Descripción", STARTS_AT, ENDS_AT, TIME_ZONE,
				Modality.IN_PERSON, null, null, null, null))
				.isInstanceOf(BlankEventTitleException.class);
	}

	@Test
	void reconstitutesAnExistingEventWithoutRevalidating() {
		List<ResponseOption> responseOptions = List.of(ResponseOption.create("Asisto", true));
		Event event = Event.reconstitute("event-1", "host-1", "Cumpleaños", "Descripción", STARTS_AT, ENDS_AT,
				TIME_ZONE, Modality.IN_PERSON, EventStatus.PUBLISHED, responseOptions, true, false, null, 3L);

		assertThat(event.getId()).isEqualTo("event-1");
		assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(event.getVersion()).isEqualTo(3L);
		assertThat(event.getResponseOptions()).isEqualTo(responseOptions);
		assertThat(event.isAllowComment()).isTrue();
		assertThat(event.isAllowResponseChange()).isFalse();
	}

	@Test
	void createAppliesDefaultResponseOptionsWhenNoneProvided() {
		Event event = createEvent();

		assertThat(event.getResponseOptions()).hasSize(2);
		assertThat(event.getResponseOptions().get(0).label()).isEqualTo("Asisto");
		assertThat(event.getResponseOptions().get(0).countsAsAttendance()).isTrue();
		assertThat(event.getResponseOptions().get(1).label()).isEqualTo("No asisto");
		assertThat(event.getResponseOptions().get(1).countsAsAttendance()).isFalse();
	}

	@Test
	void createAcceptsProvidedResponseOptions() {
		Event event = createEvent(List.of(
				new ResponseOptionDraft("Voy", true),
				new ResponseOptionDraft("No voy", false),
				new ResponseOptionDraft("Quizas", false)));

		assertThat(event.getResponseOptions()).extracting(ResponseOption::label)
				.containsExactly("Voy", "No voy", "Quizas");
		assertThat(event.getResponseOptions()).allSatisfy(option -> assertThat(option.id()).isNotNull());
	}

	@Test
	void createRejectsFewerThanTwoResponseOptions() {
		List<ResponseOptionDraft> drafts = List.of(new ResponseOptionDraft("Asisto", true));

		assertThatThrownBy(() -> createEvent(drafts)).isInstanceOf(InvalidResponseOptionCountException.class);
	}

	@Test
	void createRejectsMoreThanFiveResponseOptions() {
		List<ResponseOptionDraft> drafts = List.of(
				new ResponseOptionDraft("A", true),
				new ResponseOptionDraft("B", false),
				new ResponseOptionDraft("C", false),
				new ResponseOptionDraft("D", false),
				new ResponseOptionDraft("E", false),
				new ResponseOptionDraft("F", false));

		assertThatThrownBy(() -> createEvent(drafts)).isInstanceOf(InvalidResponseOptionCountException.class);
	}

	@Test
	void createRejectsResponseOptionsWithoutAnyCountingAsAttendance() {
		List<ResponseOptionDraft> drafts = List.of(
				new ResponseOptionDraft("Asisto", false),
				new ResponseOptionDraft("No asisto", false));

		assertThatThrownBy(() -> createEvent(drafts)).isInstanceOf(NoAttendanceResponseOptionException.class);
	}

	@Test
	void createRejectsDuplicateResponseOptionLabels() {
		List<ResponseOptionDraft> drafts = List.of(
				new ResponseOptionDraft("Asisto", true),
				new ResponseOptionDraft("Asisto", false));

		assertThatThrownBy(() -> createEvent(drafts)).isInstanceOf(DuplicateResponseOptionLabelException.class);
	}

	@Test
	void createRejectsBlankResponseOptionLabel() {
		List<ResponseOptionDraft> drafts = List.of(
				new ResponseOptionDraft(" ", true),
				new ResponseOptionDraft("No asisto", false));

		assertThatThrownBy(() -> createEvent(drafts)).isInstanceOf(BlankResponseOptionLabelException.class);
	}

	@Test
	void createDefaultsAllowCommentToFalseAndAllowResponseChangeToTrue() {
		Event event = createEvent();

		assertThat(event.isAllowComment()).isFalse();
		assertThat(event.isAllowResponseChange()).isTrue();
	}

	@Test
	void createHonoursExplicitAllowCommentAndAllowResponseChange() {
		Event event = Event.create("host-1", "Cumpleaños", "Descripción", STARTS_AT, ENDS_AT, TIME_ZONE,
				Modality.IN_PERSON, null, true, false, null);

		assertThat(event.isAllowComment()).isTrue();
		assertThat(event.isAllowResponseChange()).isFalse();
	}

	@Test
	void createRejectsResponseDeadlineAfterStartsAt() {
		LocalDateTime deadline = STARTS_AT.plusMinutes(1);

		assertThatThrownBy(() -> Event.create("host-1", "Cumpleaños", "Descripción", STARTS_AT, ENDS_AT, TIME_ZONE,
				Modality.IN_PERSON, null, null, null, deadline))
				.isInstanceOf(InvalidResponseDeadlineException.class);
	}

	@Test
	void createAcceptsResponseDeadlineEqualToStartsAt() {
		Event event = Event.create("host-1", "Cumpleaños", "Descripción", STARTS_AT, ENDS_AT, TIME_ZONE,
				Modality.IN_PERSON, null, null, null, STARTS_AT);

		assertThat(event.getResponseDeadline()).isEqualTo(STARTS_AT);
	}

	@Test
	void replaceResponseOptionsPreservesIdOfExistingOptionMatchedById() {
		Event event = createEvent();
		String existingId = event.getResponseOptions().get(0).id();

		event.replaceResponseOptions(List.of(
				new ResponseOptionEdit(existingId, "Asisto seguro", true),
				new ResponseOptionEdit(null, "No asisto", false)), false, true, null);

		assertThat(event.getResponseOptions().get(0).id()).isEqualTo(existingId);
		assertThat(event.getResponseOptions().get(0).label()).isEqualTo("Asisto seguro");
	}

	@Test
	void replaceResponseOptionsGeneratesNewIdForOptionsWithoutId() {
		Event event = createEvent();

		event.replaceResponseOptions(List.of(
				new ResponseOptionEdit(null, "Asisto", true),
				new ResponseOptionEdit(null, "No asisto", false)), false, true, null);

		assertThat(event.getResponseOptions()).allSatisfy(option -> assertThat(option.id()).isNotNull());
	}

	@Test
	void replaceResponseOptionsRejectsAnIdThatDoesNotBelongToTheEvent() {
		Event event = createEvent();

		assertThatThrownBy(() -> event.replaceResponseOptions(List.of(
				new ResponseOptionEdit("unknown-id", "Asisto", true),
				new ResponseOptionEdit(null, "No asisto", false)), false, true, null))
				.isInstanceOf(UnknownResponseOptionIdException.class);
	}

	@Test
	void replaceResponseOptionsValidatesTheResultingListJustLikeCreate() {
		Event event = createEvent();

		assertThatThrownBy(() -> event.replaceResponseOptions(
				List.of(new ResponseOptionEdit(null, "Solo una", true)), false, true, null))
				.isInstanceOf(InvalidResponseOptionCountException.class);
	}

	@Test
	void replaceResponseOptionsUpdatesAllowCommentAllowResponseChangeAndResponseDeadline() {
		Event event = createEvent();

		event.replaceResponseOptions(List.of(
				new ResponseOptionEdit(null, "Asisto", true),
				new ResponseOptionEdit(null, "No asisto", false)), true, false, STARTS_AT);

		assertThat(event.isAllowComment()).isTrue();
		assertThat(event.isAllowResponseChange()).isFalse();
		assertThat(event.getResponseDeadline()).isEqualTo(STARTS_AT);
	}

	@Test
	void replaceResponseOptionsRejectsResponseDeadlineAfterStartsAt() {
		Event event = createEvent();
		LocalDateTime deadline = STARTS_AT.plusMinutes(1);

		assertThatThrownBy(() -> event.replaceResponseOptions(List.of(
				new ResponseOptionEdit(null, "Asisto", true),
				new ResponseOptionEdit(null, "No asisto", false)), false, true, deadline))
				.isInstanceOf(InvalidResponseDeadlineException.class);
	}

	@Test
	void doesNotAllowReplacingResponseOptionsWhenEventIsNotDraft() {
		List<ResponseOption> responseOptions = List.of(ResponseOption.create("Asisto", true),
				ResponseOption.create("No asisto", false));
		Event event = Event.reconstitute("event-1", "host-1", "Cumpleaños", "Descripción", STARTS_AT, ENDS_AT,
				TIME_ZONE, Modality.IN_PERSON, EventStatus.PUBLISHED, responseOptions, false, true, null, 0L);

		assertThatThrownBy(() -> event.replaceResponseOptions(List.of(
				new ResponseOptionEdit(null, "Asisto", true),
				new ResponseOptionEdit(null, "No asisto", false)), false, true, null))
				.isInstanceOf(EventNotEditableException.class);
	}

	private Event createEvent() {
		return createEvent(null);
	}

	private Event createEvent(List<ResponseOptionDraft> responseOptionDrafts) {
		return Event.create("host-1", "Cumpleaños", "Descripción", STARTS_AT, ENDS_AT, TIME_ZONE, Modality.IN_PERSON,
				responseOptionDrafts, null, null, null);
	}
}
