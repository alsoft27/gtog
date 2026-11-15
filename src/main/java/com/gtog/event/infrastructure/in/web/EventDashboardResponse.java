package com.gtog.event.infrastructure.in.web;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import com.gtog.event.domain.model.DeliveryChannel;
import com.gtog.event.domain.model.EventAttendanceSummary;
import com.gtog.event.domain.model.InvitationStatus;

@Schema(description = "Panel privado del anfitrion: contactos de invitados y respuestas, sin email de respuesta")
public record EventDashboardResponse(Totals totals, List<OptionCount> byOption, List<GuestRow> guests) {
	public record Totals(int guests, int answered, int pending, int attending) {}
	public record OptionCount(String optionId, String label, boolean countsAsAttendance, int count) {}
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record Response(String responseOptionId, String label, String comment, Instant answeredAt, Instant updatedAt) {}
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record GuestRow(String id, String name, String email, String phoneE164, DeliveryChannel deliveryChannel,
			InvitationStatus invitationStatus, String invitationPath, boolean answered, Response response,
			@Schema(description = "true: opcion existente; false: opcion desconocida; null: sin respuesta", nullable = true) Boolean optionKnown) {}

	public static EventDashboardResponse from(EventAttendanceSummary summary) {
		var totals = summary.totals();
		return new EventDashboardResponse(new Totals(totals.guests(), totals.answered(), totals.pending(), totals.attending()),
				summary.byOption().stream().map(option -> new OptionCount(option.optionId(), option.label(), option.countsAsAttendance(), option.count())).toList(),
				summary.guests().stream().map(row -> {
					var guest = GuestInvitationResponse.from(row.guest());
					var response = row.response();
					return new GuestRow(guest.id(), guest.name(), guest.email(), guest.phoneE164(), guest.deliveryChannel(),
							guest.invitationStatus(), guest.invitationPath(), row.answered(), response == null ? null :
							new Response(response.responseOptionId(), response.label(), response.comment(), response.answeredAt(), response.updatedAt()), row.optionKnown());
				}).toList());
	}
}
