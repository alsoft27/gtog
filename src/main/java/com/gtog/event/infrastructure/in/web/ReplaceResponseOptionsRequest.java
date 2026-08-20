package com.gtog.event.infrastructure.in.web;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReplaceResponseOptionsRequest(

		@Schema(description = "Lista completa de opciones de respuesta que sustituye a la actual, entre 2 y 5")
		@NotNull List<@Valid ResponseOptionRequest> responseOptions,

		@Schema(description = "Si se permite a los invitados dejar un comentario junto a su respuesta",
				example = "false")
		boolean allowComment,

		@Schema(description = "Si un invitado puede cambiar su respuesta una vez enviada", example = "true")
		boolean allowResponseChange,

		@Schema(description = "Fecha limite para responder, en la zona horaria del evento; no puede ser posterior "
				+ "a startsAt", example = "2026-08-30T23:59:59")
		LocalDateTime responseDeadline) {
}
