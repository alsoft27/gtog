package com.gtog.event.domain.model;

import java.time.Duration;
import java.time.Instant;

public record EmailSendPolicy(int maxSendsPerGuest, Duration minInterval, int maxSendsPerHostPerDay, int maxBatch) {
	public static final Duration HOST_WINDOW = Duration.ofHours(24);
	public EmailSendPolicy {
		if (maxSendsPerGuest < 1 || minInterval == null || minInterval.isNegative() || minInterval.isZero()
				|| maxSendsPerHostPerDay < 1 || maxBatch < 1) { throw new IllegalArgumentException("Invalid invitation send limits"); }
	}
	public EmailSendSkipReason skipReason(Event event, Guest guest, boolean answered, Instant now) {
		if (event.getStatus() != EventStatus.PUBLISHED) { throw new EventInvitationSendingNotAllowedException(); }
		if (guest.getEmail() == null) { return EmailSendSkipReason.NO_EMAIL; }
		if (answered) { return EmailSendSkipReason.ALREADY_ANSWERED; }
		return guestLimitReason(guest, now);
	}
	public EmailSendSkipReason guestLimitReason(Guest guest, Instant now) {
		if (guest.getEmailSendCount() >= maxSendsPerGuest) { return EmailSendSkipReason.LIMIT_REACHED; }
		return guest.getLastEmailSentAt() != null && guest.getLastEmailSentAt().isAfter(now.minus(minInterval))
				? EmailSendSkipReason.THROTTLED : null;
	}
}
