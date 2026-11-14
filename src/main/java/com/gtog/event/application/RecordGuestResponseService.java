package com.gtog.event.application;

import java.time.Clock;
import org.springframework.stereotype.Service;
import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.GuestResponse;
import com.gtog.event.domain.model.InvitationNotFoundException;
import com.gtog.event.domain.model.InvitationView;
import com.gtog.event.domain.port.in.RecordGuestResponseCommand;
import com.gtog.event.domain.port.in.RecordGuestResponseUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;

@Service
public class RecordGuestResponseService implements RecordGuestResponseUseCase {
	private final EventRepositoryPort events;
	private final GuestResponseRepositoryPort responses;
	private final Clock clock;

	public RecordGuestResponseService(EventRepositoryPort events, GuestResponseRepositoryPort responses, Clock clock) {
		this.events = events;
		this.responses = responses;
		this.clock = clock;
	}

	@Override
	public InvitationView recordResponse(RecordGuestResponseCommand command) {
		Event event = events.findByGuestToken(command.token()).orElseThrow(InvitationNotFoundException::new);
		if (event.getStatus() == EventStatus.DRAFT) { throw new InvitationNotFoundException(); }
		Guest guest = event.getGuests().stream().filter(g -> g.getToken().equals(command.token())).findFirst()
				.orElseThrow(InvitationNotFoundException::new);
		var now = clock.instant();
		GuestResponse response = GuestResponse.create(event, command.token(), command.responseOptionId(), command.comment(), command.email(), now);
		// Nunca consultar existencia antes de insertar: el indice unico decide quien gana la carrera.
		GuestResponse stored = event.isAllowResponseChange() ? responses.upsert(response) : responses.insert(response);
		return InvitationViewFactory.create(event, guest, now, stored);
	}
}
