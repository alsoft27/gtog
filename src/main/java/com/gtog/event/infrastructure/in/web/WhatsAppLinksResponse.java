package com.gtog.event.infrastructure.in.web;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import com.gtog.event.domain.model.*;

public record WhatsAppLinksResponse(List<Result> results) {
	public static WhatsAppLinksResponse from(List<WhatsAppLinkResult> results) {
		return new WhatsAppLinksResponse(results.stream().map(result -> new Result(result.guestId(), result.status(), result.reason(), result.url(), result.message())).toList());
	}
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Result(String guestId, WhatsAppLinkStatus status, WhatsAppLinkSkipReason reason,
			@Schema(description = "Solo READY: enlace de WhatsApp con credencial de invitación. No registrar.") String url,
			@Schema(description = "Solo READY: texto plano para vista previa y copia alternativa. No registrar.") String message) {
		@Override public String toString() { return "WhatsAppLinksResponse.Result[redacted]"; }
	}
}
