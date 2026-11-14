package com.gtog.event.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.GuestResponseDeletionConfirmationRequiredException;
import com.gtog.event.domain.port.in.AddGuestCommand;
import com.gtog.event.domain.port.in.AddGuestUseCase;
import com.gtog.event.domain.port.in.UpdateGuestCommand;
import com.gtog.event.domain.port.in.UpdateGuestUseCase;
import com.gtog.event.domain.port.in.DeleteGuestCommand;
import com.gtog.event.domain.port.in.DeleteGuestUseCase;
import com.gtog.event.domain.port.in.ListGuestUseCase;
import com.gtog.event.domain.port.out.EventGuestRepositoryPort;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseCleanupPort;
import com.gtog.event.domain.port.out.GuestTokenGeneratorPort;
import com.gtog.event.domain.port.out.PhoneNormalizerPort;

@Service
public class GuestService implements AddGuestUseCase, UpdateGuestUseCase, DeleteGuestUseCase, ListGuestUseCase {

	private final EventRepositoryPort events;
	private final EventGuestRepositoryPort guests;
	private final GuestTokenGeneratorPort tokens;
	private final PhoneNormalizerPort phones;
	private final GuestResponseCleanupPort responses;

	public GuestService(EventRepositoryPort events, EventGuestRepositoryPort guests,
			GuestTokenGeneratorPort tokens, PhoneNormalizerPort phones, GuestResponseCleanupPort responses) {
		this.events = events;
		this.guests = guests;
		this.tokens = tokens;
		this.phones = phones;
		this.responses = responses;
	}

	@Override
	public Guest add(AddGuestCommand command) {
		Event event = ownedEvent(command.hostId(), command.eventId());
		event.ensureGuestsEditable();
		Guest guest = event.addGuest(Guest.create(command.name(), command.email(), command.phone(), tokens.generate(), phones));
		guests.addGuest(event.getId(), command.hostId(), event.getVersion(), guest);
		return guest;
	}

	@Override
	public Guest update(UpdateGuestCommand command) {
		Event event = ownedEvent(command.hostId(), command.eventId());
		event.ensureGuestsEditable();
		Guest guest = event.getGuest(command.guestId()).editContact(command.name(), command.email(), command.phone(), phones);
		event.updateGuest(command.guestId(), guest);
		guests.updateGuest(event.getId(), command.hostId(), event.getVersion(), guest);
		return guest;
	}

	@Override
	public void delete(DeleteGuestCommand command) {
		Event event = ownedEvent(command.hostId(), command.eventId());
		Guest guest = event.removeGuest(command.guestId());
		if (responses.hasResponse(event.getId(), guest.getToken()) && !command.confirmDeleteResponse()) {
			throw new GuestResponseDeletionConfirmationRequiredException();
		}
		guests.removeGuest(event.getId(), command.hostId(), event.getVersion(), guest.getId());
		// Nunca perder una respuesta con el invitado vivo. Limpiar tambien si aparecio tras la consulta inicial.
		responses.deleteResponse(event.getId(), guest.getToken());
	}

	@Override
	public List<Guest> list(String hostId, String eventId) {
		return ownedEvent(hostId, eventId).getGuests();
	}

	private Event ownedEvent(String hostId, String eventId) {
		Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
		if (!event.getHostId().equals(hostId)) {
			throw new EventNotFoundException(eventId);
		}
		return event;
	}
}
