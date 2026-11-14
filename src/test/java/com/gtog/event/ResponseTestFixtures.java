package com.gtog.event;

import java.time.LocalDateTime;
import java.util.List;
import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.InvitationStatus;

public final class ResponseTestFixtures {

	private ResponseTestFixtures() {}

	public static Event responseEvent(EventStatus status, boolean allowComment, boolean allowChange, LocalDateTime deadline) {
		Event original = InvitationTestFixtures.event(status, null);
		return Event.reconstituteBuilder().id(original.getId()).hostId(original.getHostId()).title(original.getTitle())
				.description(original.getDescription()).startsAt(original.getStartsAt()).endsAt(original.getEndsAt())
				.timeZone(original.getTimeZone()).modality(original.getModality()).status(status).venue(original.getVenue())
				.responseOptions(original.getResponseOptions()).allowComment(allowComment).allowResponseChange(allowChange)
				.responseDeadline(deadline).guests(List.of(InvitationTestFixtures.guest(101, InvitationStatus.OPENED, InvitationTestFixtures.NOW),
						InvitationTestFixtures.guest(202, InvitationStatus.NOT_SENT, null))).build();
	}
}
