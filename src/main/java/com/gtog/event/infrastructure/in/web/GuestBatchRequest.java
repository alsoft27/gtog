package com.gtog.event.infrastructure.in.web;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record GuestBatchRequest(@Schema(description = "Altas en orden; lista obligatoria, no vacía. Máximo configurable, 100 por defecto.") List<GuestRequest> guests) {
	@Override public String toString() { return "GuestBatchRequest[redacted]"; }
}
