package com.gtog.event.infrastructure.in.web;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

public record ChangeResponseDeadlineRequest(
		@Schema(description = "Plazo en hora local del evento. null elimina el plazo; un plazo pasado lo cierra. "
				+ "No puede ser posterior al inicio.", example = "2027-01-01T17:00:00") LocalDateTime responseDeadline) {}
