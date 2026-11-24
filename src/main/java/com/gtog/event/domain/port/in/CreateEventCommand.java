package com.gtog.event.domain.port.in;

import java.time.LocalDateTime;
import java.util.List;

import com.gtog.event.domain.model.Modality;
import com.gtog.event.domain.model.EventType;
import com.gtog.event.domain.model.DatePollDraft;
import com.gtog.event.domain.model.OnlineAccess;
import com.gtog.event.domain.model.ResponseOptionDraft;
import com.gtog.event.domain.model.Venue;

public record CreateEventCommand(
		String hostId,
		String title,
		String description,
		LocalDateTime startsAt,
		LocalDateTime endsAt,
		String timeZone,
		Modality modality,
		List<ResponseOptionDraft> responseOptions,
		Boolean allowComment,
		Boolean allowResponseChange,
		LocalDateTime responseDeadline,
		Venue venue,
		OnlineAccess onlineAccess,
		Boolean publicInvitationEnabled, EventType eventType, DatePollDraft datePoll) {
	public CreateEventCommand(String hostId, String title, String description, LocalDateTime startsAt,
			LocalDateTime endsAt, String timeZone, Modality modality, List<ResponseOptionDraft> responseOptions,
			Boolean allowComment, Boolean allowResponseChange, LocalDateTime responseDeadline, Venue venue,
			OnlineAccess onlineAccess, Boolean publicInvitationEnabled) {
		this(hostId, title, description, startsAt, endsAt, timeZone, modality, responseOptions, allowComment,
				allowResponseChange, responseDeadline, venue, onlineAccess, publicInvitationEnabled, null, null);
	}
	public CreateEventCommand(String hostId, String title, String description, LocalDateTime startsAt,
			LocalDateTime endsAt, String timeZone, Modality modality, List<ResponseOptionDraft> responseOptions,
			Boolean allowComment, Boolean allowResponseChange, LocalDateTime responseDeadline, Venue venue, OnlineAccess onlineAccess) {
		this(hostId, title, description, startsAt, endsAt, timeZone, modality, responseOptions, allowComment,
				allowResponseChange, responseDeadline, venue, onlineAccess, false);
	}
}
