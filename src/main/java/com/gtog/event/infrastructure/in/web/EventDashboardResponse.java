package com.gtog.event.infrastructure.in.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import com.gtog.event.domain.model.DeliveryChannel;
import com.gtog.event.domain.model.EventAttendanceSummary;
import com.gtog.event.domain.model.EventAvailabilitySummary;
import com.gtog.event.domain.model.InvitationStatus;

@Schema(description = "Panel privado del anfitrion: contactos de invitados y respuestas, sin email de respuesta")
public record EventDashboardResponse(Totals totals, List<OptionCount> byOption, List<GuestRow> guests,
		PublicInvitationInfo publicInvitation, @JsonInclude(JsonInclude.Include.NON_NULL) Availability availability) {
	@Schema(description = "Desglose de disponibilidad de la ronda vigente, solo para DATE_POLL")
	public record Availability(long pollRevision, List<CandidateAvailability> byCandidate,
			List<GuestReference> pendingGuests, List<GuestReference> unavailableGuests) {
		static Availability from(EventAvailabilitySummary summary) {
			if (summary == null) { return null; }
			var candidates = summary.byCandidate().stream().map(entry -> {
				var date = entry.candidate();
				return new CandidateAvailability(date.id(), date.date(), date.startTime(), entry.guests().size(),
						guestReferences(entry.guests()));
			}).toList();
			return new Availability(summary.pollRevision(), candidates, guestReferences(summary.pendingGuests()),
					guestReferences(summary.unavailableGuests()));
		}

		private static List<GuestReference> guestReferences(List<EventAvailabilitySummary.GuestRef> guests) {
			return guests.stream().map(guest -> new GuestReference(guest.id(), guest.name())).toList();
		}
	}

	public record CandidateAvailability(String candidateDateId, LocalDate date, LocalTime startTime,
			int count, List<GuestReference> guests) {}
	public record GuestReference(String id, String name) {}
	public record PublicInvitationInfo(boolean enabled, long joined, int limit, boolean open) {}
	public record Totals(int guests, int answered, int pending, int attending) {}
	public record OptionCount(String optionId, String label, boolean countsAsAttendance, int count) {}
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record Response(String responseOptionId, String label, String comment, Instant answeredAt, Instant updatedAt) {}
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record GuestRow(String id, String name, String email, String phoneE164, DeliveryChannel deliveryChannel,
			InvitationStatus invitationStatus, String invitationPath, com.gtog.event.domain.model.GuestSource source, boolean answered, Response response,
			@Schema(description = "true: opcion existente; false: opcion desconocida; null: sin respuesta", nullable = true) Boolean optionKnown) {}

	public static EventDashboardResponse from(EventAttendanceSummary summary) {
		var totals = summary.totals();
		return new EventDashboardResponse(new Totals(totals.guests(), totals.answered(), totals.pending(), totals.attending()),
				summary.byOption().stream().map(option -> new OptionCount(option.optionId(), option.label(), option.countsAsAttendance(), option.count())).toList(),
				summary.guests().stream().map(row -> {
					var guest = GuestInvitationResponse.from(row.guest());
					var response = row.response();
					return new GuestRow(guest.id(), guest.name(), guest.email(), guest.phoneE164(), guest.deliveryChannel(),
							guest.invitationStatus(), guest.invitationPath(), guest.source(), row.answered(), response == null ? null :
							new Response(response.responseOptionId(), response.label(), response.comment(), response.answeredAt(), response.updatedAt()), row.optionKnown());
				}).toList(), new PublicInvitationInfo(summary.publicInvitation().enabled(), summary.publicInvitation().joined(),
						summary.publicInvitation().limit(), summary.publicInvitation().open()), Availability.from(summary.availability()));
	}
}
