package com.gtog.event.infrastructure.in.web;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import com.gtog.event.domain.model.DatePollSchedule;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Candidatas ordenadas; no existe una fecha definitiva ni una hora de fin")
public record DatePollResponse(List<Candidate> candidateDates) {
	public record Candidate(String id, LocalDate date, LocalTime startTime) {}
	public static DatePollResponse from(DatePollSchedule poll) {
		return new DatePollResponse(poll.candidateDates().stream()
				.map(candidate -> new Candidate(candidate.id(), candidate.date(), candidate.startTime())).toList());
	}
}
