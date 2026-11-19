package com.gtog.event.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.in.ConfirmWhatsAppSentCommand;
import com.gtog.event.domain.port.in.ConfirmWhatsAppSentUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestWhatsAppConfirmationPort;

@Service
public class ConfirmWhatsAppSentService implements ConfirmWhatsAppSentUseCase {
	private final EventRepositoryPort events;
	private final GuestWhatsAppConfirmationPort confirmations;
	private final WhatsAppLinkPolicy policy;
	private final Clock clock;

	public ConfirmWhatsAppSentService(EventRepositoryPort events, GuestWhatsAppConfirmationPort confirmations,
			WhatsAppLinkPolicy policy, Clock clock) {
		this.events = events; this.confirmations = confirmations; this.policy = policy; this.clock = clock;
	}

	@Override public List<WhatsAppConfirmationResult> confirm(ConfirmWhatsAppSentCommand command) {
		Event event = ownedPublishedEvent(command);
		if (command.guestIds() == null) { throw new InvalidInvitationBatchException(); }
		var guests = InvitationBatchSelection.select(event, command.guestIds(), policy.maxLinksPerRequest());
		var results = new ArrayList<WhatsAppConfirmationResult>();
		for (Guest guest : guests) {
			if (confirmations.confirm(event.getId(), command.hostId(), guest.getId(), clock.instant())) {
				results.add(new WhatsAppConfirmationResult(guest.getId(), WhatsAppConfirmationStatus.MARKED));
			} else {
				// La reconsulta distingue idempotencia, retirada del invitado y cambio de estado durante el lote.
				Event current = ownedPublishedEvent(command);
				results.add(current.getGuests().stream().filter(g -> g.getId().equals(guest.getId())).findFirst()
						.map(WhatsAppConfirmationResult::unchanged)
						.orElseGet(() -> new WhatsAppConfirmationResult(guest.getId(), WhatsAppConfirmationStatus.NOT_FOUND)));
			}
		}
		return List.copyOf(results);
	}

	private Event ownedPublishedEvent(ConfirmWhatsAppSentCommand command) {
		Event event = events.findById(command.eventId()).orElseThrow(() -> new EventNotFoundException(command.eventId()));
		if (!event.getHostId().equals(command.hostId())) { throw new EventNotFoundException(command.eventId()); }
		policy.ensurePublished(event);
		return event;
	}
}
