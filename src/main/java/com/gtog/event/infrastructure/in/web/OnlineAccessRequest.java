package com.gtog.event.infrastructure.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

import com.gtog.event.domain.model.LinkVisibility;

// Sin anotaciones Jakarta en los campos obligatorios: son regla de dominio (MissingOnlineAccessFieldException,
// InvalidOnlineAccessUrlException, 422), no de formato.
public record OnlineAccessRequest(

		@Schema(description = "Plataforma de videollamada", example = "Zoom")
		String platform,

		@Schema(description = "Enlace de la reunion, debe ser http o https", example = "https://zoom.us/j/123456789")
		String url,

		@Schema(description = "Identificador de la sala, opcional", example = "123 456 789")
		String roomId,

		@Schema(description = "Contrasena de la sala, opcional", example = "cumple2026")
		String password,

		@Schema(description = "Instrucciones adicionales, opcional", example = "Espera en la sala hasta que te admitan")
		String instructions,

		@Schema(description = "Cuando se le muestra el enlace al invitado")
		LinkVisibility linkVisibility,

		@Schema(description = "Horas de antelacion con las que se muestra el enlace. Obligatorio y mayor que cero "
				+ "solo si linkVisibility es HOURS_BEFORE; en cualquier otro caso no debe enviarse.", example = "24")
		Integer hoursBefore) {
}
