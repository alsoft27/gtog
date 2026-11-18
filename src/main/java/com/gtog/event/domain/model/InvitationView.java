package com.gtog.event.domain.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

// Vista de lectura sin identidad del anfitrion, contactos, tokens ni otros invitados.
public record InvitationView(String guestName, String title, String description, EventStatus status,
		Modality modality, LocalDateTime startsAt, LocalDateTime endsAt, String timeZone,
		Instant startsAtInstant, Instant endsAtInstant, Venue venue, List<Option> responseOptions,
		boolean allowComment, boolean allowResponseChange, LocalDateTime responseDeadline,
		Instant cancelledAt, String cancellationReason, OnlineAccessInfo onlineAccessInfo, OnlineAccess onlineAccess, MyResponse myResponse, boolean canChangeResponse, boolean canRespond) {

	public record MyResponse(String responseOptionId, String comment, Instant answeredAt, Instant updatedAt) {}

	public record Option(String id, String label) {}

	public record OnlineAccessInfo(String platform, LinkVisibility linkVisibility, Integer hoursBefore) {}
}
