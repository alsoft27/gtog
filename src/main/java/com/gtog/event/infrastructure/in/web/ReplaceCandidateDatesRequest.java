package com.gtog.event.infrastructure.in.web;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import com.gtog.event.domain.model.CandidateDateEdit;
import io.swagger.v3.oas.annotations.media.Schema;

public record ReplaceCandidateDatesRequest(
		@Schema(description = "Revisión leída del evento; no es version del documento. Obligatoria, desde cero.") Long pollRevision,
		@Schema(description = "Lista completa, en el orden deseado. Cambiar fecha, hora, alta o baja invalida la disponibilidad anterior.") List<Candidate> candidateDates) {
	public record Candidate(@Schema(description = "Id existente, o null para una candidata nueva") String id,
			@Schema(description = "Fecha obligatoria") LocalDate date,
			@Schema(description = "Hora opcional en la zona del evento, sin hora de fin") LocalTime startTime) {}
	public List<CandidateDateEdit> toDomain() {
		return candidateDates == null ? null : candidateDates.stream().map(candidate -> candidate == null ? null :
				new CandidateDateEdit(candidate.id(), candidate.date(), candidate.startTime())).toList();
	}
}
