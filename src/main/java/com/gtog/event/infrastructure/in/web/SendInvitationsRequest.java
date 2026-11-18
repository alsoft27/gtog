package com.gtog.event.infrastructure.in.web;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record SendInvitationsRequest(
		@Schema(description = "Ids únicos del evento. Omitir para seleccionar todos; lista vacía inválida. Máximo configurable: 20 por defecto.")
		List<String> guestIds) {}
