package com.gtog.event.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventType;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.InvitationNotFoundException;
import com.gtog.event.domain.model.InvitationStatus;
import com.gtog.event.domain.model.InvitationView;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;
import com.gtog.event.domain.port.in.GetInvitationUseCase;
import com.gtog.event.domain.port.out.EventGuestRepositoryPort;
import com.gtog.event.domain.port.out.EventRepositoryPort;

@Service
public class GetInvitationService implements GetInvitationUseCase {

	private final EventRepositoryPort events;
	private final EventGuestRepositoryPort guests;
	private final Clock clock;
	private final GuestResponseRepositoryPort responses;

	public GetInvitationService(EventRepositoryPort events, EventGuestRepositoryPort guests, Clock clock, GuestResponseRepositoryPort responses) {
		this.events = events;
		this.guests = guests;
		this.clock = clock;
		this.responses = responses;
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
		return InvitationViewFactory.create(event, guest, now, event.getEventType() == EventType.FIXED_DATE ? responses.find(event.getId(), token).orElse(null) : null);
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

}
