package com.gtog.event.infrastructure.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

// Sin @NotBlank en label a proposito: una etiqueta vacia es una regla de negocio (422 del dominio), no un
// error de formato (400). Si Jakarta la bloqueara antes, el dominio nunca la vería y no habría forma de probarla.
public record CreateResponseOptionRequest(

		@Schema(description = "Texto que ve el invitado para esta opcion", example = "Asisto")
		String label,

		@Schema(description = "Si esta opcion cuenta como asistencia confirmada", example = "true")
		boolean countsAsAttendance) {
}
