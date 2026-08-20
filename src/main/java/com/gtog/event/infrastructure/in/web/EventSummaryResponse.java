package com.gtog.event.infrastructure.in.web;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventStatus;
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
		EventStatus status) {

	public static EventSummaryResponse from(Event event) {
		return new EventSummaryResponse(
				event.getId(),
				event.getTitle(),
				event.getStartsAt(),
				event.getModality(),
				event.getStatus());
	}
}
