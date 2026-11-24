package com.gtog.event.infrastructure.in.web;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.EventType;
import com.gtog.event.domain.model.EventSummary;
import com.gtog.event.domain.model.Modality;

public record EventSummaryResponse(

		@Schema(description = "Identificador del evento", example = "68a1f2c9e4b0a1234567890a")
		String id,

		@Schema(description = "Titulo del evento", example = "Cumpleaños de Ana")
		String title,

		@Schema(description = "Fecha y hora de inicio, en la zona horaria del evento", example = "2026-09-01T20:00:00")
		LocalDateTime startsAt,

		@Schema(description = "Modalidad del evento: presencial o en linea", example = "IN_PERSON")
		Modality modality,

		@Schema(description = "Estado del evento", example = "DRAFT")
		EventStatus status,

		@Schema(description = "Total de invitados actuales del evento", example = "3")
		int totalGuests,

		@Schema(description = "Invitados que han respondido, incluso con una opción ya inexistente", example = "2")
		int answered,

		@Schema(description = "Invitados que todavía no han respondido", example = "1")
		int pending,

		@Schema(description = "Invitados cuya opción actual cuenta como asistencia", example = "1")
		int attending,
		@Schema(description = "Tipo temporal del evento") EventType eventType) {

	public static EventSummaryResponse from(EventSummary summary) {
		var event = summary.event();
		var totals = summary.totals();
		return new EventSummaryResponse(
				event.getId(),
				event.getTitle(),
				event.getStartsAt(),
				event.getModality(),
				event.getStatus(),
				totals.guests(),
				totals.answered(),
				totals.pending(),
				totals.attending(), event.getEventType());
	}
}
