package com.gtog.event.infrastructure.in.web;

import java.time.Instant;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.gtog.event.domain.model.*;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Vista previa del enlace de alta: sin identidad, contactos ni secretos de la sala")
public record PublicJoinPreviewResponse(String title, String description, EventStatus status, Modality modality,
		LocalDateTime startsAt, LocalDateTime endsAt, String timeZone, Instant startsAtInstant, Instant endsAtInstant,
		VenueResponse venue, OnlinePlatform onlineAccessInfo, boolean canJoin, boolean alreadyJoined,
		@Schema(description = "Solo con la cookie de un invitado PUBLIC_LINK vivo de este evento") String invitationPath,
		@Schema(description = "Tipo temporal del evento") EventType eventType) {
	public record OnlinePlatform(String platform) {}
	public static PublicJoinPreviewResponse from(PublicInvitationPreview preview) {
		Event event = preview.event();
		return new PublicJoinPreviewResponse(event.getTitle(), event.getDescription(), event.getStatus(), event.getModality(),
				event.getStartsAt(), event.getEndsAt(), event.getTimeZone(), event.startsAtInstant(), event.endsAtInstant(),
				event.getVenue() == null ? null : VenueResponse.from(event.getVenue()),
				event.getOnlineAccess() == null ? null : new OnlinePlatform(event.getOnlineAccess().platform()),
				preview.canJoin(), preview.alreadyJoined(), preview.invitationPath(), event.getEventType());
	}
}
