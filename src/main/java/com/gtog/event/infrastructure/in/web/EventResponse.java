package com.gtog.event.infrastructure.in.web;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.Modality;

public record EventResponse(

		@Schema(description = "Identificador del evento", example = "68a1f2c9e4b0a1234567890a")
		String id,

		@Schema(description = "Identificador del anfitrion que creo el evento", example = "host-1")
		String hostId,

		@Schema(description = "Titulo del evento", example = "Cumpleaños de Ana")
		String title,

		@Schema(description = "Descripcion opcional del evento", example = "Trae algo para compartir")
		String description,

		@Schema(description = "Fecha y hora de inicio, en la zona horaria del evento", example = "2026-09-01T20:00:00")
		LocalDateTime startsAt,

		@Schema(description = "Fecha y hora de fin", example = "2026-09-01T23:00:00")
		LocalDateTime endsAt,

		@Schema(description = "Zona horaria del evento en formato IANA (region/ciudad)", example = "Europe/Madrid")
		String timeZone,

		@Schema(description = "Modalidad del evento: presencial o en linea", example = "IN_PERSON")
		Modality modality,

		@Schema(description = "Estado del evento", example = "DRAFT")
		EventStatus status,

		@Schema(description = "Opciones de respuesta del evento, en el orden en que se muestran al invitado")
		List<ResponseOptionResponse> responseOptions,

		@Schema(description = "Si se permite a los invitados dejar un comentario junto a su respuesta",
				example = "false")
		boolean allowComment,

		@Schema(description = "Si un invitado puede cambiar su respuesta una vez enviada", example = "true")
		boolean allowResponseChange,

		@Schema(description = "Fecha limite para responder, en la zona horaria del evento", example = "2026-08-30T23:59:59")
		LocalDateTime responseDeadline) {

	public static EventResponse from(Event event) {
		return new EventResponse(
				event.getId(),
				event.getHostId(),
				event.getTitle(),
				event.getDescription(),
				event.getStartsAt(),
				event.getEndsAt(),
				event.getTimeZone(),
				event.getModality(),
				event.getStatus(),
				event.getResponseOptions().stream().map(ResponseOptionResponse::from).toList(),
				event.isAllowComment(),
				event.isAllowResponseChange(),
				event.getResponseDeadline());
	}
}
