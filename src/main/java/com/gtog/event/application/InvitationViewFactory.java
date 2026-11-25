package com.gtog.event.application;

import java.time.Instant;
import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventType;
import com.gtog.event.domain.model.DatePollView;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.GuestAvailability;
import com.gtog.event.domain.model.GuestResponse;
import com.gtog.event.domain.model.InvitationView;
import com.gtog.event.domain.model.OnlineAccess;
import com.gtog.event.domain.model.PollResults;

public final class InvitationViewFactory {
	private InvitationViewFactory() {}

	public static InvitationView create(Event event, Guest guest, Instant now, GuestResponse response) {
		return create(event, guest, now, response, null);
	}

	public static InvitationView create(Event event, Guest guest, Instant now, GuestResponse response, GuestAvailability availability) {
		return create(event, guest, now, response, availability, null);
	}

	public static InvitationView create(Event event, Guest guest, Instant now, GuestResponse response,
			GuestAvailability availability, PollResults pollResults) {
		OnlineAccess access = event.getOnlineAccess();
		boolean cancelled = event.getStatus() == EventStatus.CANCELLED;
		if (event.getEventType() == EventType.DATE_POLL) { response = null; }
		boolean hasResponse = response != null || availability != null;
		boolean canRespond = event.responseAcceptancePolicy(now).canRespond(hasResponse);
		return new InvitationView(guest.getName(), event.getTitle(), event.getDescription(), event.getStatus(),
				event.getModality(), event.getStartsAt(), event.getEndsAt(), event.getTimeZone(), event.startsAtInstant(),
				event.endsAtInstant(), event.getVenue(), event.getResponseOptions().stream()
						.map(option -> new InvitationView.Option(option.id(), option.label())).toList(),
				event.isAllowComment(), event.isAllowResponseChange(), event.getResponseDeadline(),
				cancelled ? event.getCancelledAt() : null, cancelled ? event.getCancellationReason() : null,
				access == null ? null : new InvitationView.OnlineAccessInfo(access.platform(), access.linkVisibility(), access.hoursBefore()),
				event.visibleOnlineAccess(guestHasConfirmed(event, response), now).orElse(null),
				response == null ? null : new InvitationView.MyResponse(response.responseOptionId(), response.comment(), response.answeredAt(), response.updatedAt()),
				hasResponse && canRespond, canRespond, event.getEventType(), DatePollView.from(event.getSchedule()),
				availability == null ? null : new InvitationView.MyAvailability(availability.pollRevision(),
						availability.candidateDateIds(), availability.answeredAt(), availability.updatedAt()), pollResults);
	}

	private static boolean guestHasConfirmed(Event event, GuestResponse response) {
		if (response == null) { return false; }
		// Una opcion inexistente no confirma asistencia: lectura defensiva ante una respuesta huerfana.
		return event.getResponseOptions().stream().anyMatch(option -> option.id().equals(response.responseOptionId())
				&& option.countsAsAttendance());
	}
}
