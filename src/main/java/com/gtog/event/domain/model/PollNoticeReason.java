package com.gtog.event.domain.model;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public enum PollNoticeReason {
	DEADLINE_PASSED, ALL_GUESTS_ANSWERED;

	public static Optional<PollNoticeReason> resolve(Event event, Instant now, Supplier<Set<String>> answeredTokens) {
		if (event.getStatus() != EventStatus.PUBLISHED || !(event.getSchedule() instanceof DatePollSchedule poll)
				|| poll.noticeClaimed()) {
			return Optional.empty();
		}
		if (event.pollResultsVisible(now)) { return Optional.of(DEADLINE_PASSED); }
		var guests = event.getGuests();
		if (event.isPublicInvitationEnabled() || guests.isEmpty()) { return Optional.empty(); }
		var answered = answeredTokens.get();
		return guests.stream().allMatch(guest -> answered.contains(guest.getToken()))
				? Optional.of(ALL_GUESTS_ANSWERED) : Optional.empty();
	}
}
