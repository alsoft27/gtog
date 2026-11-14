package com.gtog.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.InvitationStatus;
import com.gtog.event.domain.model.LinkVisibility;
import com.gtog.event.domain.model.Modality;
import com.gtog.event.domain.model.OnlineAccess;
import com.gtog.event.domain.model.ResponseOption;
import com.gtog.event.domain.model.Venue;

public final class InvitationTestFixtures {

	public static final Instant NOW = Instant.parse("2027-01-01T15:00:00Z");
	public static final String URL = "https://secret.example.com/private-meeting";
	public static final String PASSWORD = "secret-meeting-password";
	public static final String ROOM = "private-room-id";
	public static final String INSTRUCTIONS = "private-connection-instructions";

	private InvitationTestFixtures() {}

	public static Guest guest(int id, InvitationStatus status, Instant openedAt) {
		return Guest.reconstitute("guest-" + id, "Private guest " + id, "private" + id + "@example.com",
				id == 101 ? "+34612345678" : "+16502530000", GuestTestFixtures.token(id), status, null, null, openedAt);
	}

	public static Event event(EventStatus status, LinkVisibility visibility, Guest... guests) {
		OnlineAccess access = visibility == null ? null : new OnlineAccess("Zoom", URL, ROOM, PASSWORD, INSTRUCTIONS,
				visibility, visibility == LinkVisibility.HOURS_BEFORE ? 2 : null);
		return Event.reconstituteBuilder().id("event-id").hostId("host-1").title("Public event")
				.description("Public description").startsAt(LocalDateTime.of(2027, 1, 1, 18, 0))
				.endsAt(LocalDateTime.of(2027, 1, 1, 20, 0)).timeZone("Europe/Madrid")
				.modality(visibility == null ? Modality.IN_PERSON : Modality.ONLINE).status(status)
				.venue(visibility == null ? new Venue("Sala", "Direccion", 40.0, -3.0, "place-id", "Entrada") : null)
				.onlineAccess(access).responseOptions(List.of(new ResponseOption("yes-id", "Asisto", true),
						new ResponseOption("no-id", "No asisto", false)))
				.allowComment(true).allowResponseChange(false).responseDeadline(LocalDateTime.of(2027, 1, 1, 17, 0))
				.cancelledAt(status == EventStatus.CANCELLED ? NOW.minusSeconds(60) : null)
				.cancellationReason(status == EventStatus.CANCELLED ? "Cancelled for testing" : null)
				.guests(List.of(guests)).build();
	}
}
