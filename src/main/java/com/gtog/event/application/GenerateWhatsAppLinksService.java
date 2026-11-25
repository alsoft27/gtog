package com.gtog.event.application;

import java.util.List;
import org.springframework.stereotype.Service;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.in.GenerateWhatsAppLinksCommand;
import com.gtog.event.domain.port.in.GenerateWhatsAppLinksUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;

@Service
public class GenerateWhatsAppLinksService implements GenerateWhatsAppLinksUseCase {
	private final EventRepositoryPort events;
	private final GuestResponseRepositoryPort responses;
	private final WhatsAppInvitationComposer composer;
	private final WhatsAppLinkPolicy policy;
	public GenerateWhatsAppLinksService(EventRepositoryPort events, GuestResponseRepositoryPort responses,
			WhatsAppInvitationComposer composer, WhatsAppLinkPolicy policy) {
		this.events = events; this.responses = responses; this.composer = composer; this.policy = policy;
	}
	@Override public List<WhatsAppLinkResult> generate(GenerateWhatsAppLinksCommand command) {
		Event event = events.findById(command.eventId()).orElseThrow(() -> new EventNotFoundException(command.eventId()));
		if (!event.getHostId().equals(command.hostId())) { throw new EventNotFoundException(command.eventId()); }
		policy.ensurePublished(event);
		var guests = InvitationBatchSelection.select(event, command.guestIds(), policy.maxLinksPerRequest());
		var answered = InvitationAnswers.tokens(responses, event);
		return guests.stream().map(guest -> {
			var reason = policy.skipReason(guest, answered.contains(guest.getToken()));
			if (reason != null) { return WhatsAppLinkResult.skipped(guest.getId(), reason); }
			var link = composer.compose(event, guest, command.hostName());
			return policy.acceptsUrl(link.url()) ? WhatsAppLinkResult.ready(guest.getId(), link)
					: WhatsAppLinkResult.skipped(guest.getId(), WhatsAppLinkSkipReason.MESSAGE_TOO_LONG);
		}).toList();
	}
}
