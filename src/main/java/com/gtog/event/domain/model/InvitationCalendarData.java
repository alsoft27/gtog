package com.gtog.event.domain.model;

import java.time.Instant;
import java.time.LocalDateTime;

// Proyeccion exclusiva del calendario: el enlace ya esta filtrado; sin invitados, tokens ni contactos.
public record InvitationCalendarData(String eventId, String title, String description,
		LocalDateTime startsAt, LocalDateTime endsAt, String timeZone, Venue venue,
		String onlineUrl, Instant generatedAt) {}
