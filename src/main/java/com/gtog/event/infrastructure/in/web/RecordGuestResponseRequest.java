package com.gtog.event.infrastructure.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta del invitado. El correo se guarda; todavia no se envia ninguna confirmacion")
public record RecordGuestResponseRequest(String responseOptionId,
		@Schema(description = "Maximo 500 caracteres; blanco equivale a ausente") String comment,
		@Schema(description = "Correo opcional, solo se almacena en la respuesta") String email) {
	@Override
	public String toString() { return "RecordGuestResponseRequest[redacted]"; }
}
