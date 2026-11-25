package com.gtog.event.application;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.gtog.event.domain.model.DatePollSchedule;
import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.InvitationEmail;
import com.gtog.event.domain.model.PollNoticeReason;
import com.gtog.event.domain.port.in.SendPollNoticesUseCase;
import com.gtog.event.domain.port.out.EmailSenderPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;
import com.gtog.event.domain.port.out.PollNoticeRecipientPort;
import com.gtog.event.domain.port.out.PollNoticeRepositoryPort;

@Service
public class SendPollNoticesService implements SendPollNoticesUseCase {
	private static final Logger log = LoggerFactory.getLogger(SendPollNoticesService.class);
	private final PollNoticeRepositoryPort polls;
	private final GuestResponseRepositoryPort responses;
	private final PollNoticeRecipientPort recipients;
	private final EmailSenderPort sender;
	private final Clock clock;

	public SendPollNoticesService(PollNoticeRepositoryPort polls, GuestResponseRepositoryPort responses,
			PollNoticeRecipientPort recipients, EmailSenderPort sender, Clock clock) {
		this.polls = polls;
		this.responses = responses;
		this.recipients = recipients;
		this.sender = sender;
		this.clock = clock;
	}

	@Override
	public void sendDueNotices() {
		try (var events = polls.findUnclaimedPublishedPolls()) {
			events.forEach(this::process);
		}
	}

	private void process(Event event) {
		try {
			var poll = (DatePollSchedule) event.getSchedule();
			var reason = PollNoticeReason.resolve(event, clock.instant(),
					() -> responses.findAvailabilityTokens(event.getId(), poll.pollRevision()));
			if (reason.isEmpty()) { return; }
			var recipient = recipients.findVerifiedEmail(event.getHostId());
			if (recipient.isEmpty() || !polls.claim(event.getId(), event.getVersion(), poll.pollRevision())) { return; }
			var result = sender.send(email(recipient.get(), event.getTitle(), reason.get()));
			if (!result.accepted()) {
				log.warn("POLL_NOTICE_SEND_FAILED eventId={} revision={} code={}", event.getId(), poll.pollRevision(), result.code());
			}
		} catch (RuntimeException failure) {
			// SMTP puede haber aceptado el mensaje antes del fallo. La reclamación no se libera.
			log.warn("POLL_NOTICE_PROCESS_FAILED eventId={} failureType={}", event.getId(), failure.getClass().getSimpleName());
		}
	}

	private InvitationEmail email(String recipient, String title, PollNoticeReason reason) {
		String safeTitle = title.replaceAll("\\p{Cc}", " ").strip();
		String update = switch (reason) {
			case DEADLINE_PASSED -> "Ha vencido el plazo para responder.";
			case ALL_GUESTS_ANSWERED -> "Todos los invitados de la ronda ya han respondido.";
		};
		return new InvitationEmail(recipient, "Novedades de tu consulta de fechas",
				"Consulta: " + safeTitle + "\n\n" + update + "\nPuedes consultar los resultados en el panel del evento.");
	}
}
