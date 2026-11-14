package com.gtog.event.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.InvitationNotFoundException;
import com.gtog.event.domain.model.InvitationStatus;
import com.gtog.event.domain.model.InvitationView;
import com.gtog.event.domain.model.OnlineAccess;
import com.gtog.event.domain.port.in.GetInvitationUseCase;
import com.gtog.event.domain.port.out.EventGuestRepositoryPort;
import com.gtog.event.domain.port.out.EventRepositoryPort;

@Service
public class GetInvitationService implements GetInvitationUseCase {

	private final EventRepositoryPort events;
	private final EventGuestRepositoryPort guests;
	private final Clock clock;

	public GetInvitationService(EventRepositoryPort events, EventGuestRepositoryPort guests, Clock clock) {
		this.events = events;
		this.guests = guests;
		this.clock = clock;
	}

	@Override
	public InvitationView getInvitation(String token) {
		Event event = loadInvitation(token);
		Guest guest = invitedGuest(event, token);
		Instant now = clock.instant();
		if (event.getStatus() == EventStatus.PUBLISHED
				&& (guest.getInvitationStatus() == InvitationStatus.NOT_SENT || guest.getInvitationStatus() == InvitationStatus.SENT)) {
			// Sin coincidencia significa carrera: apertura, cancelacion o borrado tras la primera lectura.
			event = guests.markGuestOpened(event.getId(), token, now).orElseGet(() -> loadInvitation(token));
			ensurePublic(event);
			guest = invitedGuest(event, token);
		}
		return viewOf(event, guest, now);
	}

	private Event loadInvitation(String token) {
		Event event = events.findByGuestToken(token).orElseThrow(InvitationNotFoundException::new);
		ensurePublic(event);
		return event;
	}

	private void ensurePublic(Event event) {
		if (event.getStatus() == EventStatus.DRAFT) {
			throw new InvitationNotFoundException();
		}
	}

	private Guest invitedGuest(Event event, String token) {
		return event.getGuests().stream().filter(guest -> guest.getToken().equals(token)).findFirst()
				.orElseThrow(InvitationNotFoundException::new);
	}

	private InvitationView viewOf(Event event, Guest guest, Instant now) {
		OnlineAccess access = event.getOnlineAccess();
		boolean cancelled = event.getStatus() == EventStatus.CANCELLED;
		return new InvitationView(guest.getName(), event.getTitle(), event.getDescription(), event.getStatus(),
				event.getModality(), event.getStartsAt(), event.getEndsAt(), event.getTimeZone(), event.startsAtInstant(),
				event.endsAtInstant(), event.getVenue(), event.getResponseOptions().stream()
						.map(option -> new InvitationView.Option(option.id(), option.label())).toList(),
				event.isAllowComment(), event.isAllowResponseChange(), event.getResponseDeadline(),
				cancelled ? event.getCancelledAt() : null, cancelled ? event.getCancellationReason() : null,
				access == null ? null : new InvitationView.OnlineAccessInfo(access.platform(), access.linkVisibility(), access.hoursBefore()),
				event.visibleOnlineAccess(guestHasConfirmed(event, guest), now).orElse(null));
	}

	private boolean guestHasConfirmed(Event event, Guest guest) {
		// R8 sustituira este false consultando responses: confirmar exige countsAsAttendance=true;
		// haber respondido (por ejemplo "No asisto") no basta. D-CONFIRMED-LINK.
		return false;
	}
}
