package com.gtog.event.application;

import java.util.Set;
import java.util.stream.Collectors;

import com.gtog.event.domain.model.DatePollSchedule;
import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.GuestResponse;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;

final class InvitationAnswers {
	private InvitationAnswers() {}

	static Set<String> tokens(GuestResponseRepositoryPort responses, Event event) {
		if (event.getSchedule() instanceof DatePollSchedule poll) {
			return responses.findAvailabilityTokens(event.getId(), poll.pollRevision());
		}
		return responses.findByEventIds(Set.of(event.getId())).stream()
				.map(GuestResponse::guestToken).collect(Collectors.toSet());
	}

	static boolean hasAnswer(GuestResponseRepositoryPort responses, Event event, Guest guest) {
		if (event.getSchedule() instanceof DatePollSchedule poll) {
			return responses.findAvailability(event.getId(), guest.getToken(), poll.pollRevision()).isPresent();
		}
		return responses.find(event.getId(), guest.getToken()).isPresent();
	}
}
