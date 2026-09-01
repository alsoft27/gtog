package com.gtog.event.infrastructure.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record CancelEventRequest(

		@Schema(description = "Motivo de la cancelacion, opcional", example = "Imprevisto del organizador")
		String reason) {
}
