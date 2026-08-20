package com.gtog.event.infrastructure.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

// Sin @NotBlank en label, por el mismo motivo que CreateResponseOptionRequest: la validacion de negocio vive
// en el dominio y debe llegar a el como 422, no cortarse antes como 400.
public record ResponseOptionRequest(

		@Schema(description = "Id de una opcion existente para conservarla al renombrarla; omitido o null para "
				+ "una opcion nueva. Un id que no exista en el evento se rechaza.", example = "3f9a2b6e-1a2b-4c3d-9e8f-1234567890ab")
		String id,

		@Schema(description = "Texto que ve el invitado para esta opcion", example = "Asisto")
		String label,

		@Schema(description = "Si esta opcion cuenta como asistencia confirmada", example = "true")
		boolean countsAsAttendance) {
}
