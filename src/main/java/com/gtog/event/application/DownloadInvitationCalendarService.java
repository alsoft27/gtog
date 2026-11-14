package com.gtog.event.application;

import java.time.Clock;
import org.springframework.stereotype.Service;
import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.InvitationCalendarData;
import com.gtog.event.domain.model.InvitationCalendarNotAvailableException;
import com.gtog.event.domain.model.InvitationNotFoundException;
import com.gtog.event.domain.port.in.DownloadInvitationCalendarUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;
import com.gtog.event.domain.port.out.InvitationCalendarRendererPort;

@Service
public class DownloadInvitationCalendarService implements DownloadInvitationCalendarUseCase {
	private final EventRepositoryPort events;
	private final GuestResponseRepositoryPort responses;
	private final InvitationCalendarRendererPort renderer;
	private final Clock clock;

	public DownloadInvitationCalendarService(EventRepositoryPort events, GuestResponseRepositoryPort responses,
			InvitationCalendarRendererPort renderer, Clock clock) {
		this.events = events;
		this.responses = responses;
		this.renderer = renderer;
		this.clock = clock;
	}

	@Override
	public byte[] downloadCalendar(String token) {
		Event event = events.findByGuestToken(token).orElseThrow(InvitationNotFoundException::new);
		if (event.getStatus() == EventStatus.DRAFT) { throw new InvitationNotFoundException(); }
		Guest guest = event.getGuests().stream().filter(candidate -> candidate.getToken().equals(token))
				.findFirst().orElseThrow(InvitationNotFoundException::new);
		if (event.getStatus() == EventStatus.CANCELLED) { throw new InvitationCalendarNotAvailableException(); }
		var now = clock.instant();
		var view = InvitationViewFactory.create(event, guest, now, responses.find(event.getId(), token).orElse(null));
		// Comparte exactamente el filtrado del JSON; el renderizador nunca recibe el acceso oculto completo.
		return renderer.render(new InvitationCalendarData(event.getId(), view.title(), view.description(), view.startsAt(),
				view.endsAt(), view.timeZone(), view.venue(), view.onlineAccess() == null ? null : view.onlineAccess().url(), now));
	}
}
