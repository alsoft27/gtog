package com.gtog.event.infrastructure.in.web;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import com.gtog.event.domain.model.*;

public record SendInvitationsResponse(List<Result> results, DailyLimit dailyLimit) {
	public static SendInvitationsResponse from(InvitationSendBatchResult batch) {
		return new SendInvitationsResponse(batch.results().stream().map(result -> new Result(result.guestId(), result.status(),
				result.code(), result.reason(), result.recorded())).toList(),
				new DailyLimit(batch.dailyLimit().limit(), batch.dailyLimit().remaining(), batch.dailyLimit().resetsAt()));
	}
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Result(String guestId, InvitationSendStatus status, EmailSendFailureCode code,
			@Schema(description = "UNVERIFIED_CONTACT: los invitados dados de alta por enlace no reciben correo de gtog, incluso tras editar su contacto") EmailSendSkipReason reason,
			@Schema(description = "Solo en SENT: false si SMTP aceptó pero falló registrar el estado; las reservas se conservan.") Boolean recorded) {}
	public record DailyLimit(int limit, int remaining,
			@Schema(description = "Fin UTC de la ventana vigente de 24 h; null sin ventana vigente.") Instant resetsAt) {}
}
