package com.gtog.event.application;

import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.port.in.CreateEventCommand;
import com.gtog.event.domain.port.in.CreateEventUseCase;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.HostEmailVerificationPort;
import com.gtog.event.domain.model.EmailNotVerifiedException;

@Service
public class CreateEventService implements CreateEventUseCase {

	private final EventRepositoryPort eventRepositoryPort;
	private final HostEmailVerificationPort verification;

	public CreateEventService(EventRepositoryPort eventRepositoryPort, HostEmailVerificationPort verification) {
		this.eventRepositoryPort = eventRepositoryPort;
		this.verification = verification;
	}

	@Override
	public Event createEvent(CreateEventCommand command) {
		if (!verification.isVerified(command.hostId())) { throw new EmailNotVerifiedException(); }
		Event event = Event.builder()
				.hostId(command.hostId())
				.title(command.title())
				.description(command.description())
				.startsAt(command.startsAt())
				.endsAt(command.endsAt())
				.timeZone(command.timeZone())
				.modality(command.modality())
				.responseOptions(command.responseOptions())
				.allowComment(command.allowComment())
				.allowResponseChange(command.allowResponseChange())
				.responseDeadline(command.responseDeadline())
				.venue(command.venue())
				.onlineAccess(command.onlineAccess())
				.publicInvitationEnabled(Boolean.TRUE.equals(command.publicInvitationEnabled()))
				.build();
		return eventRepositoryPort.save(event);
	}
}
