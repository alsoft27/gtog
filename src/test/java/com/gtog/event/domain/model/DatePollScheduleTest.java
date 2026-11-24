package com.gtog.event.domain.model;

import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class DatePollScheduleTest {
	private static final LocalDate DATE = LocalDate.of(2027, 5, 10);
	private static final LocalDateTime DEADLINE = DATE.minusDays(1).atTime(18, 0);
	private static DatePollDraft candidates(int count) {
		return new DatePollDraft(IntStream.range(0, count).mapToObj(index ->
				new CandidateDateDraft(DATE.plusDays(index), index % 2 == 0 ? LocalTime.of(20, 0) : null)).toList());
	}
	private static Event.Builder builder() {
		return Event.builder().hostId("host").title("Cena").eventType(EventType.DATE_POLL)
				.datePoll(candidates(3)).responseDeadline(DEADLINE).timeZone("Europe/Madrid")
				.modality(Modality.IN_PERSON).venue(new Venue("Sala", "Dirección", 0.0, 0.0, "p", null));
	}
	@Test void createsADraftWithMixedOptionalTimesWithoutDefinitiveDatesOrAttendanceOptions() {
		Event event = builder().build();
		assertThat(event.getEventType()).isEqualTo(EventType.DATE_POLL);
		assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
		assertThat(event.getStartsAt()).isNull(); assertThat(event.getEndsAt()).isNull();
		assertThat(event.startsAtInstant()).isNull(); assertThat(event.endsAtInstant()).isNull();
		assertThat(event.getResponseOptions()).isEmpty();
		var poll = (DatePollSchedule) event.getSchedule();
		assertThat(poll.candidateDates()).extracting(CandidateDate::date).containsExactly(DATE, DATE.plusDays(1), DATE.plusDays(2));
		assertThat(poll.candidateDates()).extracting(CandidateDate::startTime).containsExactly(LocalTime.of(20, 0), null, LocalTime.of(20, 0));
		assertThat(poll.candidateDates()).extracting(CandidateDate::id).doesNotHaveDuplicates().allSatisfy(id -> assertThat(id).isNotBlank());
		assertThat(event.getTimeZone()).isEqualTo("Europe/Madrid");
		assertThat(event.acceptsResponses(Instant.parse("2027-01-01T00:00:00Z"))).isFalse();
	}
	@Test void requiresAResponseDeadline() {
		assertThatThrownBy(() -> builder().responseDeadline(null).build()).isInstanceOf(MissingDatePollDeadlineException.class);
	}
	@Test void acceptsCandidatesWithNoStartTimesAtAll() {
		var poll = new DatePollDraft(List.of(new CandidateDateDraft(DATE, null), new CandidateDateDraft(DATE.plusDays(1), null)));
		var event = builder().datePoll(poll).build();
		assertThat(((DatePollSchedule) event.getSchedule()).candidateDates()).allSatisfy(candidate -> assertThat(candidate.startTime()).isNull());
		assertThat(event.getStartsAt()).isNull(); assertThat(event.getEndsAt()).isNull();
	}
	@ParameterizedTest @ValueSource(ints = {0, 1, 11})
	void enforcesTheDefaultCandidateCount(int count) {
		assertThatThrownBy(() -> builder().datePoll(candidates(count)).build()).isInstanceOf(InvalidCandidateDateCountException.class);
	}
	@Test void acceptsExactlyTheDefaultMaximum() { assertThat(((DatePollSchedule) builder().datePoll(candidates(10)).build().getSchedule()).candidateDates()).hasSize(10); }
	@Test void usesTheProvidedConfigurableMaximum() {
		assertThatThrownBy(() -> builder().datePollLimits(new DatePollLimits(2)).build()).isInstanceOf(InvalidCandidateDateCountException.class);
	}
	@Test void rejectsAMissingPollBlock() { assertThatThrownBy(() -> builder().datePoll(null).build()).isInstanceOf(InvalidCandidateDateCountException.class); }
	@Test void rejectsAMissingCandidateList() { assertThatThrownBy(() -> builder().datePoll(new DatePollDraft(null)).build()).isInstanceOf(InvalidCandidateDateCountException.class); }
	@Test void rejectsANullCandidate() {
		assertThatThrownBy(() -> builder().datePoll(new DatePollDraft(Arrays.asList(null, new CandidateDateDraft(DATE, null)))).build()).isInstanceOf(MissingCandidateDateException.class);
	}
	@Test void requiresTheCalendarDateEvenWhenTheStartTimeExists() {
		assertThatThrownBy(() -> builder().datePoll(new DatePollDraft(List.of(new CandidateDateDraft(null, LocalTime.NOON), new CandidateDateDraft(DATE, null)))).build()).isInstanceOf(MissingCandidateDateException.class);
	}
	@Test void rejectsTheSameDateEvenWithDifferentStartTimes() {
		assertThatThrownBy(() -> builder().datePoll(new DatePollDraft(List.of(new CandidateDateDraft(DATE, null), new CandidateDateDraft(DATE, LocalTime.NOON)))).build()).isInstanceOf(DuplicateCandidateDateException.class);
	}
	@Test void rejectsAnInvalidTimeZone() { assertThatThrownBy(() -> builder().timeZone("Unknown/Zone").build()).isInstanceOf(InvalidTimeZoneException.class); }
	@Test void rejectsDefinitiveStartOnAPoll() { assertThatThrownBy(() -> builder().startsAt(DEADLINE).build()).isInstanceOf(IncompatibleEventScheduleException.class); }
	@Test void rejectsDefinitiveEndOnAPoll() { assertThatThrownBy(() -> builder().endsAt(DEADLINE).build()).isInstanceOf(IncompatibleEventScheduleException.class); }
	@Test void rejectsAttendanceOptionsOnAPoll() {
		assertThatThrownBy(() -> builder().responseOptions(List.of(new ResponseOptionDraft("Sí", true))).build()).isInstanceOf(IncompatibleEventScheduleException.class);
	}
	@Test void rejectsPollConfigurationOnAFixedDateEvent() {
		assertThatThrownBy(() -> builder().eventType(EventType.FIXED_DATE).startsAt(DEADLINE).endsAt(DEADLINE.plusHours(1)).build()).isInstanceOf(IncompatibleEventScheduleException.class);
	}
	@Test void changingTheDeadlineCannotRemoveTheMandatoryPollDeadline() {
		assertThatThrownBy(() -> builder().build().changeResponseDeadline(null, Instant.EPOCH)).isInstanceOf(MissingDatePollDeadlineException.class);
	}
	@Test void generalDraftEditingPreservesCandidateIdentitiesAndOptionalTimes() {
		Event event = builder().build(); var before = ((DatePollSchedule) event.getSchedule()).candidateDates();
		event.edit(new EventEdit("Editado", "Descripción", null, null, "America/Bogota", Modality.IN_PERSON, null, null,
				true, false, DEADLINE, EventType.DATE_POLL));
		assertThat(event.getTitle()).isEqualTo("Editado");
		assertThat(event.getTimeZone()).isEqualTo("America/Bogota");
		assertThat(((DatePollSchedule) event.getSchedule()).candidateDates()).isEqualTo(before);
		assertThat(event.getStartsAt()).isNull(); assertThat(event.getEndsAt()).isNull();
	}
	@Test void generalEditingCannotConvertTheEventType() {
		assertThatThrownBy(() -> builder().build().edit(new EventEdit("Cena", null, DEADLINE, DEADLINE.plusHours(1), "UTC", Modality.IN_PERSON, null, null,
				false, true, DEADLINE, EventType.FIXED_DATE))).isInstanceOf(IncompatibleEventScheduleException.class);
	}
	@Test void responseOptionsCannotBeInstalledInsteadOfCandidates() {
		assertThatThrownBy(() -> builder().build().replaceResponseOptions(List.of(), Set.of())).isInstanceOf(EventScheduleConflictException.class);
	}
	@Test void aPollCanBePublishedWithoutDefinitiveDatesOrAttendanceOptions() {
		Event event = builder().build();
		// El paso 4 sustituye el bloqueo temporal del paso 2 por la publicación real de la consulta.
		event.publish();
		assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(event.getStartsAt()).isNull(); assertThat(event.getEndsAt()).isNull();
		assertThat(event.getResponseOptions()).isEmpty();
	}
	@Test void rehydrationRejectsDuplicateCandidateIdentities() {
		assertThatThrownBy(() -> new DatePollSchedule(List.of(new CandidateDate("id", DATE, null), new CandidateDate("id", DATE.plusDays(1), null)), "UTC"))
				.isInstanceOf(InvalidCandidateDateIdException.class);
	}
	@Test void candidateIdentityIsRequiredOnRehydration() {
		assertThatThrownBy(() -> new CandidateDate("", DATE, null)).isInstanceOf(InvalidCandidateDateIdException.class);
	}
	@Test void theCandidateListIsImmutable() {
		var schedule = DatePollSchedule.create(candidates(2), "UTC", DatePollLimits.defaults());
		assertThatThrownBy(() -> schedule.candidateDates().clear()).isInstanceOf(UnsupportedOperationException.class);
	}
}
