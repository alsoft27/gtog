package com.gtog.event.infrastructure.in.web;

import java.time.LocalDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

import com.gtog.event.domain.model.Modality;
import com.gtog.event.domain.model.EventType;

@ValidFixedDateFields
public record UpdateEventRequest(

		// Sin @NotBlank: el titulo obligatorio es una regla de negocio (BlankEventTitleException, 422).
		@Schema(description = "Titulo del evento", example = "Cumpleaños de Ana")
		String title,

		@Schema(description = "Descripcion opcional del evento", example = "Trae algo para compartir")
		String description,

		@Schema(description = "Inicio obligatorio para FIXED_DATE; omitido en DATE_POLL. Hora local del evento", example = "2026-09-01T20:00:00")
		LocalDateTime startsAt,

		@Schema(description = "Fin obligatorio para FIXED_DATE y posterior a startsAt; omitido en DATE_POLL", example = "2026-09-01T23:00:00")
		LocalDateTime endsAt,

		@Schema(description = "Zona horaria del evento en formato IANA (region/ciudad)", example = "Europe/Madrid")
		@NotBlank String timeZone,

		@Schema(description = "Modalidad del evento: presencial o en linea", example = "IN_PERSON")
		@NotNull Modality modality,

		@Schema(description = "Ubicacion del evento. Obligatoria si modality cambia a IN_PERSON. "
				+ "Si modality no cambia y es IN_PERSON, actualiza el bloque existente.")
		@Valid VenueRequest venue,

		@Schema(description = "Acceso en linea. Obligatorio si modality cambia a ONLINE. "
				+ "Si modality no cambia y es ONLINE, actualiza el bloque existente.")
		@Valid OnlineAccessRequest onlineAccess,

		@Schema(description = "Si se permite a los invitados dejar un comentario junto a su respuesta. "
				+ "Por defecto false si se omite.", example = "false")
		Boolean allowComment,

		@Schema(description = "Si un invitado puede cambiar su respuesta una vez enviada. "
				+ "Por defecto true si se omite.", example = "true")
		Boolean allowResponseChange,

		@Schema(description = "Fecha limite para responder, en la zona horaria del evento; no puede ser posterior "
				+ "a startsAt en FIXED_DATE. Obligatorio para DATE_POLL", example = "2026-08-30T23:59:59")
		LocalDateTime responseDeadline,
		@Schema(description = "Tipo existente; no se puede convertir el evento. Incluir DATE_POLL al editar consultas.") EventType eventType) implements TemporalEventRequest {
}
