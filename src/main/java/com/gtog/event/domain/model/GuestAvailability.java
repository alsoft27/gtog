package com.gtog.event.domain.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

public record GuestAvailability(long pollRevision, List<String> candidateDateIds, Instant answeredAt, Instant updatedAt) {
	public GuestAvailability {
		candidateDateIds = List.copyOf(candidateDateIds);
	}

	public static GuestAvailability create(Event event, Long revision, List<String> ids, Instant now) {
		if (!(event.getSchedule() instanceof DatePollSchedule poll)) { throw new EventScheduleConflictException(); }
		if (!event.responseAcceptancePolicy(now).acceptingResponses()) { throw new ResponseNotAcceptedException(); }
		if (revision == null || revision < 0) { throw new InvalidAvailabilityException(); }
		if (revision != poll.pollRevision()) { throw new StalePollRevisionException(); }
		if (ids == null || ids.size() > poll.candidateDates().size()) { throw new InvalidAvailabilityException(); }
		var selected = new HashSet<>(ids);
		var ordered = poll.candidateDates().stream().map(CandidateDate::id).filter(selected::contains).toList();
		if (selected.size() != ids.size() || ordered.size() != ids.size()) { throw new InvalidAvailabilityException(); }
		return new GuestAvailability(revision, ordered, now, now);
	}
}
