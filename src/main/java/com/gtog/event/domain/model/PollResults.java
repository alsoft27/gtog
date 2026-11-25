package com.gtog.event.domain.model;

import java.util.List;
import java.util.Map;

public record PollResults(long pollRevision, List<Vote> votes) {
	public PollResults { votes = List.copyOf(votes); }

	public record Vote(String name, boolean answered, List<String> candidateDateIds) {
		public Vote { candidateDateIds = List.copyOf(candidateDateIds); }
	}

	public static PollResults from(Event event, DatePollSchedule poll, Map<String, GuestAvailability> responses) {
		var votes = event.getGuests().stream().map(guest -> {
			var response = responses.get(guest.getToken());
			return new Vote(guest.getName(), response != null, response == null ? List.of() : response.candidateDateIds());
		}).toList();
		return new PollResults(poll.pollRevision(), votes);
	}
}
