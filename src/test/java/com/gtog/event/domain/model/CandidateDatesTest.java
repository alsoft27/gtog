package com.gtog.event.domain.model;

import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class CandidateDatesTest {
	private static final LocalDate DATE = LocalDate.of(2027, 5, 10);
	private static final DatePollLimits LIMITS = DatePollLimits.defaults();
	private static DatePollSchedule poll() {
		return new DatePollSchedule(List.of(new CandidateDate("a", DATE, LocalTime.of(20, 0)),
				new CandidateDate("b", DATE.plusDays(1), null)), "Europe/Madrid", 4, "b");
	}
	private static List<CandidateDateEdit> edits(DatePollSchedule poll) {
		return poll.candidateDates().stream().map(candidate -> new CandidateDateEdit(candidate.id(), candidate.date(), candidate.startTime())).toList();
	}
	private static Event event(EventStatus status) {
		return Event.reconstituteBuilder().id("event").hostId("host").title("Cena").status(status).version(75L)
				.schedule(poll()).responseOptions(List.of()).responseDeadline(DATE.minusDays(1).atTime(18, 0))
				.modality(Modality.IN_PERSON).venue(new Venue("Sala", "Dirección", 0.0, 0.0, "p", null)).build();
	}
	@Test void changingADateAdvancesThePollOnceAndClearsSelectionWithoutChangingIdentity() {
		var before = poll();
		var after = before.replaceCandidates(List.of(new CandidateDateEdit("a", DATE.plusDays(2), LocalTime.of(20, 0)), edits(before).get(1)), 4L, LIMITS);
		assertThat(after.pollRevision()).isEqualTo(5); assertThat(after.selectedCandidateDateId()).isNull();
		assertThat(after.candidateDates()).extracting(CandidateDate::id).containsExactly("a", "b");
		assertThat(before.pollRevision()).isEqualTo(4); assertThat(before.selectedCandidateDateId()).isEqualTo("b");
	}
	@Test void changingATimeInvalidatesTheEntireRound() {
		var before = poll();
		var after = before.replaceCandidates(List.of(edits(before).get(0), new CandidateDateEdit("b", DATE.plusDays(1), LocalTime.NOON)), 4L, LIMITS);
		assertThat(after.pollRevision()).isEqualTo(5); assertThat(after.selectedCandidateDateId()).isNull();
	}
	@Test void removingATimeAlsoInvalidatesTheRound() {
		var before = poll();
		assertThat(before.replaceCandidates(List.of(new CandidateDateEdit("a", DATE, null), edits(before).get(1)), 4L, LIMITS).pollRevision()).isEqualTo(5);
	}
	@Test void addingAndRemovingCandidatesAdvancesOneRoundForTheWholeReplacement() {
		var after = poll().replaceCandidates(List.of(new CandidateDateEdit("a", DATE, LocalTime.of(20, 0)), new CandidateDateEdit(null, DATE.plusDays(3), null)), 4L, LIMITS);
		assertThat(after.pollRevision()).isEqualTo(5); assertThat(after.selectedCandidateDateId()).isNull();
		assertThat(after.candidateDates().get(0).id()).isEqualTo("a");
		assertThat(after.candidateDates().get(1).id()).isNotBlank().isNotIn("a", "b");
	}
	@Test void repeatingAConfigurationIsANoOpWithOrWithoutCandidateIds() {
		var before = poll();
		assertThat(before.replaceCandidates(edits(before), 4L, LIMITS)).isSameAs(before);
		var withoutIds = before.candidateDates().stream().map(candidate -> new CandidateDateEdit(null, candidate.date(), candidate.startTime())).toList();
		assertThat(before.replaceCandidates(withoutIds, 4L, LIMITS)).isSameAs(before);
	}
	@Test void reorderingKeepsRevisionAndSelectionButChangesDisplayOrder() {
		var before = poll(); var after = before.replaceCandidates(edits(before).reversed(), 4L, LIMITS);
		assertThat(after.pollRevision()).isEqualTo(4); assertThat(after.selectedCandidateDateId()).isEqualTo("b");
		assertThat(after.candidateDates()).containsExactlyElementsOf(before.candidateDates().reversed());
	}
	@Test void replayingASuccessfulChangeWithTheOldRevisionPreservesTheGeneratedIds() {
		var request = List.of(new CandidateDateEdit(null, DATE.plusDays(2), null), new CandidateDateEdit(null, DATE.plusDays(3), LocalTime.NOON));
		var after = poll().replaceCandidates(request, 4L, LIMITS);
		assertThat(after.replaceCandidates(request, 4L, LIMITS)).isSameAs(after);
	}
	@Test void swappingDatesBetweenCandidateIdentitiesIsARealChange() {
		var after = poll().replaceCandidates(List.of(new CandidateDateEdit("a", DATE.plusDays(1), null), new CandidateDateEdit("b", DATE, LocalTime.of(20, 0))), 4L, LIMITS);
		assertThat(after.pollRevision()).isEqualTo(5); assertThat(after.selectedCandidateDateId()).isNull();
	}
	@Test void anOldRevisionCannotIntroduceFurtherChanges() {
		assertThatThrownBy(() -> poll().replaceCandidates(List.of(new CandidateDateEdit("a", DATE.plusDays(2), null), edits(poll()).get(1)), 3L, LIMITS)).isInstanceOf(StalePollRevisionException.class);
	}
	@Test void anOldRevisionCannotReuseRemovedCandidateIds() {
		var before = poll();
		var after = before.replaceCandidates(List.of(new CandidateDateEdit(null, DATE.plusDays(2), null), edits(before).get(1)), 4L, LIMITS);
		assertThatThrownBy(() -> after.replaceCandidates(edits(before), 4L, LIMITS)).isInstanceOf(StalePollRevisionException.class);
	}
	@Test void aFutureRevisionIsRejectedEvenForIdenticalContent() {
		assertThatThrownBy(() -> poll().replaceCandidates(edits(poll()), 5L, LIMITS)).isInstanceOf(StalePollRevisionException.class);
		assertThatThrownBy(() -> poll().replaceCandidates(List.of(new CandidateDateEdit("unknown", DATE, null), edits(poll()).get(1)), 5L, LIMITS)).isInstanceOf(StalePollRevisionException.class);
	}
	@Test void theRevisionIsRequired() { assertThatThrownBy(() -> poll().replaceCandidates(edits(poll()), null, LIMITS)).isInstanceOf(InvalidPollRevisionException.class); }
	@Test void negativeRevisionsAreRejected() { assertThatThrownBy(() -> poll().replaceCandidates(edits(poll()), -1L, LIMITS)).isInstanceOf(InvalidPollRevisionException.class); }
	@Test void unknownCandidateIdsAreRejected() {
		assertThatThrownBy(() -> poll().replaceCandidates(List.of(new CandidateDateEdit("unknown", DATE, null), edits(poll()).get(1)), 4L, LIMITS)).isInstanceOf(UnknownCandidateDateIdException.class);
	}
	@Test void duplicateCandidateIdentitiesAreRejected() {
		assertThatThrownBy(() -> poll().replaceCandidates(List.of(new CandidateDateEdit("a", DATE, null), new CandidateDateEdit("a", DATE.plusDays(2), null)), 4L, LIMITS)).isInstanceOf(InvalidCandidateDateIdException.class);
	}
	@Test void duplicateDatesAreRejectedEvenWithDifferentTimes() {
		assertThatThrownBy(() -> poll().replaceCandidates(List.of(new CandidateDateEdit("a", DATE, null), new CandidateDateEdit("b", DATE, LocalTime.NOON)), 4L, LIMITS)).isInstanceOf(DuplicateCandidateDateException.class);
	}
	@ParameterizedTest @ValueSource(ints = {0, 1, 11})
	void replacementsRespectTheConfiguredSize(int count) {
		var request = IntStream.range(0, count).mapToObj(index -> new CandidateDateEdit(null, DATE.plusDays(index), null)).toList();
		assertThatThrownBy(() -> poll().replaceCandidates(request, 4L, LIMITS)).isInstanceOf(InvalidCandidateDateCountException.class);
	}
	@Test void aNullListIsRejected() { assertThatThrownBy(() -> poll().replaceCandidates(null, 4L, LIMITS)).isInstanceOf(InvalidCandidateDateCountException.class); }
	@Test void aNullCandidateIsRejected() { assertThatThrownBy(() -> poll().replaceCandidates(Arrays.asList(null, edits(poll()).get(1)), 4L, LIMITS)).isInstanceOf(MissingCandidateDateException.class); }
	@Test void aMissingDateIsRejected() { assertThatThrownBy(() -> poll().replaceCandidates(List.of(new CandidateDateEdit("a", null, null), edits(poll()).get(1)), 4L, LIMITS)).isInstanceOf(MissingCandidateDateException.class); }
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = {"DRAFT", "PUBLISHED"})
	void draftAndPublishedPollsCanChangeCandidatesWithoutConfusingTheTwoVersions(EventStatus status) {
		Event event = event(status);
		event.replaceCandidateDates(List.of(new CandidateDateEdit("a", DATE.plusDays(2), null), edits(poll()).get(1)), 4L, LIMITS);
		assertThat(((DatePollSchedule) event.getSchedule()).pollRevision()).isEqualTo(5);
		assertThat(event.getVersion()).isEqualTo(75L);
	}
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = {"CANCELLED", "FINISHED"})
	void closedEventsRejectCandidateChanges(EventStatus status) {
		assertThatThrownBy(() -> event(status).replaceCandidateDates(edits(poll()), 4L, LIMITS)).isInstanceOf(EventNotEditableException.class);
	}
	@Test void fixedDateEventsRejectCandidateChanges() {
		Event fixed = Event.builder().hostId("host").title("Cena").startsAt(DATE.atTime(18, 0)).endsAt(DATE.atTime(20, 0))
				.timeZone("UTC").modality(Modality.IN_PERSON).venue(new Venue("Sala", "Dirección", 0.0, 0.0, "p", null)).build();
		assertThatThrownBy(() -> fixed.replaceCandidateDates(edits(poll()), 0L, LIMITS)).isInstanceOf(EventScheduleConflictException.class);
	}
	@Test void generalEditingPreservesRevisionAndSelection() {
		var event = event(EventStatus.DRAFT);
		event.edit(new EventEdit("Cena editada", null, null, null, "America/Bogota", Modality.IN_PERSON, null, null,
				false, true, DATE.minusDays(1).atTime(18, 0), EventType.DATE_POLL));
		var schedule = (DatePollSchedule) event.getSchedule();
		assertThat(schedule.pollRevision()).isEqualTo(4); assertThat(schedule.selectedCandidateDateId()).isEqualTo("b");
		assertThat(schedule.candidateDates()).isEqualTo(poll().candidateDates());
	}
	@Test void changingCandidatesDoesNotExtendAnExpiredDeadline() {
		Event event = event(EventStatus.PUBLISHED); var deadline = event.getResponseDeadline();
		event.replaceCandidateDates(List.of(new CandidateDateEdit("a", DATE.plusDays(2), null), edits(poll()).get(1)), 4L, LIMITS);
		assertThat(event.getResponseDeadline()).isEqualTo(deadline);
		assertThat(event.acceptsResponses(Instant.parse("2027-05-10T12:00:00Z"))).isFalse();
	}
	@Test void theHostCanReopenAPublishedPollBySettingANewMandatoryDeadline() {
		Event event = event(EventStatus.PUBLISHED); Instant now = Instant.parse("2027-05-10T12:00:00Z");
		assertThat(event.acceptsResponses(now)).isFalse();
		event.changeResponseDeadline(DATE.plusDays(3).atTime(18, 0), now);
		assertThat(event.acceptsResponses(now)).isTrue();
		assertThatThrownBy(() -> event.changeResponseDeadline(null, now)).isInstanceOf(MissingDatePollDeadlineException.class);
	}
	@Test void unknownSelectionsAndNegativeStoredRevisionsAreInvalid() {
		assertThatThrownBy(() -> new DatePollSchedule(poll().candidateDates(), "UTC", 0, "unknown")).isInstanceOf(UnknownCandidateDateIdException.class);
		assertThatThrownBy(() -> new DatePollSchedule(poll().candidateDates(), "UTC", -1, null)).isInstanceOf(InvalidPollRevisionException.class);
	}
}
