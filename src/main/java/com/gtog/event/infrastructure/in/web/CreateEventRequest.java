package com.gtog.event.infrastructure.in.web;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

import com.gtog.event.domain.model.Modality;

public record CreateEventRequest(

		@Schema(description = "Identificador del anfitrion que crea el evento", example = "host-1")
		@NotBlank String hostId,

		// Sin @NotBlank a proposito: el titulo obligatorio es una regla de negocio (BlankEventTitleException, 422
		// del dominio), no un error de formato (400). Ver com.gtog.event.domain.model.Event.create.
		@Schema(description = "Titulo del evento", example = "Cumpleaños de Ana")
		String title,

		@Schema(description = "Descripcion opcional del evento", example = "Trae algo para compartir")
		String description,

		@Schema(description = "Fecha y hora de inicio, en la zona horaria del evento", example = "2026-09-01T20:00:00")
		@NotNull LocalDateTime startsAt,

		@Schema(description = "Fecha y hora de fin, debe ser posterior a startsAt", example = "2026-09-01T23:00:00")
		@NotNull LocalDateTime endsAt,

		@Schema(description = "Zona horaria del evento en formato IANA (region/ciudad)", example = "Europe/Madrid")
		@NotBlank String timeZone,

		@Schema(description = "Modalidad del evento: presencial o en linea", example = "IN_PERSON")
		@NotNull Modality modality,

		@Schema(description = "Opciones de respuesta, entre 2 y 5. Si se omite, el evento se crea con dos opciones "
				+ "por defecto: \"Asisto\" (cuenta como asistencia) y \"No asisto\" (no cuenta).")
		List<@Valid CreateResponseOptionRequest> responseOptions,

		@Schema(description = "Si se permite a los invitados dejar un comentario junto a su respuesta. Por defecto "
				+ "false: es un dato personal del invitado que el anfitrion debe activar de forma consciente.",
				example = "false")
		Boolean allowComment,

		@Schema(description = "Si un invitado puede cambiar su respuesta una vez enviada. Por defecto true.",
				example = "true")
		Boolean allowResponseChange,

		@Schema(description = "Fecha limite para responder, en la zona horaria del evento; no puede ser posterior "
				+ "a startsAt. Sin valor por defecto.", example = "2026-08-30T23:59:59")
		LocalDateTime responseDeadline) {
}
