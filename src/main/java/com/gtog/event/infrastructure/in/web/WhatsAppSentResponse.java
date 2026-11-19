package com.gtog.event.infrastructure.in.web;

import java.util.List;
import com.gtog.event.domain.model.WhatsAppConfirmationResult;
import com.gtog.event.domain.model.WhatsAppConfirmationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record WhatsAppSentResponse(List<Result> results) {
	public static WhatsAppSentResponse from(List<WhatsAppConfirmationResult> results) {
		return new WhatsAppSentResponse(results.stream().map(r -> new Result(r.guestId(), r.status())).toList());
	}
	public record Result(String guestId,
			@Schema(description = "MARKED registra el primer envío; ALREADY_SENT/ALREADY_OPENED conservan sus datos; NO_PHONE no escribe; NOT_FOUND indica retirada durante el lote.")
			WhatsAppConfirmationStatus status) {}
}
