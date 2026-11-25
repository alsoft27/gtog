package com.gtog.event.infrastructure.in.web;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record RecordAvailabilityRequest(Long pollRevision,
		@Schema(description = "IDs de candidatas disponibles; una lista vacía indica ninguna") List<String> candidateDateIds) {}
