package com.gtog.event.infrastructure.in.web;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import com.gtog.event.domain.model.CandidateDateDraft;
import com.gtog.event.domain.model.DatePollDraft;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Consulta de disponibilidad; candidatas sin hora de fin")
public record DatePollRequest(List<Candidate> candidateDates) {
	public record Candidate(@Schema(description = "Fecha obligatoria") LocalDate date,
			@Schema(description = "Hora de inicio opcional, en la zona del evento") LocalTime startTime) {}
	public DatePollDraft toDomain() {
		return new DatePollDraft(candidateDates == null ? null : candidateDates.stream()
				.map(candidate -> candidate == null ? null : new CandidateDateDraft(candidate.date(), candidate.startTime())).toList());
	}
}
