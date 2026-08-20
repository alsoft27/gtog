package com.gtog.event.infrastructure.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

import com.gtog.event.domain.model.LinkVisibility;
import com.gtog.event.domain.model.OnlineAccess;

public record OnlineAccessResponse(

		@Schema(description = "Plataforma de videollamada", example = "Zoom")
		String platform,

		@Schema(description = "Enlace de la reunion", example = "https://zoom.us/j/123456789")
		String url,

		@Schema(description = "Identificador de la sala", example = "123 456 789")
		String roomId,

		@Schema(description = "Contrasena de la sala", example = "cumple2026")
		String password,

		@Schema(description = "Instrucciones adicionales", example = "Espera en la sala hasta que te admitan")
		String instructions,

		@Schema(description = "Cuando se le muestra el enlace al invitado")
		LinkVisibility linkVisibility,

		@Schema(description = "Horas de antelacion con las que se muestra el enlace, solo si linkVisibility es "
				+ "HOURS_BEFORE", example = "24")
		Integer hoursBefore) {

	public static OnlineAccessResponse from(OnlineAccess onlineAccess) {
		return new OnlineAccessResponse(onlineAccess.platform(), onlineAccess.url(), onlineAccess.roomId(),
				onlineAccess.password(), onlineAccess.instructions(), onlineAccess.linkVisibility(),
				onlineAccess.hoursBefore());
	}
}
