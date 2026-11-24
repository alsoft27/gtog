package com.gtog.event.domain.model;

import java.time.LocalDateTime;

public record EventEdit(
		String title,
		String description,
		LocalDateTime startsAt,
		LocalDateTime endsAt,
		String timeZone,
		Modality modality,
		Venue venue,
		OnlineAccess onlineAccess,
		boolean allowComment,
		boolean allowResponseChange,
		LocalDateTime responseDeadline, EventType eventType) {
	public EventEdit(String title, String description, LocalDateTime startsAt, LocalDateTime endsAt, String timeZone,
			Modality modality, Venue venue, OnlineAccess onlineAccess, boolean allowComment, boolean allowResponseChange,
			LocalDateTime responseDeadline) {
		this(title, description, startsAt, endsAt, timeZone, modality, venue, onlineAccess, allowComment, allowResponseChange, responseDeadline, null);
	}
}
