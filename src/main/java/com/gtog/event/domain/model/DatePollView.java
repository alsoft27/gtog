package com.gtog.event.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// Proyección pública: solo candidatas y revisión, sin datos de persistencia ni de los invitados.
public record DatePollView(List<Candidate> candidateDates, long pollRevision, String selectedCandidateDateId) {
	public record Candidate(String id, LocalDate date, LocalTime startTime) {}
	public static DatePollView from(EventSchedule schedule) {
		if (!(schedule instanceof DatePollSchedule poll)) { return null; }
		return new DatePollView(poll.candidateDates().stream()
				.map(candidate -> new Candidate(candidate.id(), candidate.date(), candidate.startTime())).toList(),
				poll.pollRevision(), poll.selectedCandidateDateId());
	}
}
