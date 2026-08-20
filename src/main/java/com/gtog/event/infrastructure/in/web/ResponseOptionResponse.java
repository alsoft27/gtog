package com.gtog.event.infrastructure.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

import com.gtog.event.domain.model.ResponseOption;

public record ResponseOptionResponse(

		@Schema(description = "Identificador de la opcion, generado por el dominio",
				example = "3f9a2b6e-1a2b-4c3d-9e8f-1234567890ab")
		String id,

		@Schema(description = "Texto que ve el invitado para esta opcion", example = "Asisto")
		String label,

		@Schema(description = "Si esta opcion cuenta como asistencia confirmada", example = "true")
		boolean countsAsAttendance) {

	public static ResponseOptionResponse from(ResponseOption responseOption) {
		return new ResponseOptionResponse(responseOption.id(), responseOption.label(),
				responseOption.countsAsAttendance());
	}
}
