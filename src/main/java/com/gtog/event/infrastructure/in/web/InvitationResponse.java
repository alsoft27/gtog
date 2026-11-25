package com.gtog.event.infrastructure.in.web;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.EventType;
import com.gtog.event.domain.model.InvitationView;
import com.gtog.event.domain.model.LinkVisibility;
import com.gtog.event.domain.model.Modality;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Vista publica de una invitacion, sin contactos, identidades internas ni tokens")
public record InvitationResponse(GuestName guest, String title, String description, EventStatus status,
		Modality modality, LocalDateTime startsAt, LocalDateTime endsAt, String timeZone,
		Instant startsAtInstant, Instant endsAtInstant, VenueDetails venue, List<Option> responseOptions,
		boolean allowComment, boolean allowResponseChange, LocalDateTime responseDeadline,
		Instant cancelledAt, String cancellationReason, OnlineAccessInfo onlineAccessInfo,
		@Schema(description = "Bloque omitido completamente mientras el acceso no sea visible") OnlineAccessDetails onlineAccess, MyResponse myResponse,
		@Schema(description = "Permite modificar una respuesta existente; false si todavía no ha respondido.") boolean canChangeResponse,
		@Schema(description = "Fecha fija: PUBLISHED, plazo inclusive, antes del fin y cambios habilitados si respondió. En DATE_POLL se aplica a la disponibilidad de la ronda vigente.") boolean canRespond,
		@Schema(description = "Tipo temporal del evento") EventType eventType, PublicDatePollResponse datePoll, MyAvailability myAvailability) {

	public record MyAvailability(long pollRevision, List<String> candidateDateIds, Instant answeredAt, Instant updatedAt) {}

	public record MyResponse(String responseOptionId, String comment, Instant answeredAt, Instant updatedAt) {}

	public record GuestName(String name) {}
	public record Option(String id, String label) {}
	public record VenueDetails(String placeName, String address, Double latitude, Double longitude, String placeId, String directions) {}
	public record OnlineAccessInfo(String platform, LinkVisibility linkVisibility, Integer hoursBefore) {}
	public record OnlineAccessDetails(String url, String roomId, String password, String instructions) {}

	public static InvitationResponse from(InvitationView view) {
		var venue = view.venue();
		var info = view.onlineAccessInfo();
		var access = view.onlineAccess();
		var availability = view.myAvailability();
		return new InvitationResponse(new GuestName(view.guestName()), view.title(), view.description(), view.status(),
				view.modality(), view.startsAt(), view.endsAt(), view.timeZone(), view.startsAtInstant(), view.endsAtInstant(),
				venue == null ? null : new VenueDetails(venue.placeName(), venue.address(), venue.latitude(), venue.longitude(), venue.placeId(), venue.directions()),
				view.responseOptions().stream().map(option -> new Option(option.id(), option.label())).toList(),
				view.allowComment(), view.allowResponseChange(), view.responseDeadline(), view.cancelledAt(), view.cancellationReason(),
				info == null ? null : new OnlineAccessInfo(info.platform(), info.linkVisibility(), info.hoursBefore()),
				access == null ? null : new OnlineAccessDetails(access.url(), access.roomId(), access.password(), access.instructions()),
				view.myResponse() == null ? null : new MyResponse(view.myResponse().responseOptionId(), view.myResponse().comment(), view.myResponse().answeredAt(), view.myResponse().updatedAt()),
				view.canChangeResponse(), view.canRespond(), view.eventType(), PublicDatePollResponse.from(view.datePoll()),
				availability == null ? null : new MyAvailability(availability.pollRevision(), availability.candidateDateIds(),
						availability.answeredAt(), availability.updatedAt()));
	}
}
