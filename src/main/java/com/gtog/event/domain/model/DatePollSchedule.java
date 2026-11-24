package com.gtog.event.domain.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record DatePollSchedule(List<CandidateDate> candidateDates, String timeZone) implements EventSchedule {
	public DatePollSchedule {
		EventSchedule.zone(timeZone);
		if (candidateDates == null || candidateDates.size() < 2) { throw new InvalidCandidateDateCountException(); }
		if (candidateDates.stream().anyMatch(candidate -> candidate == null)) { throw new MissingCandidateDateException(); }
		if (candidateDates.stream().map(CandidateDate::date).distinct().count() != candidateDates.size()) {
			throw new DuplicateCandidateDateException();
		}
		if (candidateDates.stream().map(CandidateDate::id).distinct().count() != candidateDates.size()) {
			throw new InvalidCandidateDateIdException();
		}
		candidateDates = List.copyOf(candidateDates);
	}
	public static DatePollSchedule create(DatePollDraft draft, String timeZone, DatePollLimits limits) {
		if (draft == null || draft.candidateDates() == null || draft.candidateDates().size() < 2
				|| draft.candidateDates().size() > limits.maxCandidates()) { throw new InvalidCandidateDateCountException(); }
		return new DatePollSchedule(draft.candidateDates().stream().map(CandidateDate::create).toList(), timeZone);
	}
	@Override public EventType type() { return EventType.DATE_POLL; }
	@Override public boolean hasEnded(Instant now) { return false; } // No existe todavía un fin definitivo.
	@Override public void validateResponseDeadline(LocalDateTime deadline) {
		if (deadline == null) { throw new MissingDatePollDeadlineException(); }
	}
}
