package com.gtog.event.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.in.SendInvitationsCommand;
import com.gtog.event.domain.port.in.SendInvitationsUseCase;
import com.gtog.event.domain.port.out.*;

@Service
public class SendInvitationsService implements SendInvitationsUseCase {
	private static final Logger log = LoggerFactory.getLogger(SendInvitationsService.class);
	private final EventRepositoryPort events;
	private final GuestResponseRepositoryPort responses;
	private final GuestEmailSendReservationPort guestReservations;
	private final HostSendQuotaPort quotas;
	private final EmailSendReservationService reservations;
	private final EmailSenderPort sender;
	private final InvitationEmailComposer composer;
	private final EmailSendPolicy policy;
	private final Clock clock;

	public SendInvitationsService(EventRepositoryPort events, GuestResponseRepositoryPort responses,
			GuestEmailSendReservationPort guestReservations, HostSendQuotaPort quotas, EmailSendReservationService reservations,
			EmailSenderPort sender, InvitationEmailComposer composer, EmailSendPolicy policy, Clock clock) {
		this.events = events; this.responses = responses; this.guestReservations = guestReservations; this.quotas = quotas;
		this.reservations = reservations; this.sender = sender; this.composer = composer; this.policy = policy; this.clock = clock;
	}

	@Override
	public InvitationSendBatchResult send(SendInvitationsCommand command) {
		Event event = ownedEvent(command.hostId(), command.eventId());
		policy.ensureSendingAllowed(event);
		var selected = InvitationBatchSelection.select(event, command.guestIds(), policy.maxBatch());
		Set<String> answered = InvitationAnswers.tokens(responses, event);
		var results = new ArrayList<GuestEmailSendResult>();
		EmailSendSkipReason stopped = null;
		int providerFailures = 0;
		for (Guest guest : selected) {
			if (stopped != null) { results.add(GuestEmailSendResult.skipped(guest.getId(), stopped)); continue; }
			var permit = reservations.reserve(event, guest, answered.contains(guest.getToken()));
			if (permit.reservation() == null) {
				results.add(GuestEmailSendResult.skipped(guest.getId(), permit.reason()));
				if (permit.reason() == EmailSendSkipReason.DAILY_LIMIT) { stopped = permit.reason(); }
				continue;
			}
			var reservation = permit.reservation();
			InvitationEmail email;
			try {
				// Revalidar antes de SMTP: nunca enviar si ya respondió, fue retirado o cambió el contacto.
				Event current = ownedEvent(command.hostId(), event.getId());
				policy.ensureSendingAllowed(current);
				Guest currentGuest = current.getGuest(guest.getId());
				if (!java.util.Objects.equals(currentGuest.getEmail(), guest.getEmail())) { throw new EventConcurrentModificationException(); }
				if (InvitationAnswers.hasAnswer(responses, current, guest)) {
					reservations.compensate(reservation);
					results.add(GuestEmailSendResult.skipped(guest.getId(), EmailSendSkipReason.ALREADY_ANSWERED));
					continue;
				}
				email = composer.compose(current, currentGuest, command.hostName());
			}
			catch (RuntimeException exception) { reservations.compensate(reservation); throw exception; }
			EmailDeliveryResult delivery = deliver(email);
			if (delivery.accepted()) {
				results.add(GuestEmailSendResult.sent(guest.getId(), recordSent(reservation)));
				providerFailures = 0;
			}
			else {
				reservations.compensate(reservation);
				results.add(GuestEmailSendResult.failed(guest.getId(), delivery.code()));
				providerFailures = delivery.code().isProviderFailure() ? providerFailures + 1 : 0;
				if (providerFailures == 3) { stopped = EmailSendSkipReason.PROVIDER_UNAVAILABLE; }
			}
		}
		return new InvitationSendBatchResult(results, quotas.read(command.hostId(), clock.instant(), policy.maxSendsPerHostPerDay()));
	}

	private Event ownedEvent(String hostId, String eventId) {
		Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
		if (!event.getHostId().equals(hostId)) { throw new EventNotFoundException(eventId); }
		return event;
	}

	private EmailDeliveryResult deliver(InvitationEmail email) {
		try { return sender.send(email); }
		catch (RuntimeException exception) {
			log.error("EMAIL_SEND_FAILED exceptionType={} correlationId={}", exception.getClass().getName(), UUID.randomUUID());
			return EmailDeliveryResult.failure(EmailSendFailureCode.PROVIDER_UNAVAILABLE);
		}
	}

	private boolean recordSent(EmailSendReservation reservation) {
		try {
			if (guestReservations.recordSent(reservation.guest(), clock.instant())) { return true; }
			throw new EventConcurrentModificationException();
		}
		catch (RuntimeException exception) {
			// SMTP ya aceptó: conservar las cuotas, informar y nunca reenviar aquí.
			log.error("EMAIL_SENT_RECORDING_FAILED exceptionType={} correlationId={}", exception.getClass().getName(), UUID.randomUUID());
			return false;
		}
	}
}
