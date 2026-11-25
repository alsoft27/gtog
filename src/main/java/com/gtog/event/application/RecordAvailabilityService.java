package com.gtog.event.application;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.in.RecordAvailabilityUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;

@Service
public class RecordAvailabilityService implements RecordAvailabilityUseCase {
	private final EventRepositoryPort events;
	private final GuestResponseRepositoryPort responses;
	private final Clock clock;

	public RecordAvailabilityService(EventRepositoryPort events, GuestResponseRepositoryPort responses, Clock clock) {
		this.events = events;
		this.responses = responses;
		this.clock = clock;
	}

	@Override
	public InvitationView recordAvailability(String token, Long pollRevision, List<String> candidateDateIds) {
		var event = loadInvitation(token);
		var now = clock.instant();
		var availability = GuestAvailability.create(event, pollRevision, candidateDateIds, now);
		var stored = responses.saveAvailability(event.getId(), token, availability, event.isAllowResponseChange());

		// La ronda pertenece a events y la respuesta a responses. Si cambió en paralelo,
		// la respuesta de la ronda anterior queda invisible y nunca pisa una ronda más nueva.
		var current = loadInvitation(token);
		if (!(current.getSchedule() instanceof DatePollSchedule poll) || poll.pollRevision() != stored.pollRevision()) {
			throw new StalePollRevisionException();
		}
		var guest = current.getGuests().stream().filter(candidate -> candidate.getToken().equals(token)).findFirst()
				.orElseThrow(InvitationNotFoundException::new);
		return InvitationViewFactory.create(current, guest, now, null, stored);
	}

	private Event loadInvitation(String token) {
		var event = events.findByGuestToken(token).orElseThrow(InvitationNotFoundException::new);
		if (event.getStatus() == EventStatus.DRAFT) { throw new InvitationNotFoundException(); }
		return event;
	}
}
