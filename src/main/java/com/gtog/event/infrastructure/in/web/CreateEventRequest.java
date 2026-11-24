package com.gtog.event.infrastructure.in.web;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

import com.gtog.event.domain.model.Modality;
import com.gtog.event.domain.model.EventType;

@ValidFixedDateFields
public record CreateEventRequest(

		// Sin @NotBlank a proposito: el titulo obligatorio es una regla de negocio (BlankEventTitleException, 422
		// del dominio), no un error de formato (400). Ver com.gtog.event.domain.model.Event.create.
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
				+ "a startsAt para FIXED_DATE. Obligatorio para DATE_POLL.", example = "2026-08-30T23:59:59")
		LocalDateTime responseDeadline,

		@Schema(description = "Ubicacion del evento. Obligatoria si modality es IN_PERSON, prohibida si es ONLINE.")
		VenueRequest venue,

		@Schema(description = "Acceso en linea del evento. Obligatorio si modality es ONLINE, prohibido si es "
				+ "IN_PERSON.")
		OnlineAccessRequest onlineAccess,

		@Schema(description = "Solicita crear el enlace público al publicar; false por defecto")
		Boolean publicInvitationEnabled,
		@Schema(description = "FIXED_DATE por defecto; DATE_POLL no admite startsAt ni endsAt") EventType eventType,
		@Schema(description = "Obligatorio para DATE_POLL; prohibido para FIXED_DATE") DatePollRequest datePoll) implements TemporalEventRequest {
}
