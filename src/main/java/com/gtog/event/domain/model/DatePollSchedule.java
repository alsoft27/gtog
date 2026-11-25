package com.gtog.event.domain.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;

public record DatePollSchedule(List<CandidateDate> candidateDates, String timeZone, long pollRevision, String selectedCandidateDateId, Long noticeClaimedRevision) implements EventSchedule {
	public DatePollSchedule(List<CandidateDate> candidateDates, String timeZone) { this(candidateDates, timeZone, 0, null); }

	public DatePollSchedule(List<CandidateDate> candidateDates, String timeZone, long pollRevision, String selectedCandidateDateId) {
		this(candidateDates, timeZone, pollRevision, selectedCandidateDateId, null);
	}

	public DatePollSchedule {
		if (pollRevision < 0 || (noticeClaimedRevision != null && (noticeClaimedRevision < 0 || noticeClaimedRevision > pollRevision))) { throw new InvalidPollRevisionException(); }
		EventSchedule.zone(timeZone);
		if (candidateDates == null || candidateDates.size() < 2) { throw new InvalidCandidateDateCountException(); }
		if (candidateDates.stream().anyMatch(candidate -> candidate == null)) { throw new MissingCandidateDateException(); }
		if (candidateDates.stream().map(CandidateDate::date).distinct().count() != candidateDates.size()) {
			throw new DuplicateCandidateDateException();
		}
		if (candidateDates.stream().map(CandidateDate::id).distinct().count() != candidateDates.size()) {
			throw new InvalidCandidateDateIdException();
		}
		if (selectedCandidateDateId != null && candidateDates.stream().noneMatch(candidate -> candidate.id().equals(selectedCandidateDateId))) {
			throw new UnknownCandidateDateIdException();
		}
		candidateDates = List.copyOf(candidateDates);
	}
	public static DatePollSchedule create(DatePollDraft draft, String timeZone, DatePollLimits limits) {
		if (draft == null || draft.candidateDates() == null) { throw new InvalidCandidateDateCountException(); }
		limits.validateCount(draft.candidateDates().size());
		return new DatePollSchedule(draft.candidateDates().stream().map(CandidateDate::create).toList(), timeZone);
	}
	public DatePollSchedule withTimeZone(String zone) {
		return new DatePollSchedule(candidateDates, zone, pollRevision, selectedCandidateDateId, noticeClaimedRevision);
	}

	public DatePollSchedule replaceCandidates(List<CandidateDateEdit> edits, Long expectedRevision, DatePollLimits limits) {
		if (expectedRevision == null || expectedRevision < 0) { throw new InvalidPollRevisionException(); }
		if (expectedRevision > pollRevision) { throw new StalePollRevisionException(); }
		if (edits == null) { throw new InvalidCandidateDateCountException(); }
		limits.validateCount(edits.size());
		List<CandidateDate> resolved;
		try {
			resolved = edits.stream().map(this::resolve).toList();
		} catch (UnknownCandidateDateIdException exception) {
			// Una pantalla antigua puede referenciar una candidata eliminada: necesita recargar la ronda.
			if (expectedRevision < pollRevision) { throw new StalePollRevisionException(); }
			throw exception;
		}
		// La validación estructural se comparte con creación y rehidratación, sin cambiar todavía la revisión.
		DatePollSchedule candidate = new DatePollSchedule(resolved, timeZone, pollRevision, null);
		boolean changed = !new HashSet<>(candidateDates).equals(new HashSet<>(candidate.candidateDates()));
		if (changed && expectedRevision != pollRevision) { throw new StalePollRevisionException(); }
		if (!changed && candidateDates.equals(resolved)) { return this; }
		return new DatePollSchedule(resolved, timeZone, changed ? Math.incrementExact(pollRevision) : pollRevision,
				changed ? null : selectedCandidateDateId, noticeClaimedRevision);
	}

	public boolean noticeClaimed() { return noticeClaimedRevision != null && noticeClaimedRevision == pollRevision; }

	private CandidateDate resolve(CandidateDateEdit edit) {
		if (edit == null) { throw new MissingCandidateDateException(); }
		if (edit.id() != null) {
			CandidateDate existing = candidateDates.stream().filter(candidate -> candidate.id().equals(edit.id())).findFirst()
					.orElseThrow(UnknownCandidateDateIdException::new);
			return new CandidateDate(existing.id(), edit.date(), edit.startTime());
		}
		// Repetir una petición sin ids conserva las identidades de las fechas que ya coinciden.
		return candidateDates.stream().filter(candidate -> candidate.date().equals(edit.date()) && Objects.equals(candidate.startTime(), edit.startTime()))
				.findFirst().orElseGet(() -> CandidateDate.create(new CandidateDateDraft(edit.date(), edit.startTime())));
	}

	@Override public EventType type() { return EventType.DATE_POLL; }
	@Override public boolean hasEnded(Instant now) { return false; } // No existe todavía un fin definitivo.
	@Override public void validateResponseDeadline(LocalDateTime deadline) {
		if (deadline == null) { throw new MissingDatePollDeadlineException(); }
	}
}
