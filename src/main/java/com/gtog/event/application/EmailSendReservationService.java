package com.gtog.event.application;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.out.GuestEmailSendReservationPort;
import com.gtog.event.domain.port.out.HostSendQuotaPort;

@Service
public class EmailSendReservationService {
	private static final Logger LOG = LoggerFactory.getLogger(EmailSendReservationService.class);
	private final GuestEmailSendReservationPort guests;
	private final HostSendQuotaPort hosts;
	private final EmailSendPolicy policy;
	private final Clock clock;
	public EmailSendReservationService(GuestEmailSendReservationPort guests, HostSendQuotaPort hosts, EmailSendPolicy policy, Clock clock) {
		this.guests = guests; this.hosts = hosts; this.policy = policy; this.clock = clock;
	}
	public EmailSendReservationResult reserve(Event event, Guest guest, boolean answered) {
		var now = clock.instant();
		EmailSendSkipReason reason = policy.skipReason(event, guest, answered, now);
		if (reason != null) { return new EmailSendReservationResult(null, reason); }
		var result = guests.reserve(event.getId(), event.getHostId(), guest.getId(), guest.getEmail(), now, policy);
		if (result.reservation() == null) { return new EmailSendReservationResult(null, result.reason()); }
		try {
			var host = hosts.reserve(event.getHostId(), now, policy.maxSendsPerHostPerDay());
			if (host.isPresent()) { return new EmailSendReservationResult(new EmailSendReservation(result.reservation(), host.get()), null); }
		} catch (RuntimeException exception) {
			// Una respuesta incierta de Mongo puede haber reservado cuota: conservar el bloqueo del invitado.
			logFailure("HOST_RESERVATION_UNCERTAIN", result.reservation(), exception);
			throw exception;
		}
		releaseGuest(result.reservation());
		return new EmailSendReservationResult(null, EmailSendSkipReason.DAILY_LIMIT);
	}
	public boolean compensate(EmailSendReservation reservation) {
		try {
			if (!hosts.release(reservation.host())) { throw new EventConcurrentModificationException(); }
		} catch (RuntimeException exception) {
			logFailure("HOST", reservation.guest(), exception);
			return false;
		}
		return releaseGuest(reservation.guest());
	}
	private boolean releaseGuest(GuestEmailReservation reservation) {
		try {
			if (!guests.release(reservation)) { throw new EventConcurrentModificationException(); }
			return true;
		} catch (RuntimeException exception) {
			logFailure("GUEST", reservation, exception);
			return false;
		}
	}
	private void logFailure(String stage, GuestEmailReservation reservation, RuntimeException exception) {
		LOG.error("EMAIL_SEND_COMPENSATION_FAILED stage={} eventId={} guestId={} exceptionType={} correlationId={}", stage,
				reservation.eventId(), reservation.guestId(), exception.getClass().getName(), UUID.randomUUID());
	}
}
