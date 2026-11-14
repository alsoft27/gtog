package com.gtog.event.infrastructure.in.web;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import com.gtog.event.domain.model.EventStatus;
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
		@Schema(description = "Bloque omitido completamente mientras el acceso no sea visible") OnlineAccessDetails onlineAccess, MyResponse myResponse, boolean canChangeResponse) {

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
		return new InvitationResponse(new GuestName(view.guestName()), view.title(), view.description(), view.status(),
				view.modality(), view.startsAt(), view.endsAt(), view.timeZone(), view.startsAtInstant(), view.endsAtInstant(),
				venue == null ? null : new VenueDetails(venue.placeName(), venue.address(), venue.latitude(), venue.longitude(), venue.placeId(), venue.directions()),
				view.responseOptions().stream().map(option -> new Option(option.id(), option.label())).toList(),
				view.allowComment(), view.allowResponseChange(), view.responseDeadline(), view.cancelledAt(), view.cancellationReason(),
				info == null ? null : new OnlineAccessInfo(info.platform(), info.linkVisibility(), info.hoursBefore()),
				access == null ? null : new OnlineAccessDetails(access.url(), access.roomId(), access.password(), access.instructions()),
				view.myResponse() == null ? null : new MyResponse(view.myResponse().responseOptionId(), view.myResponse().comment(), view.myResponse().answeredAt(), view.myResponse().updatedAt()),
				view.canChangeResponse());
	}
}
