package com.gtog.event.infrastructure.in.web;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.gtog.event.domain.model.DatePollView;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Candidatas públicas; fecha obligatoria y hora opcional, sin fin ni identidades privadas")
public record PublicDatePollResponse(List<Candidate> candidateDates, long pollRevision, String selectedCandidateDateId) {
	public record Candidate(String id, LocalDate date, LocalTime startTime) {}
	public static PublicDatePollResponse from(DatePollView view) {
		if (view == null) { return null; }
		return new PublicDatePollResponse(view.candidateDates().stream()
				.map(candidate -> new Candidate(candidate.id(), candidate.date(), candidate.startTime())).toList(),
				view.pollRevision(), view.selectedCandidateDateId());
	}
}
