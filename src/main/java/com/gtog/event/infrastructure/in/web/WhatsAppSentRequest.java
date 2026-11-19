package com.gtog.event.infrastructure.in.web;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record WhatsAppSentRequest(
		@Schema(description = "Lista obligatoria, no vacía, de ids únicos del evento. Máximo configurable: 100 por defecto.", requiredMode = Schema.RequiredMode.REQUIRED)
		List<String> guestIds) {}
