package com.gtog.event.infrastructure.in.web;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import com.gtog.event.domain.model.DatePollSchedule;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Consulta de disponibilidad: candidatas ordenadas sin hora de fin y revisión propia")
public record DatePollResponse(List<Candidate> candidateDates,
		@Schema(description = "Revisión de candidatas desde cero, independiente de version del evento") long pollRevision,
		@Schema(description = "Campo reservado para la candidata seleccionada") String selectedCandidateDateId) {
	public record Candidate(String id, LocalDate date, LocalTime startTime) {}
	public static DatePollResponse from(DatePollSchedule poll) {
		return new DatePollResponse(poll.candidateDates().stream()
				.map(candidate -> new Candidate(candidate.id(), candidate.date(), candidate.startTime())).toList(), poll.pollRevision(), poll.selectedCandidateDateId());
	}
}
